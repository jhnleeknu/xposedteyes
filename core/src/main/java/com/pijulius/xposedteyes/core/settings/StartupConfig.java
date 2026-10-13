package com.pijulius.xposedteyes.core.settings;

/** Immutable, validated startup launch configuration. */
public final class StartupConfig {
	public final boolean enabled;
	public final StartupTrigger trigger;
	public final LaunchMode mode;
	public final LaunchTarget primary;
	public final LaunchTarget secondary;
	public final int delayMs;
	public final int retryCount;
	public final int retryDelayMs;
	public final ForegroundPolicy foregroundPolicy;
	public final PostLaunchPolicy postLaunchPolicy;
	public final int recoveryLimit;
	public final int recoveryDelayMs;

	public StartupConfig(boolean enabled, StartupTrigger trigger, LaunchMode mode,
			LaunchTarget primary, LaunchTarget secondary, int delayMs, int retryCount,
			int retryDelayMs, ForegroundPolicy foregroundPolicy, PostLaunchPolicy postLaunchPolicy,
			int recoveryLimit, int recoveryDelayMs) {
		this.enabled = enabled;
		this.trigger = trigger;
		this.mode = mode;
		this.primary = primary;
		this.secondary = secondary;
		this.delayMs = delayMs;
		this.retryCount = retryCount;
		this.retryDelayMs = retryDelayMs;
		this.foregroundPolicy = foregroundPolicy;
		this.postLaunchPolicy = postLaunchPolicy;
		this.recoveryLimit = recoveryLimit;
		this.recoveryDelayMs = recoveryDelayMs;
	}

	public static StartupConfig defaults() {
		return new StartupConfig(false, StartupTrigger.COLD_BOOT, LaunchMode.SINGLE, null, null,
			Limits.STARTUP_DELAY_DEFAULT_MS, Limits.RETRY_COUNT_DEFAULT, Limits.RETRY_DELAY_DEFAULT_MS,
			ForegroundPolicy.ALWAYS, PostLaunchPolicy.NONE,
			Limits.RECOVERY_LIMIT_DEFAULT, Limits.RECOVERY_DELAY_DEFAULT_MS);
	}

	public boolean triggersOnBoot() {
		return trigger == StartupTrigger.COLD_BOOT || trigger == StartupTrigger.BOTH;
	}

	public boolean triggersOnAcc() {
		return trigger == StartupTrigger.ACC_ON || trigger == StartupTrigger.BOTH;
	}

	/** True when the configured mode has every target it needs. */
	public boolean hasTargets() {
		return primary != null && (mode == LaunchMode.SINGLE || secondary != null);
	}

	/** Identity of what and how it would be launched; any change cancels pending work. */
	public String launchFingerprint() {
		return mode + "|" + primary + "|" + (mode == LaunchMode.SPLIT ? String.valueOf(secondary) : "") +
			"|" + trigger + "|" + foregroundPolicy + "|" + delayMs + "|" + retryCount + "|" + retryDelayMs +
			"|" + postLaunchPolicy;
	}
}
