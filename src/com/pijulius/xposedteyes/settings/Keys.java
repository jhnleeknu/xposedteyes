package com.pijulius.xposedteyes.settings;

/**
 * Preference keys shared by the settings UI (module process) and the hooks
 * (SystemUI and other hooked processes, read through XSharedPreferences).
 *
 * Values are stored flat in the existing world-readable preferences file so
 * XSharedPreferences keeps working. See {@link StartupSettings} for the typed
 * model and validation rules.
 */
public final class Keys {
	private Keys() {}

	public static final String PACKAGE = "com.pijulius.xposedteyes";

	/** Integer schema version of the stored settings. Absent means legacy (pre 3.1). */
	public static final String SCHEMA_VERSION = "settingsSchemaVersion";
	public static final int CURRENT_SCHEMA = 1;

	/** Set by migration when legacy auto launch settings were converted. */
	public static final String MIGRATION_NOTICE = "migrationNotice";

	/** Master switch: when true every feature added in 3.1 is stopped. */
	public static final String NEW_FEATURES_DISABLED = "newFeaturesDisabled";

	// Startup app management
	public static final String STARTUP_ENABLED = "startupEnabled";
	public static final String STARTUP_TRIGGER = "startupTrigger";
	public static final String STARTUP_MODE = "startupMode";
	public static final String STARTUP_PRIMARY = "startupPrimary";
	public static final String STARTUP_SECONDARY = "startupSecondary";
	public static final String STARTUP_DELAY_S = "startupDelaySeconds";
	public static final String STARTUP_RETRY_COUNT = "startupRetryCount";
	public static final String STARTUP_RETRY_DELAY_S = "startupRetryDelaySeconds";
	public static final String STARTUP_FOREGROUND_POLICY = "startupForegroundPolicy";
	public static final String STARTUP_POST_LAUNCH_POLICY = "startupPostLaunchPolicy";
	public static final String STARTUP_RECOVERY_LIMIT = "startupRecoveryLimit";
	public static final String STARTUP_RECOVERY_DELAY_S = "startupRecoveryDelaySeconds";
	public static final String A11Y_ROOT_AUTO_ENABLE = "a11yRootAutoEnable";

	// Bluetooth connection launch
	public static final String BT_LAUNCH_ENABLED = "btLaunchEnabled";
	public static final String BT_LAUNCH_TARGET = "btLaunchTarget";
	public static final String BT_LAUNCH_DELAY_S = "btLaunchDelaySeconds";
	public static final String BT_CONFLICT_POLICY = "btConflictPolicy";
	public static final String BT_LAUNCH_ON_DISCONNECT = "btLaunchStartupOnDisconnect";

	// Read-only diagnostics hooks (installed only when enabled, at process start)
	public static final String DIAG_CARLINK_VIDEO = "diagCarlinkVideo";
	public static final String DIAG_AUDIO_PATH = "diagAudioPath";

	// Legacy keys (3.0 and earlier). Kept for the manual split screen shortcuts
	// and for migration; the auto launch behaviour moved to the keys above.
	public static final String LEGACY_LAUNCH_ON_STARTUP = "launchOnStartup";
	public static final String LEGACY_LAUNCH_ON_BLUETOOTH = "launchOnBluetooth";
	public static final String LEGACY_MUSIC_APP = "launcherMusicApp";
	public static final String LEGACY_NAVIGATION_APP = "launcherNavigationApp";
	public static final String LEGACY_VIDEOS_APP = "launcherVideosApp";
	public static final String LEGACY_BLUETOOTH_APP = "launcherBluetoothApp";

	/** Broadcast from the settings app to SystemUI: settings changed, re-read them. */
	public static final String ACTION_SETTINGS_CHANGED = PACKAGE + ".SETTINGS_CHANGED";
	/** Broadcast from SystemUI to the settings app carrying a startup status report. */
	public static final String ACTION_STARTUP_STATUS = PACKAGE + ".STARTUP_STATUS";
	/** Signature permission guarding {@link #ACTION_SETTINGS_CHANGED}. */
	public static final String PERMISSION_CONTROL = PACKAGE + ".permission.CONTROL";
	/** Permission SystemUI holds; guards the status receiver in the module app. */
	public static final String PERMISSION_STATUS_SENDER = "android.permission.STATUS_BAR_SERVICE";
}
