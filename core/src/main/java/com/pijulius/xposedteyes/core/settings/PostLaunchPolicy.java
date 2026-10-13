package com.pijulius.xposedteyes.core.settings;

public enum PostLaunchPolicy {
	/** Never relaunch after the request (default). */
	NONE,
	/** Limited recovery after a verified abnormal exit. Requires a crash signal backend. */
	RECOVER_ABNORMAL_EXIT,
	/** User chosen limited keep-alive. Requires a crash/exit signal backend. */
	LIMITED_KEEP_ALIVE
}
