package com.pijulius.xposedteyes.startup;

/** Status values reported for startup and Bluetooth launch sessions. */
public enum StartupStatus {
	/** A launch is scheduled after the configured delay. */
	SCHEDULED,
	/** Auto launch is switched off; nothing requested. */
	SKIPPED_DISABLED,
	/** No app selected, or the app was removed/disabled. Nothing requested. */
	INVALID_TARGET,
	/** Explicit launch error; retries (if any) used up. */
	REQUEST_FAILED,
	/** A retryable failure; another request is scheduled. */
	RETRY_SCHEDULED,
	/** Request handed over; the visible result is not confirmed yet. */
	REQUEST_ACCEPTED,
	/** The selected app was seen in front (or already was). */
	STARTED,
	/** Request handed over but the result could not be confirmed. Not retried. */
	UNCONFIRMED,
	/** A pending launch was cancelled (ACC OFF, settings change, disconnect...). */
	CANCELLED,
	/** Skipped because the other auto launch has priority. */
	SKIPPED_CONFLICT,
	/** "Home screen only" policy and another app is in front. */
	SKIPPED_NOT_HOME,
	/** "Home screen only" policy and the foreground app could not be determined. */
	SKIPPED_FOREGROUND_UNKNOWN,
	/** Hooks were registered after this boot had already completed (e.g. module just enabled). */
	SKIPPED_LATE_REGISTRATION,
	/** Repeated internal errors; the engine stopped itself for this process. */
	ENGINE_DISABLED
}
