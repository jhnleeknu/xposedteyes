package com.pijulius.xposedteyes.core.settings;

/** Immutable, validated "launch an app when bluetooth connects" configuration. */
public final class BluetoothLaunchConfig {
	public final boolean enabled;
	public final LaunchTarget target;
	public final int delayMs;
	public final ConflictPolicy conflictPolicy;
	/** Relaunch the startup target after the phone disconnects (legacy behaviour). */
	public final boolean launchOnDisconnect;

	public BluetoothLaunchConfig(boolean enabled, LaunchTarget target, int delayMs,
			ConflictPolicy conflictPolicy, boolean launchOnDisconnect) {
		this.enabled = enabled;
		this.target = target;
		this.delayMs = delayMs;
		this.conflictPolicy = conflictPolicy;
		this.launchOnDisconnect = launchOnDisconnect;
	}

	public static BluetoothLaunchConfig defaults() {
		return new BluetoothLaunchConfig(false, null, Limits.BT_DELAY_DEFAULT_MS,
			ConflictPolicy.STARTUP_FIRST, false);
	}

	public String launchFingerprint() {
		return enabled + "|" + target + "|" + delayMs;
	}
}
