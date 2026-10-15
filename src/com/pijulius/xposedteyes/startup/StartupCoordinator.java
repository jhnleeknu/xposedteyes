package com.pijulius.xposedteyes.startup;

import com.pijulius.xposedteyes.settings.StartupSettings;
import com.pijulius.xposedteyes.settings.StartupSettings.ConflictPolicy;
import com.pijulius.xposedteyes.settings.StartupSettings.ForegroundPolicy;
import com.pijulius.xposedteyes.settings.Target;
import com.pijulius.xposedteyes.startup.StatusReport.Cause;
import com.pijulius.xposedteyes.startup.StatusReport.Kind;

/**
 * Decides when the startup app and the Bluetooth app are launched.
 *
 * All entry points must be called from a single thread (the hook posts them
 * to one HandlerThread). No Android dependencies, so the policy is covered by
 * JVM unit tests.
 *
 * Event model:
 * - Cold boot: ACTION_BOOT_COMPLETED, at most once per boot (persisted).
 * - ACC: every ACC OFF starts a new "ACC epoch"; the first ACC ON of an epoch
 *   is the event, repeats are duplicates. Epoch 0 (no ACC OFF yet in this
 *   boot) is the initial power-on and, in BOTH mode, the same session as the
 *   cold boot event.
 * - Bluetooth: only connected/disconnected transitions count.
 */
public final class StartupCoordinator {
	static final long VERIFY_DELAY_MS = 2000;
	static final int MAX_ERRORS = 3;

	static final String K_BOOT = "bootId";
	static final String K_HOOK_SEEN = "hookSeenBeforeBootCompleted";
	static final String K_BOOT_PROCESSED = "bootProcessed";
	static final String K_INITIAL = "initialSessionHandled";
	static final String K_ACC_EPOCH = "accEpoch";
	static final String K_ACC_HANDLED = "accHandledEpoch";

	private final StartupEnv env;
	private final Scheduler scheduler;
	private StateStore store;

	private Session startup;
	private Session bluetooth;
	private boolean btConnected = false;
	private int errors = 0;
	private boolean disabled = false;

	public StartupCoordinator(StartupEnv env, Scheduler scheduler, StateStore store) {
		this.env = env;
		this.scheduler = scheduler;
		this.store = store;
	}

	private static final class Session {
		final Kind kind;
		final Cause cause;
		final String fingerprint;
		int attempt = 0;
		boolean launched = false;
		boolean ended = false;
		Scheduler.Task task;

		Session(Kind kind, Cause cause, String fingerprint) {
			this.kind = kind;
			this.cause = cause;
			this.fingerprint = fingerprint;
		}

		boolean pending() {
			return !ended && !launched;
		}
	}

	// ---------------------------------------------------------------- events

	/**
	 * Called once when the hook registers its receivers.
	 *
	 * @param bootId identifier of the current boot, or null if unknown (then
	 *               only in-memory duplicate protection and no catch-up)
	 * @param bootCompleted whether the system already reports boot completed
	 */
	public void onHookInit(String bootId, boolean bootCompleted) {
		if (disabled)
			return;
		try {
			if (bootId == null)
				store = new StateStore.Memory();

			if (bootId == null || !bootId.equals(store.get(K_BOOT))) {
				store.clear();
				if (bootId != null)
					store.put(K_BOOT, bootId);
			}

			boolean hookSeen = "1".equals(store.get(K_HOOK_SEEN));

			if (!bootCompleted) {
				store.put(K_HOOK_SEEN, "1");
				return;
			}

			if (isBootProcessed())
				return;

			if (hookSeen) {
				// Registered in time earlier in this boot, then the process
				// restarted before the boot completed broadcast was handled.
				handleBootCompleted(Cause.BOOT_CATCHUP);
				return;
			}

			// First registration after the boot already completed (module just
			// enabled, or no boot id): never launch for this boot.
			store.put(K_HOOK_SEEN, "1");
			store.put(K_BOOT_PROCESSED, "1");
			store.put(K_INITIAL, "1");
			report(Kind.STARTUP, Cause.BOOT, StartupStatus.SKIPPED_LATE_REGISTRATION, null, 0);
		} catch (RuntimeException e) {
			onError(e);
		}
	}

	public void onBootCompleted() {
		if (disabled)
			return;
		try {
			if (isBootProcessed())
				return;
			handleBootCompleted(Cause.BOOT);
		} catch (RuntimeException e) {
			onError(e);
		}
	}

	public void onAccOn() {
		if (disabled)
			return;
		try {
			int epoch = accEpoch();
			if (String.valueOf(epoch).equals(store.get(K_ACC_HANDLED)))
				return;
			store.put(K_ACC_HANDLED, String.valueOf(epoch));

			StartupSettings s = env.loadSettings();
			if (!s.triggersOnAcc())
				return;

			if (epoch == 0) {
				if ("1".equals(store.get(K_INITIAL)))
					return;
				store.put(K_INITIAL, "1");
				startStartup(Cause.ACC_INITIAL, s);
			} else {
				startStartup(Cause.ACC_RESUME, s);
			}
		} catch (RuntimeException e) {
			onError(e);
		}
	}

	public void onAccOff() {
		if (disabled)
			return;
		try {
			store.put(K_ACC_EPOCH, String.valueOf(accEpoch() + 1));
			cancel(startup, "acc_off");
			cancel(bluetooth, "acc_off");
			startup = null;
			bluetooth = null;
			btConnected = false;
		} catch (RuntimeException e) {
			onError(e);
		}
	}

	public void onBluetoothState(boolean connected) {
		if (disabled)
			return;
		try {
			if (connected == btConnected)
				return;
			btConnected = connected;

			if (connected) {
				startBluetooth(env.loadSettings());
			} else {
				cancel(bluetooth, "bt_disconnected");
				bluetooth = null;
			}
		} catch (RuntimeException e) {
			onError(e);
		}
	}

	/** Settings were saved; pending launches whose settings changed are cancelled. */
	public void onSettingsChanged() {
		if (disabled)
			return;
		try {
			StartupSettings s = env.loadSettings();

			if (startup != null && !startup.ended &&
				(!s.enabled || !s.startupFingerprint().equals(startup.fingerprint)))
			{
				cancel(startup, "settings_changed");
				startup = null;
			}

			if (bluetooth != null && !bluetooth.ended &&
				(!s.btEnabled || !s.bluetoothFingerprint().equals(bluetooth.fingerprint)))
			{
				cancel(bluetooth, "settings_changed");
				bluetooth = null;
			}
		} catch (RuntimeException e) {
			onError(e);
		}
	}

	// --------------------------------------------------------------- helpers

	private void handleBootCompleted(Cause cause) {
		store.put(K_BOOT_PROCESSED, "1");

		StartupSettings s = env.loadSettings();
		if (!s.triggersOnColdBoot())
			return;

		if (s.trigger == StartupSettings.Trigger.BOTH && "1".equals(store.get(K_INITIAL)))
			return;

		store.put(K_INITIAL, "1");
		startStartup(cause, s);
	}

	private boolean isBootProcessed() {
		return "1".equals(store.get(K_BOOT_PROCESSED));
	}

	private int accEpoch() {
		String v = store.get(K_ACC_EPOCH);
		if (v == null)
			return 0;
		try {
			return Integer.parseInt(v);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private void startStartup(Cause cause, StartupSettings s) {
		if (!s.enabled) {
			report(Kind.STARTUP, cause, StartupStatus.SKIPPED_DISABLED, null, 0);
			return;
		}

		if (bluetooth != null && bluetooth.pending()) {
			if (s.conflictPolicy == ConflictPolicy.BLUETOOTH_FIRST) {
				report(Kind.STARTUP, cause, StartupStatus.SKIPPED_CONFLICT, "bluetooth_first", 0);
				return;
			}
			cancel(bluetooth, "conflict");
			bluetooth = null;
		}

		cancel(startup, "superseded");
		startup = schedule(new Session(Kind.STARTUP, cause, s.startupFingerprint()), s.delayMs);
	}

	private void startBluetooth(StartupSettings s) {
		if (!s.btEnabled)
			return;

		if (startup != null && startup.pending()) {
			if (s.conflictPolicy == ConflictPolicy.STARTUP_FIRST) {
				report(Kind.BLUETOOTH, Cause.BT_CONNECT, StartupStatus.SKIPPED_CONFLICT, "startup_first", 0);
				return;
			}
			cancel(startup, "conflict");
			startup = null;
		}

		cancel(bluetooth, "superseded");
		bluetooth = schedule(new Session(Kind.BLUETOOTH, Cause.BT_CONNECT, s.bluetoothFingerprint()), s.btDelayMs);
	}

	private Session schedule(Session session, long delayMs) {
		session.task = scheduler.schedule(fireRunnable(session), delayMs);
		report(session, StartupStatus.SCHEDULED, delayMs / 1000 + "s");
		return session;
	}

	private Runnable fireRunnable(final Session session) {
		return new Runnable() {
			@Override
			public void run() {
				if (disabled)
					return;
				try {
					fire(session);
				} catch (RuntimeException e) {
					onError(e);
				}
			}
		};
	}

	private boolean isCurrent(Session session) {
		return !session.ended && (session == startup || session == bluetooth);
	}

	private void fire(final Session session) {
		if (!isCurrent(session))
			return;
		session.task = null;

		// Re-read: a missed "settings changed" broadcast must not launch with old settings.
		StartupSettings s = env.loadSettings();
		boolean isStartup = session.kind == Kind.STARTUP;

		if (isStartup ? !s.enabled : !s.btEnabled) {
			end(session, StartupStatus.SKIPPED_DISABLED, null);
			return;
		}

		String fingerprint = isStartup ? s.startupFingerprint() : s.bluetoothFingerprint();
		if (!fingerprint.equals(session.fingerprint)) {
			end(session, StartupStatus.CANCELLED, "settings_changed");
			return;
		}

		if (!isStartup && !btConnected) {
			end(session, StartupStatus.CANCELLED, "bt_disconnected");
			return;
		}

		Target selected = isStartup ? s.app : s.btTarget;
		if (selected == null) {
			end(session, StartupStatus.INVALID_TARGET, "not_selected");
			return;
		}

		final Target target = env.resolve(selected);
		if (target == null) {
			end(session, StartupStatus.INVALID_TARGET, "not_launchable");
			return;
		}

		String foreground = env.foregroundPackage();
		if (target.packageName.equals(foreground)) {
			session.launched = true;
			end(session, StartupStatus.STARTED, "already_foreground");
			return;
		}

		if (s.foregroundPolicy == ForegroundPolicy.HOME_ONLY) {
			String home = env.homePackage();
			if (foreground == null || home == null) {
				end(session, StartupStatus.SKIPPED_FOREGROUND_UNKNOWN, null);
				return;
			}
			if (!foreground.equals(home)) {
				end(session, StartupStatus.SKIPPED_NOT_HOME, null);
				return;
			}
		}

		session.attempt++;
		LaunchOutcome outcome = env.launchSingle(target);

		if (outcome == LaunchOutcome.ACCEPTED) {
			session.launched = true;
			report(session, StartupStatus.REQUEST_ACCEPTED, null);

			session.task = scheduler.schedule(new Runnable() {
				@Override
				public void run() {
					if (disabled || !isCurrent(session))
						return;
					try {
						String now = env.foregroundPackage();
						if (now == null)
							end(session, StartupStatus.UNCONFIRMED, "foreground_unknown");
						else if (now.equals(target.packageName))
							end(session, StartupStatus.STARTED, null);
						else
							end(session, StartupStatus.UNCONFIRMED, "other_foreground");
					} catch (RuntimeException e) {
						onError(e);
					}
				}
			}, VERIFY_DELAY_MS);
			return;
		}

		if (outcome == LaunchOutcome.FAILED_RETRYABLE && session.attempt <= s.retryCount) {
			session.task = scheduler.schedule(fireRunnable(session), s.retryDelayMs);
			report(session, StartupStatus.RETRY_SCHEDULED, s.retryDelayMs / 1000 + "s");
			return;
		}

		end(session, StartupStatus.REQUEST_FAILED,
			outcome == LaunchOutcome.FAILED_PERMANENT ? "permanent" : "retries_exhausted");
	}

	private void end(Session session, StartupStatus status, String detail) {
		if (session.task != null) {
			session.task.cancel();
			session.task = null;
		}
		session.ended = true;
		report(session, status, detail);
	}

	private void cancel(Session session, String reason) {
		if (session == null || session.ended)
			return;

		boolean wasPending = session.pending();
		if (session.task != null) {
			session.task.cancel();
			session.task = null;
		}
		session.ended = true;

		// A session already handed to the system only had its verification left.
		if (wasPending)
			report(session, StartupStatus.CANCELLED, reason);
	}

	private void report(Session session, StartupStatus status, String detail) {
		report(session.kind, session.cause, status, detail, session.attempt);
	}

	private void report(Kind kind, Cause cause, StartupStatus status, String detail, int attempt) {
		env.report(new StatusReport(kind, cause, status, detail, attempt, scheduler.nowMs()));
	}

	private void onError(RuntimeException e) {
		errors++;
		if (errors < MAX_ERRORS)
			return;

		disabled = true;
		try {
			if (startup != null && startup.task != null)
				startup.task.cancel();
			if (bluetooth != null && bluetooth.task != null)
				bluetooth.task.cancel();
		} catch (RuntimeException ignored) {}
		startup = null;
		bluetooth = null;

		try {
			report(Kind.STARTUP, Cause.NONE, StartupStatus.ENGINE_DISABLED, e.getClass().getSimpleName(), 0);
		} catch (RuntimeException ignored) {}
	}

	/** For tests and diagnostics. */
	public boolean isDisabled() {
		return disabled;
	}
}
