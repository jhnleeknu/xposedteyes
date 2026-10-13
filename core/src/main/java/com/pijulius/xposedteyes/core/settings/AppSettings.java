package com.pijulius.xposedteyes.core.settings;

import java.util.Map;

/**
 * Typed, validated view of the module preferences. Both the settings UI and the
 * SystemUI hook build their view through {@link #read(Map)} so they apply the
 * same defaults, ranges and legacy fallback.
 */
public final class AppSettings {
	public final int schemaVersion;
	public final boolean killSwitch;
	public final StartupConfig startup;
	public final BluetoothLaunchConfig bluetooth;
	public final boolean rootAccessibilityAssist;
	/** True when the values were derived from unmigrated legacy keys. */
	public final boolean derivedFromLegacy;

	AppSettings(int schemaVersion, boolean killSwitch, StartupConfig startup,
			BluetoothLaunchConfig bluetooth, boolean rootAccessibilityAssist, boolean derivedFromLegacy) {
		this.schemaVersion = schemaVersion;
		this.killSwitch = killSwitch;
		this.startup = startup;
		this.bluetooth = bluetooth;
		this.rootAccessibilityAssist = rootAccessibilityAssist;
		this.derivedFromLegacy = derivedFromLegacy;
	}

	public static AppSettings defaults() {
		return new AppSettings(Keys.CURRENT_SCHEMA_VERSION, false, StartupConfig.defaults(),
			BluetoothLaunchConfig.defaults(), false, false);
	}

	/** Startup launching is effectively on (switch on and kill switch off). */
	public boolean startupActive() {
		return !killSwitch && startup.enabled;
	}

	public boolean bluetoothLaunchActive() {
		return !killSwitch && bluetooth.enabled;
	}

	public boolean bluetoothDisconnectActive() {
		return !killSwitch && bluetooth.launchOnDisconnect;
	}

	/**
	 * A preferences map written before schema 1 that still holds user values.
	 * An empty map is a fresh install, not a legacy one.
	 */
	public static boolean isUnmigratedLegacy(Map<String, ?> map) {
		if (map.containsKey(Keys.SCHEMA_VERSION))
			return false;
		for (String key : map.keySet()) {
			if (!key.equals(Keys.SCHEMA_VERSION))
				return true;
		}
		return false;
	}

	public static AppSettings read(Map<String, ?> map) {
		if (isUnmigratedLegacy(map))
			return readLegacy(map);

		int schema = Values.getClampedInt(map, Keys.SCHEMA_VERSION, Keys.CURRENT_SCHEMA_VERSION, 0, Integer.MAX_VALUE);
		boolean kill = Values.getBoolean(map, Keys.KILL_SWITCH, false);

		StartupConfig d = StartupConfig.defaults();
		StartupConfig startup = new StartupConfig(
			Values.getBoolean(map, Keys.STARTUP_ENABLED, d.enabled),
			Values.getEnum(map, Keys.STARTUP_TRIGGER, StartupTrigger.class, d.trigger),
			Values.getEnum(map, Keys.STARTUP_MODE, LaunchMode.class, d.mode),
			LaunchTarget.parse(Values.getString(map, Keys.STARTUP_PRIMARY, null)),
			LaunchTarget.parse(Values.getString(map, Keys.STARTUP_SECONDARY, null)),
			Values.getClampedInt(map, Keys.STARTUP_DELAY_MS, d.delayMs,
				Limits.STARTUP_DELAY_MIN_MS, Limits.STARTUP_DELAY_MAX_MS),
			Values.getClampedInt(map, Keys.STARTUP_RETRY_COUNT, d.retryCount,
				Limits.RETRY_COUNT_MIN, Limits.RETRY_COUNT_MAX),
			Values.getClampedInt(map, Keys.STARTUP_RETRY_DELAY_MS, d.retryDelayMs,
				Limits.RETRY_DELAY_MIN_MS, Limits.RETRY_DELAY_MAX_MS),
			Values.getEnum(map, Keys.STARTUP_FOREGROUND_POLICY, ForegroundPolicy.class, d.foregroundPolicy),
			Values.getEnum(map, Keys.STARTUP_POST_LAUNCH_POLICY, PostLaunchPolicy.class, d.postLaunchPolicy),
			Values.getClampedInt(map, Keys.STARTUP_RECOVERY_LIMIT, d.recoveryLimit,
				Limits.RECOVERY_LIMIT_MIN, Limits.RECOVERY_LIMIT_MAX),
			Values.getClampedInt(map, Keys.STARTUP_RECOVERY_DELAY_MS, d.recoveryDelayMs,
				Limits.RECOVERY_DELAY_MIN_MS, Limits.RECOVERY_DELAY_MAX_MS));

		BluetoothLaunchConfig bd = BluetoothLaunchConfig.defaults();
		BluetoothLaunchConfig bt = new BluetoothLaunchConfig(
			Values.getBoolean(map, Keys.BT_LAUNCH_ENABLED, bd.enabled),
			LaunchTarget.parse(Values.getString(map, Keys.BT_LAUNCH_TARGET, null)),
			Values.getClampedInt(map, Keys.BT_LAUNCH_DELAY_MS, bd.delayMs,
				Limits.BT_DELAY_MIN_MS, Limits.BT_DELAY_MAX_MS),
			Values.getEnum(map, Keys.BT_CONFLICT_POLICY, ConflictPolicy.class, bd.conflictPolicy),
			Values.getBoolean(map, Keys.BT_LAUNCH_ON_DISCONNECT, bd.launchOnDisconnect));

		return new AppSettings(schema, kill, startup, bt,
			Values.getBoolean(map, Keys.ROOT_ACCESSIBILITY_ASSIST, false), false);
	}

	/**
	 * Reproduces what the previous release effectively did with these values so a
	 * device that has not opened the new settings screen yet keeps its behaviour:
	 * split screen music + navigation on every ACC ON (including the first one
	 * after boot) one second later, the bluetooth app three seconds after a
	 * connection (the hook defaulted to on when the key was never saved) and the
	 * startup split again after a disconnect.
	 */
	static AppSettings readLegacy(Map<String, ?> map) {
		boolean launchOnStartup = Values.getBoolean(map, Keys.LEGACY_LAUNCH_ON_STARTUP, false);
		boolean bluetoothHook = Values.getBoolean(map, Keys.LEGACY_LAUNCH_ON_BLUETOOTH, true);

		StartupConfig d = StartupConfig.defaults();
		StartupConfig startup = new StartupConfig(
			launchOnStartup, StartupTrigger.BOTH, LaunchMode.SPLIT,
			LaunchTarget.parse(Values.getString(map, Keys.LEGACY_MUSIC_APP, "")),
			LaunchTarget.parse(Values.getString(map, Keys.LEGACY_NAVIGATION_APP, "")),
			Limits.LEGACY_STARTUP_DELAY_MS, d.retryCount, d.retryDelayMs,
			ForegroundPolicy.ALWAYS, PostLaunchPolicy.NONE, d.recoveryLimit, d.recoveryDelayMs);

		LaunchTarget btTarget = LaunchTarget.parse(Values.getString(map, Keys.LEGACY_BLUETOOTH_APP, ""));
		BluetoothLaunchConfig bt = new BluetoothLaunchConfig(
			bluetoothHook && btTarget != null, btTarget, Limits.LEGACY_BT_DELAY_MS,
			ConflictPolicy.BLUETOOTH_FIRST, bluetoothHook && launchOnStartup);

		return new AppSettings(0, false, startup, bt, false, true);
	}
}
