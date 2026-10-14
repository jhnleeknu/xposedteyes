package com.pijulius.xposedteyes.settings;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Typed, validated view of the startup app and Bluetooth launch settings,
 * shared by the settings UI and the SystemUI hook. Missing, invalid or out of
 * range stored values fall back to the default.
 */
public final class StartupSettings {

	public enum Trigger { COLD_BOOT, ACC_RESUME, BOTH }
	public enum ForegroundPolicy { ALWAYS, HOME_ONLY }
	public enum ConflictPolicy { STARTUP_FIRST, BLUETOOTH_FIRST, LAST_EVENT }

	/** Inclusive integer range plus default, in the unit stored in prefs. */
	public static final class Range {
		public final int min, max, def;

		Range(int min, int max, int def) {
			this.min = min;
			this.max = max;
			this.def = def;
		}

		public boolean contains(int v) {
			return v >= min && v <= max;
		}
	}

	public static final Range DELAY_S = new Range(0, 60, 5);
	public static final int[] DELAY_PRESETS_S = { 0, 3, 5, 10, 15 };
	public static final Range RETRY_COUNT = new Range(0, 3, 1);
	public static final Range RETRY_DELAY_S = new Range(1, 30, 5);
	public static final Range BT_DELAY_S = new Range(0, 60, 3);

	public static final Map<String, Range> RANGES;
	static {
		Map<String, Range> m = new HashMap<>();
		m.put(Keys.STARTUP_DELAY_S, DELAY_S);
		m.put(Keys.STARTUP_RETRY_COUNT, RETRY_COUNT);
		m.put(Keys.STARTUP_RETRY_DELAY_S, RETRY_DELAY_S);
		m.put(Keys.BT_LAUNCH_DELAY_S, BT_DELAY_S);
		RANGES = Collections.unmodifiableMap(m);
	}

	public final boolean enabled;
	public final Trigger trigger;
	public final Target app;
	public final long delayMs;
	public final int retryCount;
	public final long retryDelayMs;
	public final ForegroundPolicy foregroundPolicy;

	public final boolean btEnabled;
	public final Target btTarget;
	public final long btDelayMs;
	public final ConflictPolicy conflictPolicy;

	private StartupSettings(PrefsReader p) {
		enabled = p.getBoolean(Keys.STARTUP_ENABLED, false);
		trigger = readEnum(p, Keys.STARTUP_TRIGGER, Trigger.class, Trigger.COLD_BOOT);
		app = Target.parse(p.getString(Keys.STARTUP_APP, ""));
		delayMs = readRange(p, Keys.STARTUP_DELAY_S, DELAY_S) * 1000L;
		retryCount = readRange(p, Keys.STARTUP_RETRY_COUNT, RETRY_COUNT);
		retryDelayMs = readRange(p, Keys.STARTUP_RETRY_DELAY_S, RETRY_DELAY_S) * 1000L;
		foregroundPolicy = readEnum(p, Keys.STARTUP_FOREGROUND_POLICY, ForegroundPolicy.class,
			ForegroundPolicy.ALWAYS);

		btEnabled = p.getBoolean(Keys.BT_LAUNCH_ENABLED, false);
		btTarget = Target.parse(p.getString(Keys.BT_LAUNCH_TARGET, ""));
		btDelayMs = readRange(p, Keys.BT_LAUNCH_DELAY_S, BT_DELAY_S) * 1000L;
		conflictPolicy = readEnum(p, Keys.BT_CONFLICT_POLICY, ConflictPolicy.class, ConflictPolicy.STARTUP_FIRST);
	}

	public static StartupSettings from(Map<String, ?> stored) {
		return new StartupSettings(new PrefsReader(stored));
	}

	public boolean triggersOnColdBoot() {
		return trigger == Trigger.COLD_BOOT || trigger == Trigger.BOTH;
	}

	public boolean triggersOnAcc() {
		return trigger == Trigger.ACC_RESUME || trigger == Trigger.BOTH;
	}

	/** A pending startup launch is cancelled when this changes. */
	public String startupFingerprint() {
		return enabled + "|" + trigger + "|" + app + "|" + delayMs + "|" + retryCount + "|" + retryDelayMs + "|" +
			foregroundPolicy;
	}

	public String bluetoothFingerprint() {
		return btEnabled + "|" + btTarget + "|" + btDelayMs + "|" + conflictPolicy + "|" + retryCount + "|" +
			retryDelayMs + "|" + foregroundPolicy;
	}

	private static int readRange(PrefsReader p, String key, Range range) {
		int v = p.getInt(key, range.def);
		return range.contains(v) ? v : range.def;
	}

	private static <E extends Enum<E>> E readEnum(PrefsReader p, String key, Class<E> type, E def) {
		try {
			return Enum.valueOf(type, p.getString(key, def.name()));
		} catch (IllegalArgumentException e) {
			return def;
		}
	}
}
