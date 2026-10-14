package com.pijulius.xposedteyes.settings;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Settings export / import. Only whitelisted keys with validated values are
 * accepted; nothing in an imported file is ever executed or used as a class
 * name. A file with any invalid known value is rejected as a whole.
 */
public final class SettingsCodec {
	private SettingsCodec() {}

	public static final String FORMAT = "xposedteyes-settings";

	enum Type { BOOLEAN, INTEGER, STRING }

	interface Check {
		boolean ok(Object value);
	}

	static final class Entry {
		final Type type;
		final Check check;

		Entry(Type type, Check check) {
			this.type = type;
			this.check = check;
		}
	}

	private static final Pattern INT_STRING = Pattern.compile("-?\\d{1,6}");
	private static final Pattern RESOLUTION = Pattern.compile("(\\d{3,4}x\\d{3,4})?");

	static final Map<String, Entry> SCHEMA;
	static {
		Map<String, Entry> m = new LinkedHashMap<>();
		Check any = new Check() {
			@Override
			public boolean ok(Object v) {
				return true;
			}
		};
		final Check intString = new Check() {
			@Override
			public boolean ok(Object v) {
				return INT_STRING.matcher((String)v).matches();
			}
		};
		Check ratio = new Check() {
			@Override
			public boolean ok(Object v) {
				try {
					float f = Float.parseFloat((String)v);
					return f > 0f && f < 1f;
				} catch (NumberFormatException e) {
					return false;
				}
			}
		};
		Check packageName = new Check() {
			@Override
			public boolean ok(Object v) {
				String s = (String)v;
				return s.isEmpty() || (Target.parse(s) != null && Target.parse(s).isPackageOnly());
			}
		};

		for (String k : new String[] { Keys.NEW_FEATURES_DISABLED, Keys.STARTUP_ENABLED, Keys.A11Y_ROOT_AUTO_ENABLE,
			Keys.BT_LAUNCH_ENABLED, Keys.BT_LAUNCH_ON_DISCONNECT, Keys.DIAG_CARLINK_VIDEO, Keys.DIAG_AUDIO_PATH,
			"resizableSplitScreen", "dontExitSplitScreen", "hideDivider", "leftScreenPosition", "forceSystemUI",
			"useAdBlocking", "cameraWarning", "cameraEnhance", "cameraRound", "cameraKeepSystemUI",
			"carlinkKeepSystemUI", Keys.LEGACY_LAUNCH_ON_STARTUP, Keys.LEGACY_LAUNCH_ON_BLUETOOTH })
		{
			m.put(k, new Entry(Type.BOOLEAN, any));
		}

		for (final String k : new String[] { Keys.STARTUP_DELAY_S, Keys.STARTUP_RETRY_DELAY_S,
			Keys.STARTUP_RECOVERY_LIMIT, Keys.STARTUP_RECOVERY_DELAY_S, Keys.BT_LAUNCH_DELAY_S })
		{
			m.put(k, new Entry(Type.INTEGER, new Check() {
				@Override
				public boolean ok(Object v) {
					return StartupSettings.isValidValue(k, v);
				}
			}));
		}

		for (final String k : new String[] { Keys.STARTUP_RETRY_COUNT, Keys.STARTUP_TRIGGER, Keys.STARTUP_MODE,
			Keys.STARTUP_PRIMARY, Keys.STARTUP_SECONDARY, Keys.STARTUP_FOREGROUND_POLICY,
			Keys.STARTUP_POST_LAUNCH_POLICY, Keys.BT_LAUNCH_TARGET, Keys.BT_CONFLICT_POLICY })
		{
			m.put(k, new Entry(Type.STRING, new Check() {
				@Override
				public boolean ok(Object v) {
					return StartupSettings.isValidValue(k, v);
				}
			}));
		}

		for (String k : new String[] { "dividerWidth", "dividerInsetPosition", "cameraWidth", "cameraHeight",
			"cameraX", "cameraY", "carlinkResolutionWidthMargin", "carlinkResolutionHeightMargin", "carlinkDPI",
			"miniDesktopDPI" })
		{
			m.put(k, new Entry(Type.STRING, intString));
		}

		m.put("screenRatio", new Entry(Type.STRING, ratio));
		m.put("carlinkResolution", new Entry(Type.STRING, new Check() {
			@Override
			public boolean ok(Object v) {
				return RESOLUTION.matcher((String)v).matches();
			}
		}));
		for (String k : new String[] { Keys.LEGACY_MUSIC_APP, Keys.LEGACY_NAVIGATION_APP, Keys.LEGACY_VIDEOS_APP,
			Keys.LEGACY_BLUETOOTH_APP })
		{
			m.put(k, new Entry(Type.STRING, packageName));
		}

		SCHEMA = Collections.unmodifiableMap(m);
	}

	public static boolean isKnownKey(String key) {
		return SCHEMA.containsKey(key);
	}

	/** Serializes the whitelisted keys present in {@code stored}. */
	public static String export(Map<String, ?> stored) {
		try {
			JSONObject values = new JSONObject();
			for (Map.Entry<String, ?> e : new TreeMap<String, Object>(stored).entrySet())
				if (SCHEMA.containsKey(e.getKey()) && e.getValue() != null)
					values.put(e.getKey(), e.getValue());

			JSONObject root = new JSONObject();
			root.put("format", FORMAT);
			root.put("schemaVersion", Keys.CURRENT_SCHEMA);
			root.put("values", values);
			return root.toString(2);
		} catch (JSONException e) {
			throw new IllegalStateException(e);
		}
	}

	public static final class ImportResult {
		public final Map<String, Object> values;
		public final int skippedUnknown;
		public final String error;

		ImportResult(Map<String, Object> values, int skippedUnknown, String error) {
			this.values = values;
			this.skippedUnknown = skippedUnknown;
			this.error = error;
		}

		public boolean ok() {
			return error == null;
		}
	}

	private static ImportResult fail(String error) {
		return new ImportResult(Collections.<String, Object>emptyMap(), 0, error);
	}

	/** Parses and validates an exported file. Never throws. */
	public static ImportResult parse(String text) {
		if (text == null || text.length() > 256 * 1024)
			return fail("size");

		try {
			JSONObject root = new JSONObject(text);
			if (!FORMAT.equals(root.optString("format")))
				return fail("format");
			if (root.optInt("schemaVersion", -1) != Keys.CURRENT_SCHEMA)
				return fail("schemaVersion");

			JSONObject values = root.optJSONObject("values");
			if (values == null)
				return fail("values");

			Map<String, Object> out = new LinkedHashMap<>();
			int skipped = 0;

			Iterator<String> keys = values.keys();
			while (keys.hasNext()) {
				String key = keys.next();
				Entry entry = SCHEMA.get(key);
				if (entry == null) {
					skipped++;
					continue;
				}

				Object v = values.get(key);
				Object typed = coerce(entry.type, v);
				if (typed == null || !entry.check.ok(typed))
					return fail(key);
				out.put(key, typed);
			}

			out.put(Keys.SCHEMA_VERSION, Keys.CURRENT_SCHEMA);
			return new ImportResult(out, skipped, null);
		} catch (JSONException | RuntimeException e) {
			return fail("json");
		}
	}

	private static Object coerce(Type type, Object v) {
		switch (type) {
		case BOOLEAN:
			return v instanceof Boolean ? v : null;
		case INTEGER:
			if (v instanceof Integer)
				return v;
			if (v instanceof Long && (Long)v >= Integer.MIN_VALUE && (Long)v <= Integer.MAX_VALUE)
				return (int)(long)(Long)v;
			return null;
		case STRING:
			return v instanceof String && ((String)v).length() <= 512 ? v : null;
		default:
			return null;
		}
	}
}
