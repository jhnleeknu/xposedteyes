package com.pijulius.xposedteyes.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class SettingsMigrationTest {

	@Test
	public void newInstall_defaultsOnly() {
		Map<String, Object> stored = new HashMap<>();
		Map<String, Object> changes = SettingsMigration.changes(stored);
		assertEquals(Keys.CURRENT_SCHEMA, changes.get(Keys.SCHEMA_VERSION));
		assertFalse(changes.containsKey(Keys.STARTUP_ENABLED));
		assertFalse(changes.containsKey(Keys.MIGRATION_NOTICE));

		StartupSettings s = StartupSettings.from(stored);
		assertFalse(s.enabled);
		assertEquals(StartupSettings.Trigger.COLD_BOOT, s.trigger);
		assertEquals(StartupSettings.Mode.SINGLE, s.mode);
		assertNull(s.primary);
		assertEquals(5000, s.delayMs);
		assertEquals(1, s.retryCount);
		assertEquals(5000, s.retryDelayMs);
		assertEquals(StartupSettings.ForegroundPolicy.ALWAYS, s.foregroundPolicy);
		assertEquals(StartupSettings.PostLaunchPolicy.NONE, s.postLaunchPolicy);
		assertFalse(s.btEnabled);
		assertEquals(3000, s.btDelayMs);
		assertEquals(StartupSettings.ConflictPolicy.STARTUP_FIRST, s.conflictPolicy);
		assertFalse(s.btLaunchStartupOnDisconnect);
	}

	@Test
	public void legacyStartupAndBluetooth_preserved() {
		Map<String, Object> stored = new HashMap<>();
		stored.put(Keys.LEGACY_LAUNCH_ON_STARTUP, true);
		stored.put(Keys.LEGACY_LAUNCH_ON_BLUETOOTH, true);
		stored.put(Keys.LEGACY_MUSIC_APP, "com.audials");
		stored.put(Keys.LEGACY_NAVIGATION_APP, "com.waze");
		stored.put(Keys.LEGACY_BLUETOOTH_APP, "com.syu.carlink");

		StartupSettings s = StartupSettings.from(stored);
		assertTrue(s.enabled);
		assertEquals(StartupSettings.Trigger.ACC_RESUME, s.trigger);
		assertEquals(StartupSettings.Mode.SPLIT, s.mode);
		assertEquals("com.audials", s.primary.flatten());
		assertEquals("com.waze", s.secondary.flatten());
		assertEquals(1000, s.delayMs);
		assertTrue(s.a11yRootAutoEnable);
		assertTrue(s.btEnabled);
		assertEquals("com.syu.carlink", s.btTarget.flatten());
		assertTrue(s.btLaunchStartupOnDisconnect);
		assertEquals(StartupSettings.ConflictPolicy.BLUETOOTH_FIRST, s.conflictPolicy);

		Map<String, Object> changes = SettingsMigration.changes(stored);
		assertEquals("startup,bluetooth", changes.get(Keys.MIGRATION_NOTICE));
	}

	@Test
	public void legacyBluetoothDefaultTrueWhenNeverSaved() {
		Map<String, Object> stored = new HashMap<>();
		stored.put(Keys.LEGACY_BLUETOOTH_APP, "com.syu.carlink");
		StartupSettings s = StartupSettings.from(stored);
		assertTrue(s.btEnabled);
		assertFalse(s.btLaunchStartupOnDisconnect);
		assertEquals(StartupSettings.ConflictPolicy.STARTUP_FIRST, s.conflictPolicy);
	}

	@Test
	public void legacyStartupWithInvalidApps_keptOffWithNotice() {
		Map<String, Object> stored = new HashMap<>();
		stored.put(Keys.LEGACY_LAUNCH_ON_STARTUP, true);
		stored.put(Keys.LEGACY_MUSIC_APP, "");
		stored.put(Keys.LEGACY_NAVIGATION_APP, "com.waze");
		Map<String, Object> changes = SettingsMigration.changes(stored);
		assertFalse(changes.containsKey(Keys.STARTUP_ENABLED));
		assertEquals(SettingsMigration.NOTICE_STARTUP_INVALID, changes.get(Keys.MIGRATION_NOTICE));
	}

	/** T-ST-22 */
	@Test
	public void migrationIsIdempotentAndKeepsLegacyAndUserValues() {
		Map<String, Object> stored = new HashMap<>();
		stored.put(Keys.LEGACY_LAUNCH_ON_STARTUP, true);
		stored.put(Keys.LEGACY_MUSIC_APP, "com.audials");
		stored.put(Keys.LEGACY_NAVIGATION_APP, "com.waze");
		stored.put(Keys.STARTUP_DELAY_S, 10); // already chosen by the user

		Map<String, Object> first = SettingsMigration.changes(stored);
		assertFalse(first.containsKey(Keys.STARTUP_DELAY_S));
		stored.putAll(first);
		Map<String, Object> snapshot = new HashMap<>(stored);

		Map<String, Object> second = SettingsMigration.changes(stored);
		assertTrue(second.isEmpty());
		assertEquals(snapshot, stored);
		assertEquals("com.audials", stored.get(Keys.LEGACY_MUSIC_APP));
		assertEquals(10, StartupSettings.from(stored).delayMs / 1000);
	}

	@Test
	public void afterMigration_userCanDisable() {
		Map<String, Object> stored = new HashMap<>();
		stored.put(Keys.LEGACY_LAUNCH_ON_STARTUP, true);
		stored.put(Keys.LEGACY_MUSIC_APP, "com.audials");
		stored.put(Keys.LEGACY_NAVIGATION_APP, "com.waze");
		stored.putAll(SettingsMigration.changes(stored));
		stored.put(Keys.STARTUP_ENABLED, false);
		assertFalse(StartupSettings.from(stored).enabled);
	}
}
