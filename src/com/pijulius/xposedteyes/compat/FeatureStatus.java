package com.pijulius.xposedteyes.compat;

import com.pijulius.xposedteyes.R;

/**
 * Implementation / verification state of each feature, as shown in
 * "Advanced / Diagnostics" and documented in docs/COMPATIBILITY.md. Keep both
 * in sync. "Implemented" never implies "verified on a device".
 */
public final class FeatureStatus {
	private FeatureStatus() {}

	public enum State { IMPLEMENTED, DEVICE_VERIFIED, BLOCKED, UNSUPPORTED, NOT_TESTED }

	public static final class Row {
		public final int name;
		public final State code;
		public final State device;

		Row(int name, State code, State device) {
			this.name = name;
			this.code = code;
			this.device = device;
		}
	}

	/** Feature name, code state, device state. */
	public static final Row[] ROWS = {
		new Row(R.string.feature_startup, State.IMPLEMENTED, State.NOT_TESTED),
		new Row(R.string.feature_bt_launch, State.IMPLEMENTED, State.NOT_TESTED),
		new Row(R.string.feature_post_launch_recovery, State.BLOCKED, State.NOT_TESTED),
		new Row(R.string.feature_last_app_restore, State.BLOCKED, State.NOT_TESTED),
		new Row(R.string.feature_carplay_density, State.BLOCKED, State.NOT_TESTED),
		new Row(R.string.feature_carplay_scale, State.BLOCKED, State.NOT_TESTED),
		new Row(R.string.feature_carplay_diag, State.IMPLEMENTED, State.NOT_TESTED),
		new Row(R.string.feature_btmic_controls, State.BLOCKED, State.NOT_TESTED),
		new Row(R.string.feature_btmic_diag, State.IMPLEMENTED, State.NOT_TESTED),
	};
}
