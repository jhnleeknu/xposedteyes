package com.pijulius.xposedteyes.core.settings;

public enum ForegroundPolicy {
	/** Bring the selected app to the front with a normal activity start. */
	ALWAYS,
	/** Only launch when the home/launcher is in front; skip if that cannot be determined. */
	HOME_ONLY
}
