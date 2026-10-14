package com.pijulius.xposedteyes.startup;

import com.pijulius.xposedteyes.settings.StartupSettings;
import com.pijulius.xposedteyes.settings.Target;

/** Everything the coordinator needs from Android; faked in unit tests. */
public interface StartupEnv {
	/** Re-reads the stored settings (never a stale snapshot). */
	StartupSettings loadSettings();

	/**
	 * Returns the launchable component for {@code target}, or null when the
	 * app is not installed, disabled or has no launcher activity. A
	 * package-only target or an outdated component of the same package may
	 * resolve to that package's current launcher activity; never to another app.
	 */
	Target resolve(Target target);

	LaunchOutcome launchSingle(Target target);

	LaunchOutcome launchSplit(Target primary, Target secondary);

	/** Package of the top activity, or null when it cannot be determined. */
	String foregroundPackage();

	/** Package of the default home app, or null when unknown/ambiguous. */
	String homePackage();

	/** True when a reliable app exit signal (crash vs. normal vs. force stop) exists. */
	boolean exitSignalAvailable();

	void report(StatusReport report);
}
