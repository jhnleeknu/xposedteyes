package com.pijulius.xposedteyes.startup;

/** Result of handing a launch request to the system. */
public enum LaunchOutcome {
	/** The request was handed over without error. Not proof the app is visible. */
	ACCEPTED,
	/** Explicit failure that may succeed later (e.g. system not ready). */
	FAILED_RETRYABLE,
	/** Explicit failure that will not change by retrying (missing, not permitted). */
	FAILED_PERMANENT
}
