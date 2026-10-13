package com.pijulius.xposedteyes.core.compat;

/** Status vocabulary from the spec (section 3). */
public enum FeatureStatus {
	/** Code and automated tests done. */
	IMPLEMENTED,
	/** Confirmed on a named device / app version combination. */
	DEVICE_VERIFIED,
	/** Missing implementation point, material or test environment. */
	BLOCKED,
	/** Confirmed not supported on that combination. */
	UNSUPPORTED,
	/** Not checked yet. Does not mean supported. */
	NOT_TESTED
}
