package com.pijulius.xposedteyes.core.startup;

import com.pijulius.xposedteyes.core.settings.AppSettings;
import com.pijulius.xposedteyes.core.settings.BluetoothLaunchConfig;
import com.pijulius.xposedteyes.core.settings.ConflictPolicy;
import com.pijulius.xposedteyes.core.settings.ForegroundPolicy;
import com.pijulius.xposedteyes.core.settings.LaunchMode;
import com.pijulius.xposedteyes.core.settings.StartupConfig;
import com.pijulius.xposedteyes.core.settings.StartupTrigger;
import com.pijulius.xposedteyes.core.startup.Ports.Cancellable;
import com.pijulius.xposedteyes.core.startup.Ports.Foreground;
import com.pijulius.xposedteyes.core.startup.Ports.Launcher;
import com.pijulius.xposedteyes.core.startup.Ports.RequestResult;
import com.pijulius.xposedteyes.core.startup.Ports.RuntimeStore;
import com.pijulius.xposedteyes.core.startup.Ports.Scheduler;
import com.pijulius.xposedteyes.core.startup.Ports.SettingsSource;
import com.pijulius.xposedteyes.core.startup.Ports.StatusSink;

/**
 * Decides when the startup app and the bluetooth app are launched. All methods
 * must be called on one thread (the scheduler's thread).
 *
 * <p>There is at most one active launch session. Sessions are deduplicated per
 * boot through {@link RuntimeStore} so a SystemUI restart or a repeated
 * broadcast never launches twice for the same boot.
 */
public final class LaunchCoordinator {
	static final String KEY_BOOT = "bootKey";
	static final String KEY_BOOT_PHASE = "bootPhase";
	static final String KEY_INITIAL_ACC_HANDLED = "initialAccHandled";
	static final String KEY_ACC_OFF_SEEN = "accOffSeen";

	/** Per-boot progress of the boot launch session. */
	enum BootPhase { NONE, SCHEDULED, REQUESTED, DONE, CANCELLED }

	enum Tri { UNKNOWN, ON, OFF }

	public static final int CONFIRM_DELAY_SINGLE_MS = 3_000;
	public static final int CONFIRM_DELAY_SPLIT_MS = 6_000;

	private final Scheduler scheduler;
	private final SettingsSource settingsSource;
	private final RuntimeStore store;
	private final Launcher launcher;
	private final StatusSink sink;

	private boolean initialized;
	private boolean bootCompleted;
	private boolean accAwaitingBoot;
	private Tri acc = Tri.UNKNOWN;
	private Tri bluetooth = Tri.UNKNOWN;
	private Session active;
	private long nextSessionId = 1;

	private final class Session {
		final long id = nextSessionId++;
		final LaunchPlan plan;
		final boolean bootSession;
		int attempts;
		boolean confirming;
		Cancellable timer;

		Session(LaunchPlan plan, boolean bootSession) {
			this.plan = plan;
			this.bootSession = bootSession;
		}
	}

	public LaunchCoordinator(Scheduler scheduler, SettingsSource settingsSource, RuntimeStore store,
			Launcher launcher, StatusSink sink) {
		this.scheduler = scheduler;
		this.settingsSource = settingsSource;
		this.store = store;
		this.launcher = launcher;
		this.sink = sink;
	}

	/**
	 * @param bootKey identifier of the current boot (local only, never exported)
	 * @param bootAlreadyCompleted whether the system verifiably finished booting
	 *        before this coordinator was created (e.g. SystemUI restarted)
	 */
	public void init(String bootKey, boolean bootAlreadyCompleted) {
		if (initialized)
			return;
		initialized = true;

		if (bootKey == null || !bootKey.equals(store.get(KEY_BOOT))) {
			store.put(KEY_BOOT, bootKey == null ? "" : bootKey);
			store.put(KEY_BOOT_PHASE, BootPhase.NONE.name());
			store.put(KEY_INITIAL_ACC_HANDLED, "false");
			store.put(KEY_ACC_OFF_SEEN, "false");
		}

		if (!bootAlreadyCompleted)
			return;

		bootCompleted = true;
		BootPhase phase = bootPhase();

		if (phase == BootPhase.SCHEDULED) {
			// An earlier SystemUI of this boot saw BOOT_COMPLETED but died before requesting.
			AppSettings settings = settingsSource.load();
			LaunchPlan plan = planFor(LaunchCause.BOOT, settings);
			if (plan != null) {
				startSession(plan, true, settings.startup.delayMs, settings);
			} else {
				setBootPhase(BootPhase.DONE);
			}
		} else if (phase == BootPhase.NONE) {
			// Never seen by us: the module may just have been activated on a running
			// system. Do not launch anything out of the blue.
			setBootPhase(BootPhase.DONE);
			report(0, LaunchCause.BOOT, LaunchStatus.SKIPPED_MISSED_EVENT, "boot_not_observed", 0, null, true);
		}
	}

	public void onBootCompleted() {
		if (bootPhase() != BootPhase.NONE)
			return; // duplicate broadcast for this boot

		bootCompleted = true;
		AppSettings settings = settingsSource.load();

		if (accAwaitingBoot) {
			accAwaitingBoot = false;
			LaunchPlan accPlan = planFor(LaunchCause.ACC_ON, settings);
			if (accPlan != null)
				startSession(accPlan, false, settings.startup.delayMs, settings);
		}

		LaunchPlan plan = planFor(LaunchCause.BOOT, settings);
		if (plan == null) {
			setBootPhase(BootPhase.DONE);
			if (!settings.startupActive())
				report(0, LaunchCause.BOOT, LaunchStatus.SKIPPED_DISABLED, null, 0, null, true);
			else if (settings.startup.triggersOnBoot())
				report(0, LaunchCause.BOOT, LaunchStatus.INVALID_TARGET, "not_selected", 0, null, true);
			return;
		}

		setBootPhase(BootPhase.SCHEDULED);
		startSession(plan, true, settings.startup.delayMs, settings);
	}

	public void onAccOn() {
		if (acc == Tri.ON)
			return; // the same transition is broadcast under several action names
		acc = Tri.ON;

		boolean initial = !"true".equals(store.get(KEY_ACC_OFF_SEEN));
		AppSettings settings = settingsSource.load();

		if (initial) {
			if ("true".equals(store.get(KEY_INITIAL_ACC_HANDLED)))
				return;
			store.put(KEY_INITIAL_ACC_HANDLED, "true");

			// With BOTH, the first ACC ON of a boot belongs to the boot session.
			if (settings.startup.trigger != StartupTrigger.ACC_ON)
				return;

			if (!bootCompleted) {
				accAwaitingBoot = planFor(LaunchCause.ACC_ON, settings) != null;
				return;
			}
		}

		LaunchPlan plan = planFor(LaunchCause.ACC_ON, settings);
		if (plan != null)
			startSession(plan, false, settings.startup.delayMs, settings);
	}

	public void onAccOff() {
		if (acc == Tri.OFF)
			return;
		acc = Tri.OFF;
		store.put(KEY_ACC_OFF_SEEN, "true");
		accAwaitingBoot = false;
		// The phone link drops with ACC; a reconnect after wakeup is a new connection.
		bluetooth = Tri.OFF;
		cancelActive("acc_off");
	}

	public void onBluetooth(boolean connected) {
		Tri next = connected ? Tri.ON : Tri.OFF;
		if (bluetooth == next)
			return; // repeated report of the same state
		Tri previous = bluetooth;
		bluetooth = next;

		AppSettings settings = settingsSource.load();

		if (connected) {
			LaunchPlan plan = planFor(LaunchCause.BT_CONNECT, settings);
			if (plan != null)
				startSession(plan, false, settings.bluetooth.delayMs, settings);
			return;
		}

		if (active != null && active.plan.cause == LaunchCause.BT_CONNECT)
			cancelActive("bt_disconnected");

		if (previous == Tri.ON) {
			LaunchPlan plan = planFor(LaunchCause.BT_DISCONNECT, settings);
			if (plan != null)
				startSession(plan, false, settings.bluetooth.delayMs, settings);
		}
	}

	/** Settings were saved. Never starts anything; cancels work that no longer matches. */
	public void onSettingsChanged() {
		if (active == null || active.confirming)
			return;
		AppSettings settings = settingsSource.load();
		if (!stillValid(active, settings))
			cancelActive("settings_changed");
	}

	/** Stops everything, e.g. when the feature is being shut down after repeated errors. */
	public void shutdown(String reason) {
		cancelActive(reason);
	}

	boolean hasActiveSession() {
		return active != null;
	}

	// --- sessions -----------------------------------------------------------------

	private void startSession(LaunchPlan plan, boolean bootSession, int delayMs, AppSettings settings) {
		if (active != null) {
			boolean sameKind = active.plan.cause.isBluetooth() == plan.cause.isBluetooth();
			if (!sameKind && !incomingWins(plan, settings.bluetooth.conflictPolicy)) {
				report(nextSessionId++, plan.cause, LaunchStatus.SKIPPED_CONFLICT,
					"conflict_with_" + active.plan.cause.name().toLowerCase(), 0, plan.describeTarget(), true);
				return;
			}
			cancelActive(sameKind ? "superseded" : "conflict");
		}

		final Session session = new Session(plan, bootSession);
		active = session;
		session.timer = scheduler.schedule(new Runnable() {
			@Override
			public void run() {
				fire(session);
			}
		}, delayMs);
	}

	private static boolean incomingWins(LaunchPlan incoming, ConflictPolicy policy) {
		switch (policy) {
			case STARTUP_FIRST:
				return !incoming.cause.isBluetooth();
			case BLUETOOTH_FIRST:
				return incoming.cause.isBluetooth();
			default:
				return true;
		}
	}

	private void fire(final Session session) {
		if (active != session)
			return;
		session.timer = null;

		AppSettings settings = settingsSource.load();
		if (!stillValid(session, settings)) {
			finish(session, LaunchStatus.CANCELLED, "settings_changed");
			return;
		}

		LaunchPlan plan = session.plan;
		Foreground fg = launcher.foreground(plan);
		if (fg == Foreground.TARGET) {
			finish(session, LaunchStatus.STARTED, "already_foreground");
			return;
		}
		if (plan.foregroundPolicy == ForegroundPolicy.HOME_ONLY && fg != Foreground.HOME) {
			finish(session, LaunchStatus.SKIPPED_NOT_HOME,
				fg == Foreground.UNKNOWN ? "foreground_unknown" : "other_app_in_front");
			return;
		}
		if (!launcher.isLaunchable(plan)) {
			finish(session, LaunchStatus.INVALID_TARGET, "not_launchable");
			return;
		}

		if (session.bootSession)
			setBootPhase(BootPhase.REQUESTED);
		session.attempts++;

		RequestResult result = launcher.request(plan);
		switch (result) {
			case ACCEPTED:
				session.confirming = true;
				report(session, LaunchStatus.REQUEST_ACCEPTED, null, false);
				session.timer = scheduler.schedule(new Runnable() {
					@Override
					public void run() {
						confirm(session);
					}
				}, plan.mode == LaunchMode.SPLIT ? CONFIRM_DELAY_SPLIT_MS : CONFIRM_DELAY_SINGLE_MS);
				return;

			case FAILED_RETRYABLE:
				if (session.attempts < plan.maxAttempts) {
					report(session, LaunchStatus.REQUEST_FAILED, "will_retry", false);
					session.timer = scheduler.schedule(new Runnable() {
						@Override
						public void run() {
							fire(session);
						}
					}, plan.retryDelayMs);
				} else {
					finish(session, LaunchStatus.REQUEST_FAILED, "retries_exhausted");
				}
				return;

			case FAILED_PERMANENT:
				finish(session, LaunchStatus.REQUEST_FAILED, "not_retryable");
				return;

			default:
				finish(session, LaunchStatus.INVALID_TARGET, "not_launchable");
		}
	}

	private void confirm(Session session) {
		if (active != session)
			return;
		session.timer = null;
		boolean started = launcher.confirmStarted(session.plan);
		finish(session, started ? LaunchStatus.STARTED : LaunchStatus.UNCONFIRMED,
			started ? null : "not_verified_in_front");
	}

	private void cancelActive(String reason) {
		Session session = active;
		if (session == null)
			return;
		if (session.timer != null) {
			session.timer.cancel();
			session.timer = null;
		}
		// A request that was already sent cannot be taken back; it just stays unverified.
		finish(session, session.confirming ? LaunchStatus.UNCONFIRMED : LaunchStatus.CANCELLED, reason);
	}

	private void finish(Session session, LaunchStatus status, String reason) {
		if (active == session)
			active = null;
		if (session.bootSession) {
			setBootPhase(status == LaunchStatus.CANCELLED && session.attempts == 0
				? BootPhase.CANCELLED : BootPhase.DONE);
		}
		report(session, status, reason, true);
	}

	private boolean stillValid(Session session, AppSettings settings) {
		LaunchPlan current = planFor(session.plan.cause, settings);
		return current != null && current.fingerprint.equals(session.plan.fingerprint);
	}

	/** Builds the plan the current settings call for, or null if the cause is not active. */
	static LaunchPlan planFor(LaunchCause cause, AppSettings settings) {
		StartupConfig s = settings.startup;
		BluetoothLaunchConfig b = settings.bluetooth;

		switch (cause) {
			case BOOT:
				if (!settings.startupActive() || !s.triggersOnBoot() || !s.hasTargets())
					return null;
				return startupPlan(cause, s, "S|");

			case ACC_ON:
				if (!settings.startupActive() || !s.triggersOnAcc() || !s.hasTargets())
					return null;
				return startupPlan(cause, s, "S|");

			case BT_CONNECT:
				if (!settings.bluetoothLaunchActive() || b.target == null)
					return null;
				return new LaunchPlan(cause, LaunchMode.SINGLE, b.target, null, ForegroundPolicy.ALWAYS,
					1, 0, "B|" + b.launchFingerprint());

			case BT_DISCONNECT:
				if (!settings.bluetoothDisconnectActive() || !s.hasTargets())
					return null;
				return startupPlan(cause, s, "D|" + b.delayMs + "|");
		}
		return null;
	}

	private static LaunchPlan startupPlan(LaunchCause cause, StartupConfig s, String prefix) {
		return new LaunchPlan(cause, s.mode, s.primary, s.mode == LaunchMode.SPLIT ? s.secondary : null,
			s.foregroundPolicy, 1 + s.retryCount, s.retryDelayMs, prefix + s.launchFingerprint());
	}

	// --- helpers ------------------------------------------------------------------

	private BootPhase bootPhase() {
		String v = store.get(KEY_BOOT_PHASE);
		if (v == null)
			return BootPhase.NONE;
		try {
			return BootPhase.valueOf(v);
		} catch (IllegalArgumentException e) {
			return BootPhase.NONE;
		}
	}

	private void setBootPhase(BootPhase phase) {
		store.put(KEY_BOOT_PHASE, phase.name());
	}

	private void report(Session session, LaunchStatus status, String reason, boolean isFinal) {
		report(session.id, session.plan.cause, status, reason, session.attempts,
			session.plan.describeTarget(), isFinal);
	}

	private void report(long id, LaunchCause cause, LaunchStatus status, String reason, int attempts,
			String target, boolean isFinal) {
		sink.report(new LaunchRecord(id, cause, status, reason, attempts, target, isFinal));
	}
}
