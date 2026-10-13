package com.pijulius.xposedteyes.core.settings;

import java.util.Map;

/**
 * Lenient readers for SharedPreferences maps. Older preferences were stored as
 * strings by EditTextPreference/ListPreference, newer ones as ints/booleans, so
 * every reader accepts both representations and falls back to the default on
 * anything it cannot parse.
 */
public final class Values {
	private Values() {}

	public static String getString(Map<String, ?> map, String key, String def) {
		Object v = map.get(key);
		if (v == null)
			return def;
		return String.valueOf(v);
	}

	public static boolean getBoolean(Map<String, ?> map, String key, boolean def) {
		Object v = map.get(key);
		if (v instanceof Boolean)
			return (Boolean)v;
		if (v instanceof String) {
			if ("true".equalsIgnoreCase((String)v))
				return true;
			if ("false".equalsIgnoreCase((String)v))
				return false;
		}
		return def;
	}

	/** Returns null when the value is missing or not an integer. */
	public static Integer getIntOrNull(Map<String, ?> map, String key) {
		Object v = map.get(key);
		if (v instanceof Integer || v instanceof Long || v instanceof Short)
			return ((Number)v).intValue();
		if (v instanceof String) {
			try {
				return Integer.valueOf(((String)v).trim());
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}

	public static int getClampedInt(Map<String, ?> map, String key, int def, int min, int max) {
		Integer v = getIntOrNull(map, key);
		if (v == null)
			return def;
		return Math.max(min, Math.min(max, v));
	}

	public static <E extends Enum<E>> E getEnum(Map<String, ?> map, String key, Class<E> type, E def) {
		Object v = map.get(key);
		if (v == null)
			return def;
		try {
			return Enum.valueOf(type, String.valueOf(v));
		} catch (IllegalArgumentException e) {
			return def;
		}
	}
}
