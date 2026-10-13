package com.pijulius.xposedteyes.core.settings;

/** Ranges and defaults from the spec (section 5.1). */
public final class Limits {
	private Limits() {}

	public static final int STARTUP_DELAY_MIN_MS = 0;
	public static final int STARTUP_DELAY_MAX_MS = 60_000;
	public static final int STARTUP_DELAY_DEFAULT_MS = 5_000;
	public static final int[] STARTUP_DELAY_PRESETS_S = {0, 3, 5, 10, 15};

	public static final int RETRY_COUNT_MIN = 0;
	public static final int RETRY_COUNT_MAX = 3;
	public static final int RETRY_COUNT_DEFAULT = 1;

	public static final int RETRY_DELAY_MIN_MS = 1_000;
	public static final int RETRY_DELAY_MAX_MS = 30_000;
	public static final int RETRY_DELAY_DEFAULT_MS = 5_000;

	public static final int RECOVERY_LIMIT_MIN = 1;
	public static final int RECOVERY_LIMIT_MAX = 3;
	public static final int RECOVERY_LIMIT_DEFAULT = 1;

	public static final int RECOVERY_DELAY_MIN_MS = 5_000;
	public static final int RECOVERY_DELAY_MAX_MS = 60_000;
	public static final int RECOVERY_DELAY_DEFAULT_MS = 5_000;

	public static final int BT_DELAY_MIN_MS = 0;
	public static final int BT_DELAY_MAX_MS = 60_000;
	public static final int BT_DELAY_DEFAULT_MS = 3_000;

	/** Legacy autostart fired one second after ACC ON. */
	public static final int LEGACY_STARTUP_DELAY_MS = 1_000;
	/** Legacy bluetooth launch fired three seconds after the state change. */
	public static final int LEGACY_BT_DELAY_MS = 3_000;
}
