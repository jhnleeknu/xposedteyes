package com.pijulius.xposedteyes.core.settings;

public enum StartupTrigger {
	/** Android boot completed only. */
	COLD_BOOT,
	/** ACC ON / resume from sleep only. */
	ACC_ON,
	/** Both; the boot and the first ACC ON of the same boot form one session. */
	BOTH
}
