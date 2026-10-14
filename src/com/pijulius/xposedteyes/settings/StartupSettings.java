package com.pijulius.xposedteyes.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed, validated view of the startup app and Bluetooth launch settings.
 *
 * The same parsing and range rules are used by the settings UI and by the
 * SystemUI hook so both sides agree on what a stored value means. Invalid or
 * out of range stored values fall back to the documented default and are
 * listed in {@link #invalidKeys}; they are never clamped silently.
 */
public final class StartupSettings {

	public enum Trigger { COLD_BOOT, ACC_RESUME, BOTH }
	public enum Mode { SINGLE, SPLIT }
	public enum ForegroundPolicy { ALWAYS, HOME_ONLY }
	public enum PostLaunchPolicy { NONE, RECOVER_CRASH, KEEP_ALIVE }
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
	public static final Range RECOVERY_LIMIT = new Range(1, 3, 1);
	public static final Range RECOVERY_DELAY_S = new Range(5, 60, 5);
	public static final Range BT_DELAY_S = new Range(0, 60, 3);

	public static final Map<String, Range> RANGES;
	static {
		Map<String, Range> m = new HashMap<>();
		m.put(Keys.STARTUP_DELAY_S, DELAY_S);
		m.put(Keys.STARTUP_RETRY_COUNT, RETRY_COUNT);
		m.put(Keys.STARTUP_RETRY_DELAY_S, RETRY_DELAY_S);
		m.put(Keys.STARTUP_RECOVERY_LIMIT, RECOVERY_LIMIT);
		m.put(Keys.STARTUP_RECOVERY_DELAY_S, RECOVERY_DELAY_S);
		m.put(Keys.BT_LAUNCH_DELAY_S, BT_DELAY_S);
		RANGES = Collections.unmodifiableMap(m);
	}

	/** Defaults for a new install or "restore defaults". */
	public static Map<String, Object> defaults() {
		Map<String, Object> d = new HashMap<>();
		d.put(Keys.STARTUP_ENABLED, false);
		d.put(Keys.STARTUP_TRIGGER, Trigger.COLD_BOOT.name());
		d.put(Keys.STARTUP_MODE, Mode.SINGLE.name());
		d.put(Keys.STARTUP_PRIMARY, "");
		d.put(Keys.STARTUP_SECONDARY, "");
		d.put(Keys.STARTUP_DELAY_S, DELAY_S.def);
		d.put(Keys.STARTUP_RETRY_COUNT, String.valueOf(RETRY_COUNT.def));
		d.put(Keys.STARTUP_RETRY_DELAY_S, RETRY_DELAY_S.def);
		d.put(Keys.STARTUP_FOREGROUND_POLICY, ForegroundPolicy.ALWAYS.name());
		d.put(Keys.STARTUP_POST_LAUNCH_POLICY, PostLaunchPolicy.NONE.name());
		d.put(Keys.STARTUP_RECOVERY_LIMIT, RECOVERY_LIMIT.def);
		d.put(Keys.STARTUP_RECOVERY_DELAY_S, RECOVERY_DELAY_S.def);
		d.put(Keys.A11Y_ROOT_AUTO_ENABLE, false);
		d.put(Keys.BT_LAUNCH_ENABLED, false);
		d.put(Keys.BT_LAUNCH_TARGET, "");
		d.put(Keys.BT_LAUNCH_DELAY_S, BT_DELAY_S.def);
		d.put(Keys.BT_CONFLICT_POLICY, ConflictPolicy.STARTUP_FIRST.name());
		d.put(Keys.BT_LAUNCH_ON_DISCONNECT, false);
		return d;
	}

	public final boolean newFeaturesDisabled;

	public final boolean enabled;
	public final Trigger trigger;
	public final Mode mode;
	public final Target primary;
	public final Target secondary;
	public final long delayMs;
	public final int retryCount;
	public final long retryDelayMs;
	public final ForegroundPolicy foregroundPolicy;
	public final PostLaunchPolicy postLaunchPolicy;
	public final int recoveryLimit;
	public final long recoveryDelayMs;
	public final boolean a11yRootAutoEnable;

	public final boolean btEnabled;
	public final Target btTarget;
	public final long btDelayMs;
	public final ConflictPolicy conflictPolicy;
	public final boolean btLaunchStartupOnDisconnect;

	public final List<String> invalidKeys;

	private StartupSettings(PrefsReader p) {
		List<String> invalid = new ArrayList<>();

		newFeaturesDisabled = p.getBoolean(Keys.NEW_FEATURES_DISABLED, false);

		enabled = p.getBoolean(Keys.STARTUP_ENABLED, false);
		trigger = readEnum(p, Keys.STARTUP_TRIGGER, Trigger.class, Trigger.COLD_BOOT, invalid);
		mode = readEnum(p, Keys.STARTUP_MODE, Mode.class, Mode.SINGLE, invalid);
		primary = readTarget(p, Keys.STARTUP_PRIMARY, invalid);
		secondary = readTarget(p, Keys.STARTUP_SECONDARY, invalid);
		delayMs = readRange(p, Keys.STARTUP_DELAY_S, DELAY_S, invalid) * 1000L;
		retryCount = readRange(p, Keys.STARTUP_RETRY_COUNT, RETRY_COUNT, invalid);
		retryDelayMs = readRange(p, Keys.STARTUP_RETRY_DELAY_S, RETRY_DELAY_S, invalid) * 1000L;
		foregroundPolicy = readEnum(p, Keys.STARTUP_FOREGROUND_POLICY, ForegroundPolicy.class,
			ForegroundPolicy.ALWAYS, invalid);
		postLaunchPolicy = readEnum(p, Keys.STARTUP_POST_LAUNCH_POLICY, PostLaunchPolicy.class,
			PostLaunchPolicy.NONE, invalid);
		recoveryLimit = readRange(p, Keys.STARTUP_RECOVERY_LIMIT, RECOVERY_LIMIT, invalid);
		recoveryDelayMs = readRange(p, Keys.STARTUP_RECOVERY_DELAY_S, RECOVERY_DELAY_S, invalid) * 1000L;
		a11yRootAutoEnable = p.getBoolean(Keys.A11Y_ROOT_AUTO_ENABLE, false);

		btEnabled = p.getBoolean(Keys.BT_LAUNCH_ENABLED, false);
		btTarget = readTarget(p, Keys.BT_LAUNCH_TARGET, invalid);
		btDelayMs = readRange(p, Keys.BT_LAUNCH_DELAY_S, BT_DELAY_S, invalid) * 1000L;
		conflictPolicy = readEnum(p, Keys.BT_CONFLICT_POLICY, ConflictPolicy.class,
			ConflictPolicy.STARTUP_FIRST, invalid);
		btLaunchStartupOnDisconnect = p.getBoolean(Keys.BT_LAUNCH_ON_DISCONNECT, false);

		invalidKeys = Collections.unmodifiableList(invalid);
	}

	/**
	 * Builds the effective settings from the raw stored values. Legacy (pre
	 * schema) values are migrated in memory first, so a hook process that
	 * starts before the settings app was opened after an update behaves the
	 * same as after the persisted migration.
	 */
	public static StartupSettings from(Map<String, ?> stored) {
		return new StartupSettings(new PrefsReader(SettingsMigration.effectiveValues(stored)));
	}

	public boolean startupActive() {
		return !newFeaturesDisabled && enabled;
	}

	public boolean bluetoothActive() {
		return !newFeaturesDisabled && btEnabled;
	}

	public boolean triggersOnColdBoot() {
		return trigger == Trigger.COLD_BOOT || trigger == Trigger.BOTH;
	}

	public boolean triggersOnAcc() {
		return trigger == Trigger.ACC_RESUME || trigger == Trigger.BOTH;
	}

	/**
	 * Identifies the startup behaviour. A pending startup session is cancelled
	 * when this changes, so new settings only apply from the next event.
	 */
	public String startupFingerprint() {
		return startupActive() + "|" + trigger + "|" + mode + "|" + primary + "|" +
			(mode == Mode.SPLIT ? String.valueOf(secondary) : "-") + "|" + delayMs + "|" +
			retryCount + "|" + retryDelayMs + "|" + foregroundPolicy + "|" + postLaunchPolicy + "|" +
			recoveryLimit + "|" + recoveryDelayMs;
	}

	public String bluetoothFingerprint() {
		return bluetoothActive() + "|" + btTarget + "|" + btDelayMs + "|" + conflictPolicy + "|" +
			retryCount + "|" + retryDelayMs + "|" + foregroundPolicy;
	}

	/** Validates a single value as the UI would store it; returns false when out of range. */
	public static boolean isValidValue(String key, Object value) {
		Range r = RANGES.get(key);
		if (r != null) {
			Integer v = toInt(value);
			return v != null && r.contains(v);
		}
		if (Keys.STARTUP_PRIMARY.equals(key) || Keys.STARTUP_SECONDARY.equals(key) ||
			Keys.BT_LAUNCH_TARGET.equals(key))
		{
			return value instanceof String && Target.isValid((String)value);
		}
		if (Keys.STARTUP_TRIGGER.equals(key))
			return isEnumValue(Trigger.class, value);
		if (Keys.STARTUP_MODE.equals(key))
			return isEnumValue(Mode.class, value);
		if (Keys.STARTUP_FOREGROUND_POLICY.equals(key))
			return isEnumValue(ForegroundPolicy.class, value);
		if (Keys.STARTUP_POST_LAUNCH_POLICY.equals(key))
			return isEnumValue(PostLaunchPolicy.class, value);
		if (Keys.BT_CONFLICT_POLICY.equals(key))
			return isEnumValue(ConflictPolicy.class, value);
		return true;
	}

	static Integer toInt(Object value) {
		if (value instanceof Integer)
			return (Integer)value;
		if (value instanceof String) {
			try {
				return Integer.parseInt(((String)value).trim());
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}

	private static <E extends Enum<E>> boolean isEnumValue(Class<E> type, Object value) {
		if (!(value instanceof String))
			return false;
		try {
			Enum.valueOf(type, (String)value);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static int readRange(PrefsReader p, String key, Range range, List<String> invalid) {
		if (!p.contains(key))
			return range.def;

		Integer v = toInt(p.raw(key));
		if (v == null || !range.contains(v)) {
			invalid.add(key);
			return range.def;
		}
		return v;
	}

	private static <E extends Enum<E>> E readEnum(PrefsReader p, String key, Class<E> type, E def,
		List<String> invalid)
	{
		if (!p.contains(key))
			return def;

		Object v = p.raw(key);
		if (!isEnumValue(type, v)) {
			invalid.add(key);
			return def;
		}
		return Enum.valueOf(type, (String)v);
	}

	private static Target readTarget(PrefsReader p, String key, List<String> invalid) {
		String v = p.getString(key, "");
		if (v.trim().isEmpty())
			return null;

		Target t = Target.parse(v);
		if (t == null)
			invalid.add(key);
		return t;
	}
}
