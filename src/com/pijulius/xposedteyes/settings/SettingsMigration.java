package com.pijulius.xposedteyes.settings;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts the 3.0 auto launch settings (launchOnStartup, launchOnBluetooth
 * and the package name fields) to the startup app manager settings.
 *
 * Legacy behaviour that is preserved:
 * - launchOnStartup launched the Music + Navigation apps in split screen on
 *   every ACC ON (cold boot and wake up) after ~1 s, and only when Bluetooth
 *   was not connected.
 * - launchOnBluetooth (hook default: true when never saved) launched the
 *   Bluetooth app 3 s after connecting and, when launchOnStartup was on, the
 *   split screen again 3 s after disconnecting.
 *
 * Legacy keys are never removed: the manual split screen shortcuts still use
 * the package name fields.
 */
public final class SettingsMigration {
	private SettingsMigration() {}

	public static final String NOTICE_STARTUP = "startup";
	public static final String NOTICE_STARTUP_INVALID = "startup_invalid";
	public static final String NOTICE_BLUETOOTH = "bluetooth";

	public static boolean needsMigration(Map<String, ?> stored) {
		return !new PrefsReader(stored).contains(Keys.SCHEMA_VERSION);
	}

	/**
	 * Values to write to bring {@code stored} to the current schema. Empty
	 * when already migrated, so running it twice is a no-op. Never overwrites
	 * a key that is already present.
	 */
	public static Map<String, Object> changes(Map<String, ?> stored) {
		Map<String, Object> out = new LinkedHashMap<>();
		if (!needsMigration(stored))
			return out;

		PrefsReader p = new PrefsReader(stored);
		StringBuilder notice = new StringBuilder();

		boolean legacyStartup = p.getBoolean(Keys.LEGACY_LAUNCH_ON_STARTUP, false);
		boolean startupMigrated = false;

		if (legacyStartup) {
			Target music = Target.parse(p.getString(Keys.LEGACY_MUSIC_APP, ""));
			Target nav = Target.parse(p.getString(Keys.LEGACY_NAVIGATION_APP, ""));

			if (music != null && nav != null) {
				putIfAbsent(out, p, Keys.STARTUP_ENABLED, true);
				putIfAbsent(out, p, Keys.STARTUP_TRIGGER, StartupSettings.Trigger.ACC_RESUME.name());
				putIfAbsent(out, p, Keys.STARTUP_MODE, StartupSettings.Mode.SPLIT.name());
				putIfAbsent(out, p, Keys.STARTUP_PRIMARY, music.flatten());
				putIfAbsent(out, p, Keys.STARTUP_SECONDARY, nav.flatten());
				putIfAbsent(out, p, Keys.STARTUP_DELAY_S, 1);
				putIfAbsent(out, p, Keys.A11Y_ROOT_AUTO_ENABLE, true);
				startupMigrated = true;
				append(notice, NOTICE_STARTUP);
			} else {
				// Keep the new manager off; the legacy values stay stored.
				append(notice, NOTICE_STARTUP_INVALID);
			}
		}

		// The 3.0 hook read launchOnBluetooth with a default of true.
		boolean legacyBluetooth = p.getBoolean(Keys.LEGACY_LAUNCH_ON_BLUETOOTH, true);
		Target btApp = Target.parse(p.getString(Keys.LEGACY_BLUETOOTH_APP, ""));

		if (legacyBluetooth && btApp != null) {
			putIfAbsent(out, p, Keys.BT_LAUNCH_ENABLED, true);
			putIfAbsent(out, p, Keys.BT_LAUNCH_TARGET, btApp.flatten());
			putIfAbsent(out, p, Keys.BT_LAUNCH_DELAY_S, 3);
			putIfAbsent(out, p, Keys.BT_LAUNCH_ON_DISCONNECT, startupMigrated);
			if (startupMigrated) {
				// Legacy startup did not launch while Bluetooth was connected.
				putIfAbsent(out, p, Keys.BT_CONFLICT_POLICY,
					StartupSettings.ConflictPolicy.BLUETOOTH_FIRST.name());
			}
			append(notice, NOTICE_BLUETOOTH);
		}

		if (notice.length() > 0)
			out.put(Keys.MIGRATION_NOTICE, notice.toString());

		out.put(Keys.SCHEMA_VERSION, Keys.CURRENT_SCHEMA);
		return out;
	}

	/** Stored values with the migration applied in memory (stored values win). */
	public static Map<String, ?> effectiveValues(Map<String, ?> stored) {
		if (stored == null)
			return new HashMap<String, Object>();
		if (!needsMigration(stored))
			return stored;

		Map<String, Object> merged = new HashMap<>(stored);
		merged.putAll(changes(stored));
		return merged;
	}

	private static void putIfAbsent(Map<String, Object> out, PrefsReader p, String key, Object value) {
		if (!p.contains(key))
			out.put(key, value);
	}

	private static void append(StringBuilder sb, String s) {
		if (sb.length() > 0)
			sb.append(',');
		sb.append(s);
	}
}
