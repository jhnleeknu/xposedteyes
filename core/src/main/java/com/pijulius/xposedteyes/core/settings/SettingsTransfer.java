package com.pijulius.xposedteyes.core.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Whitelist and validation for settings export/import. Import never accepts a key
 * that is not listed here, and every value is type and range checked, so a backup
 * file cannot smuggle in shell commands, class names to load or out of range values.
 */
public final class SettingsTransfer {
	private SettingsTransfer() {}

	public static final String FORMAT = "xposedteyes-settings";

	enum Kind { BOOLEAN, INT, ENUM, TARGET, STRING_INT, STRING_FLOAT, PACKAGE, CHOICE }

	static final class Spec {
		final Kind kind;
		final int min;
		final int max;
		final Class<? extends Enum<?>> enumType;
		final String[] choices;

		Spec(Kind kind, int min, int max, Class<? extends Enum<?>> enumType, String[] choices) {
			this.kind = kind;
			this.min = min;
			this.max = max;
			this.enumType = enumType;
			this.choices = choices;
		}
	}

	private static final Map<String, Spec> SPECS = new LinkedHashMap<>();

	private static void bool(String key) { SPECS.put(key, new Spec(Kind.BOOLEAN, 0, 0, null, null)); }
	private static void integer(String key, int min, int max) { SPECS.put(key, new Spec(Kind.INT, min, max, null, null)); }
	private static void stringInt(String key, int min, int max) { SPECS.put(key, new Spec(Kind.STRING_INT, min, max, null, null)); }
	private static void enumeration(String key, Class<? extends Enum<?>> type) { SPECS.put(key, new Spec(Kind.ENUM, 0, 0, type, null)); }
	private static void target(String key) { SPECS.put(key, new Spec(Kind.TARGET, 0, 0, null, null)); }
	private static void pkg(String key) { SPECS.put(key, new Spec(Kind.PACKAGE, 0, 0, null, null)); }

	static {
		bool(Keys.KILL_SWITCH);
		bool(Keys.STARTUP_ENABLED);
		enumeration(Keys.STARTUP_TRIGGER, StartupTrigger.class);
		enumeration(Keys.STARTUP_MODE, LaunchMode.class);
		target(Keys.STARTUP_PRIMARY);
		target(Keys.STARTUP_SECONDARY);
		integer(Keys.STARTUP_DELAY_MS, Limits.STARTUP_DELAY_MIN_MS, Limits.STARTUP_DELAY_MAX_MS);
		integer(Keys.STARTUP_RETRY_COUNT, Limits.RETRY_COUNT_MIN, Limits.RETRY_COUNT_MAX);
		integer(Keys.STARTUP_RETRY_DELAY_MS, Limits.RETRY_DELAY_MIN_MS, Limits.RETRY_DELAY_MAX_MS);
		enumeration(Keys.STARTUP_FOREGROUND_POLICY, ForegroundPolicy.class);
		enumeration(Keys.STARTUP_POST_LAUNCH_POLICY, PostLaunchPolicy.class);
		integer(Keys.STARTUP_RECOVERY_LIMIT, Limits.RECOVERY_LIMIT_MIN, Limits.RECOVERY_LIMIT_MAX);
		integer(Keys.STARTUP_RECOVERY_DELAY_MS, Limits.RECOVERY_DELAY_MIN_MS, Limits.RECOVERY_DELAY_MAX_MS);
		bool(Keys.BT_LAUNCH_ENABLED);
		target(Keys.BT_LAUNCH_TARGET);
		integer(Keys.BT_LAUNCH_DELAY_MS, Limits.BT_DELAY_MIN_MS, Limits.BT_DELAY_MAX_MS);
		enumeration(Keys.BT_CONFLICT_POLICY, ConflictPolicy.class);
		bool(Keys.BT_LAUNCH_ON_DISCONNECT);
		bool(Keys.ROOT_ACCESSIBILITY_ASSIST);

		// Pre-existing feature settings (stored as strings by EditTextPreference).
		bool("resizableSplitScreen");
		bool("dontExitSplitScreen");
		SPECS.put("screenRatio", new Spec(Kind.STRING_FLOAT, 0, 1, null, null));
		bool("hideDivider");
		stringInt("dividerWidth", 0, 500);
		stringInt("dividerInsetPosition", -500, 500);
		bool("leftScreenPosition");
		bool("forceSystemUI");
		bool("useAdBlocking");
		stringInt("cameraWidth", 0, 10000);
		stringInt("cameraHeight", 0, 10000);
		stringInt("cameraX", -10000, 10000);
		stringInt("cameraY", -10000, 10000);
		bool("cameraWarning");
		bool("cameraEnhance");
		bool("cameraRound");
		bool("cameraKeepSystemUI");
		SPECS.put("carlinkResolution", new Spec(Kind.CHOICE, 0, 0, null, new String[] {
			"", "800x480", "1280x720", "1920x1080", "2560x1440", "3840x2160",
			"720x1280", "1080x1920", "1440x2560", "2160x3840"}));
		stringInt("carlinkResolutionWidthMargin", -2000, 2000);
		stringInt("carlinkResolutionHeightMargin", -2000, 2000);
		stringInt("carlinkDPI", 0, 1000);
		bool("carlinkKeepSystemUI");
		pkg(Keys.LEGACY_MUSIC_APP);
		pkg(Keys.LEGACY_NAVIGATION_APP);
		pkg(Keys.LEGACY_VIDEOS_APP);
		stringInt("miniDesktopDPI", 50, 1000);
	}

	public static boolean isTransferable(String key) {
		return SPECS.containsKey(key);
	}

	/** The subset of preferences that a backup file carries. */
	public static Map<String, Object> exportable(Map<String, ?> prefs) {
		Map<String, Object> out = new LinkedHashMap<>();
		for (String key : SPECS.keySet()) {
			Object v = prefs.get(key);
			if (v != null && validate(key, v) != null)
				out.put(key, v);
		}
		return out;
	}

	public static final class ImportResult {
		public final Map<String, Object> accepted;
		public final List<String> errors;

		ImportResult(Map<String, Object> accepted, List<String> errors) {
			this.accepted = Collections.unmodifiableMap(accepted);
			this.errors = Collections.unmodifiableList(errors);
		}

		/** True when the file was usable at all (header ok). */
		public boolean usable() {
			return !accepted.isEmpty() || errors.isEmpty();
		}
	}

	/**
	 * Validates a parsed backup. {@code format} and {@code schemaVersion} come from
	 * the file header; {@code values} is the settings object.
	 */
	public static ImportResult validateImport(String format, Integer schemaVersion, Map<String, ?> values) {
		Map<String, Object> accepted = new LinkedHashMap<>();
		List<String> errors = new ArrayList<>();

		if (!FORMAT.equals(format)) {
			errors.add("format");
			return new ImportResult(accepted, errors);
		}
		if (schemaVersion == null || schemaVersion < 1 || schemaVersion > Keys.CURRENT_SCHEMA_VERSION) {
			errors.add("schemaVersion");
			return new ImportResult(accepted, errors);
		}
		if (values == null) {
			errors.add("values");
			return new ImportResult(accepted, errors);
		}

		for (Map.Entry<String, ?> e : values.entrySet()) {
			Object normalized = SPECS.containsKey(e.getKey()) ? validate(e.getKey(), e.getValue()) : null;
			if (normalized == null)
				errors.add(e.getKey());
			else
				accepted.put(e.getKey(), normalized);
		}
		return new ImportResult(accepted, errors);
	}

	/** Returns the value normalized to its storage type, or null if invalid. */
	static Object validate(String key, Object value) {
		Spec spec = SPECS.get(key);
		if (spec == null || value == null)
			return null;

		switch (spec.kind) {
			case BOOLEAN:
				return value instanceof Boolean ? value : null;

			case INT: {
				if (!(value instanceof Number))
					return null;
				double d = ((Number)value).doubleValue();
				if (d != Math.rint(d) || d < spec.min || d > spec.max)
					return null;
				return (int)d;
			}

			case ENUM: {
				if (!(value instanceof String))
					return null;
				for (Enum<?> constant : spec.enumType.getEnumConstants()) {
					if (constant.name().equals(value))
						return value;
				}
				return null;
			}

			case TARGET: {
				if (!(value instanceof String))
					return null;
				if (((String)value).isEmpty())
					return "";
				LaunchTarget t = LaunchTarget.parse((String)value);
				return t == null ? null : t.flatten();
			}

			case PACKAGE: {
				if (!(value instanceof String))
					return null;
				String s = ((String)value).trim();
				return s.isEmpty() || LaunchTarget.isValidName(s) ? s : null;
			}

			case STRING_INT: {
				if (!(value instanceof String))
					return null;
				try {
					int i = Integer.parseInt(((String)value).trim());
					return i < spec.min || i > spec.max ? null : String.valueOf(i);
				} catch (NumberFormatException ex) {
					return null;
				}
			}

			case STRING_FLOAT: {
				if (!(value instanceof String))
					return null;
				try {
					float f = Float.parseFloat(((String)value).trim());
					return Float.isNaN(f) || f < spec.min || f > spec.max ? null : ((String)value).trim();
				} catch (NumberFormatException ex) {
					return null;
				}
			}

			case CHOICE: {
				if (!(value instanceof String))
					return null;
				for (String c : spec.choices) {
					if (c.equals(value))
						return value;
				}
				return null;
			}
		}
		return null;
	}
}
