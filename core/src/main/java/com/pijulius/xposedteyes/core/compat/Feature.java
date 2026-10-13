package com.pijulius.xposedteyes.core.compat;

/**
 * Build-time status of each feature. Runtime checks (module active, class found,
 * accessibility enabled) are layered on top by the app; they can only lower a
 * status, never raise it to DEVICE_VERIFIED.
 */
public enum Feature {
	STARTUP_LAUNCH(FeatureStatus.IMPLEMENTED, FeatureStatus.NOT_TESTED),
	STARTUP_SPLIT(FeatureStatus.IMPLEMENTED, FeatureStatus.NOT_TESTED),
	BLUETOOTH_LAUNCH(FeatureStatus.IMPLEMENTED, FeatureStatus.NOT_TESTED),
	POST_LAUNCH_RECOVERY(FeatureStatus.BLOCKED, FeatureStatus.NOT_TESTED),
	LAST_APP_RESTORE(FeatureStatus.BLOCKED, FeatureStatus.NOT_TESTED),
	ANDROID_AUTO_VIDEO(FeatureStatus.IMPLEMENTED, FeatureStatus.NOT_TESTED),
	CARPLAY_DISPLAY(FeatureStatus.BLOCKED, FeatureStatus.NOT_TESTED),
	BT_MIC_DIAGNOSTICS(FeatureStatus.IMPLEMENTED, FeatureStatus.NOT_TESTED),
	BT_MIC_CONTROLS(FeatureStatus.BLOCKED, FeatureStatus.NOT_TESTED);

	public final FeatureStatus code;
	public final FeatureStatus device;

	Feature(FeatureStatus code, FeatureStatus device) {
		this.code = code;
		this.device = device;
	}

	/** Whether the feature may be switched on by the user at all. */
	public boolean controllable() {
		return code == FeatureStatus.IMPLEMENTED || code == FeatureStatus.DEVICE_VERIFIED;
	}
}
