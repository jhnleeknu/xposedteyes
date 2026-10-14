package com.pijulius.xposedteyes.startup;

import java.util.HashMap;
import java.util.Map;

/**
 * Small persistent key/value store for per-boot processing state, so that a
 * SystemUI restart does not repeat a launch. Kept local to the device: the
 * stored boot identifier is never exported or reported.
 */
public interface StateStore {
	String get(String key);
	void put(String key, String value);
	void clear();

	final class Memory implements StateStore {
		private final Map<String, String> values = new HashMap<>();

		@Override
		public String get(String key) {
			return values.get(key);
		}

		@Override
		public void put(String key, String value) {
			values.put(key, value);
		}

		@Override
		public void clear() {
			values.clear();
		}
	}
}
