package com.pijulius.xposedteyes.settings;

/**
 * Preference keys shared by the settings UI (module process) and the hooks
 * (SystemUI, read through XSharedPreferences).
 */
public final class Keys {
	private Keys() {}

	public static final String PACKAGE = "com.pijulius.xposedteyes";

	// Startup app
	public static final String STARTUP_ENABLED = "startupEnabled";
	public static final String STARTUP_TRIGGER = "startupTrigger";
	public static final String STARTUP_APP = "startupPrimary";
	public static final String STARTUP_DELAY_S = "startupDelaySeconds";
	public static final String STARTUP_RETRY_COUNT = "startupRetryCount";
	public static final String STARTUP_RETRY_DELAY_S = "startupRetryDelaySeconds";
	public static final String STARTUP_FOREGROUND_POLICY = "startupForegroundPolicy";

	// Bluetooth connection launch
	public static final String BT_LAUNCH_ENABLED = "btLaunchEnabled";
	public static final String BT_LAUNCH_TARGET = "btLaunchTarget";
	public static final String BT_LAUNCH_DELAY_S = "btLaunchDelaySeconds";
	public static final String BT_CONFLICT_POLICY = "btConflictPolicy";

	// Read-only diagnostics hooks (installed only when enabled, at process start)
	public static final String DIAG_CARLINK_VIDEO = "diagCarlinkVideo";
	public static final String DIAG_AUDIO_PATH = "diagAudioPath";

	/** Broadcast from the settings app to SystemUI: settings changed, re-read them. */
	public static final String ACTION_SETTINGS_CHANGED = PACKAGE + ".SETTINGS_CHANGED";
	/** Broadcast from SystemUI to the settings app carrying a startup status report. */
	public static final String ACTION_STARTUP_STATUS = PACKAGE + ".STARTUP_STATUS";
	/** Signature permission guarding {@link #ACTION_SETTINGS_CHANGED}. */
	public static final String PERMISSION_CONTROL = PACKAGE + ".permission.CONTROL";
}
