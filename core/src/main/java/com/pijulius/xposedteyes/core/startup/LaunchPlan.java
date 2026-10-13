package com.pijulius.xposedteyes.core.startup;

import com.pijulius.xposedteyes.core.settings.ForegroundPolicy;
import com.pijulius.xposedteyes.core.settings.LaunchMode;
import com.pijulius.xposedteyes.core.settings.LaunchTarget;

/** What a session launches, fixed at the time the triggering event arrived. */
public final class LaunchPlan {
	public final LaunchCause cause;
	public final LaunchMode mode;
	public final LaunchTarget primary;
	public final LaunchTarget secondary;
	public final ForegroundPolicy foregroundPolicy;
	public final int maxAttempts;
	public final int retryDelayMs;
	final String fingerprint;

	LaunchPlan(LaunchCause cause, LaunchMode mode, LaunchTarget primary, LaunchTarget secondary,
			ForegroundPolicy foregroundPolicy, int maxAttempts, int retryDelayMs, String fingerprint) {
		this.cause = cause;
		this.mode = mode;
		this.primary = primary;
		this.secondary = secondary;
		this.foregroundPolicy = foregroundPolicy;
		this.maxAttempts = maxAttempts;
		this.retryDelayMs = retryDelayMs;
		this.fingerprint = fingerprint;
	}

	public String describeTarget() {
		return mode == LaunchMode.SPLIT ? primary + " + " + secondary : String.valueOf(primary);
	}
}
