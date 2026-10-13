package com.pijulius.xposedteyes.core.startup;

/** Outcome of a launch session (spec 5.5) plus the coordinator's own skip reasons. */
public enum LaunchStatus {
	SKIPPED_DISABLED,
	INVALID_TARGET,
	REQUEST_FAILED,
	/** The request was handed to the system; the screen has not been verified yet. */
	REQUEST_ACCEPTED,
	/** The selected activity was verified to be in front. */
	STARTED,
	/** Requested, but the result could not be verified; never re-requested automatically. */
	UNCONFIRMED,
	/**
	 * The app exited after starting. Not produced yet: no reliable exit/crash signal
	 * is wired in (see docs/COMPATIBILITY.md, post-launch policy).
	 */
	FINISHED_AFTER_START,
	CANCELLED,
	SKIPPED_CONFLICT,
	SKIPPED_NOT_HOME,
	/** Boot completed but this boot was never seen by an earlier SystemUI instance. */
	SKIPPED_MISSED_EVENT
}
