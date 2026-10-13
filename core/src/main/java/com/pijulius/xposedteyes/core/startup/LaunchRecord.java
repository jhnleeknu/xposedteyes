package com.pijulius.xposedteyes.core.startup;

/** One status report from the coordinator. */
public final class LaunchRecord {
	public final long sessionId;
	public final LaunchCause cause;
	public final LaunchStatus status;
	/** Short machine readable reason, e.g. "acc_off", "retries_exhausted". */
	public final String reason;
	public final int attempts;
	public final String target;
	/** False for intermediate reports (e.g. REQUEST_ACCEPTED before confirmation). */
	public final boolean isFinal;

	public LaunchRecord(long sessionId, LaunchCause cause, LaunchStatus status, String reason,
			int attempts, String target, boolean isFinal) {
		this.sessionId = sessionId;
		this.cause = cause;
		this.status = status;
		this.reason = reason;
		this.attempts = attempts;
		this.target = target;
		this.isFinal = isFinal;
	}

	@Override
	public String toString() {
		return "#" + sessionId + " " + cause + " " + status + (reason == null ? "" : " (" + reason + ")") +
			" attempts=" + attempts + " target=" + target + (isFinal ? "" : " [pending]");
	}
}
