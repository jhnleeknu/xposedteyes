package com.pijulius.xposedteyes.settings;

import java.util.Map;

/**
 * Minimal read access to preference values, tolerant of the type differences
 * between ListPreference (strings) and custom preferences (ints).
 */
public final class PrefsReader {
	private final Map<String, ?> values;

	public PrefsReader(Map<String, ?> values) {
		this.values = values;
	}

	public boolean contains(String key) {
		return values != null && values.containsKey(key);
	}

	public Object raw(String key) {
		return values == null ? null : values.get(key);
	}

	public boolean getBoolean(String key, boolean def) {
		Object v = raw(key);
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

	/** Returns the integer value, or {@code def} when missing or not a number. */
	public int getInt(String key, int def) {
		Object v = raw(key);
		if (v instanceof Integer)
			return (Integer)v;
		if (v instanceof Long)
			return (int)(long)(Long)v;
		if (v instanceof String) {
			try {
				return Integer.parseInt(((String)v).trim());
			} catch (NumberFormatException e) {
				return def;
			}
		}
		return def;
	}

	public String getString(String key, String def) {
		Object v = raw(key);
		if (v instanceof String)
			return (String)v;
		return def;
	}
}
