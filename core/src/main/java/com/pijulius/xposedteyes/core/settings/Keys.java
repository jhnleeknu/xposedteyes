package com.pijulius.xposedteyes.core.settings;

/**
 * Preference keys shared by the settings UI (module app process) and the
 * hooks (SystemUI process, read through XSharedPreferences).
 */
public final class Keys {
	private Keys() {}

	public static final String SCHEMA_VERSION = "settingsSchemaVersion";
	public static final int CURRENT_SCHEMA_VERSION = 1;

	/** "Turn off all new features" master switch. */
	public static final String KILL_SWITCH = "newFeaturesDisabled";

	public static final String STARTUP_ENABLED = "startupEnabled";
	public static final String STARTUP_TRIGGER = "startupTrigger";
	public static final String STARTUP_MODE = "startupMode";
	public static final String STARTUP_PRIMARY = "startupPrimary";
	public static final String STARTUP_SECONDARY = "startupSecondary";
	public static final String STARTUP_DELAY_MS = "startupDelayMs";
	public static final String STARTUP_RETRY_COUNT = "startupRetryCount";
	public static final String STARTUP_RETRY_DELAY_MS = "startupRetryDelayMs";
	public static final String STARTUP_FOREGROUND_POLICY = "startupForegroundPolicy";
	public static final String STARTUP_POST_LAUNCH_POLICY = "startupPostLaunchPolicy";
	public static final String STARTUP_RECOVERY_LIMIT = "startupRecoveryLimit";
	public static final String STARTUP_RECOVERY_DELAY_MS = "startupRecoveryDelayMs";

	public static final String BT_LAUNCH_ENABLED = "btLaunchEnabled";
	public static final String BT_LAUNCH_TARGET = "btLaunchTarget";
	public static final String BT_LAUNCH_DELAY_MS = "btLaunchDelayMs";
	public static final String BT_CONFLICT_POLICY = "btConflictPolicy";
	public static final String BT_LAUNCH_ON_DISCONNECT = "btLaunchOnDisconnect";

	/** Explicit consent for re-enabling the accessibility service through su. */
	public static final String ROOT_ACCESSIBILITY_ASSIST = "rootAccessibilityAssist";

	/** Set by the migration when legacy autostart settings were carried over. */
	public static final String MIGRATION_NOTICE = "migrationNotice";

	// Phase 2/3 placeholders: stored only, never applied until a verified backend exists.
	public static final String CARPLAY_ENABLED = "carplayEnabled";
	public static final String BTMIC_ENABLED = "btMicEnabled";

	// Legacy (schema 0) keys.
	public static final String LEGACY_LAUNCH_ON_STARTUP = "launchOnStartup";
	public static final String LEGACY_LAUNCH_ON_BLUETOOTH = "launchOnBluetooth";
	public static final String LEGACY_MUSIC_APP = "launcherMusicApp";
	public static final String LEGACY_NAVIGATION_APP = "launcherNavigationApp";
	public static final String LEGACY_VIDEOS_APP = "launcherVideosApp";
	public static final String LEGACY_BLUETOOTH_APP = "launcherBluetoothApp";

	/** Keys owned by the startup / bluetooth launch feature (used for "restore defaults"). */
	public static final String[] LAUNCH_FEATURE_KEYS = {
		STARTUP_ENABLED, STARTUP_TRIGGER, STARTUP_MODE, STARTUP_PRIMARY, STARTUP_SECONDARY,
		STARTUP_DELAY_MS, STARTUP_RETRY_COUNT, STARTUP_RETRY_DELAY_MS, STARTUP_FOREGROUND_POLICY,
		STARTUP_POST_LAUNCH_POLICY, STARTUP_RECOVERY_LIMIT, STARTUP_RECOVERY_DELAY_MS,
		BT_LAUNCH_ENABLED, BT_LAUNCH_TARGET, BT_LAUNCH_DELAY_MS, BT_CONFLICT_POLICY,
		BT_LAUNCH_ON_DISCONNECT, ROOT_ACCESSIBILITY_ASSIST,
	};
}
