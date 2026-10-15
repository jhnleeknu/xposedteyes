package com.pijulius.xposedteyes.startup;

/** One status event. Contains no device identifiers. */
public final class StatusReport {
	public enum Kind { STARTUP, BLUETOOTH }

	public enum Cause {
		BOOT, BOOT_CATCHUP, ACC_INITIAL, ACC_RESUME, BT_CONNECT, NONE
	}

	public final Kind kind;
	public final Cause cause;
	public final StartupStatus status;
	public final String detail;
	public final int attempt;
	public final long monotonicMs;

	public StatusReport(Kind kind, Cause cause, StartupStatus status, String detail, int attempt,
		long monotonicMs)
	{
		this.kind = kind;
		this.cause = cause;
		this.status = status;
		this.detail = detail == null ? "" : detail;
		this.attempt = attempt;
		this.monotonicMs = monotonicMs;
	}

	@Override
	public String toString() {
		return kind + " " + cause + " " + status + (detail.isEmpty() ? "" : " (" + detail + ")") +
			(attempt > 0 ? " #" + attempt : "");
	}
}
