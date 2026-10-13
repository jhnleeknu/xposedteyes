package com.pijulius.xposedteyes.core.startup;

import com.pijulius.xposedteyes.core.settings.AppSettings;

/** Platform dependencies of {@link LaunchCoordinator}, injected so it can be unit tested. */
public final class Ports {
	private Ports() {}

	public interface Cancellable {
		void cancel();
	}

	/** Runs tasks on the coordinator thread after a monotonic delay. */
	public interface Scheduler {
		Cancellable schedule(Runnable task, long delayMs);
	}

	/** Re-reads the current settings each call. */
	public interface SettingsSource {
		AppSettings load();
	}

	/** Small persistent key/value store that survives a SystemUI restart. */
	public interface RuntimeStore {
		String get(String key);
		void put(String key, String value);
	}

	public interface StatusSink {
		void report(LaunchRecord record);
	}

	public enum Foreground {
		/** The (primary) target is already in front. */
		TARGET,
		HOME,
		OTHER,
		UNKNOWN
	}

	public enum RequestResult {
		ACCEPTED,
		/** Explicit failure that may succeed later (e.g. system not ready). */
		FAILED_RETRYABLE,
		/** Explicit failure that will not change by retrying (e.g. permission denied). */
		FAILED_PERMANENT,
		INVALID_TARGET
	}

	public interface Launcher {
		/** Whether every target of the plan currently resolves to an enabled launchable activity. */
		boolean isLaunchable(LaunchPlan plan);

		Foreground foreground(LaunchPlan plan);

		RequestResult request(LaunchPlan plan);

		/** Whether the launched activity is verifiably in front now. */
		boolean confirmStarted(LaunchPlan plan);
	}
}
