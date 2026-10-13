package com.pijulius.xposedteyes.core.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class MigrationTest {
	static final Migration.LauncherResolver RESOLVER = new Migration.LauncherResolver() {
		@Override
		public String launcherActivity(String packageName) {
			return "com.waze".equals(packageName) ? "com.waze.FreeMapAppActivity" : null;
		}
	};

	static Map<String, Object> legacy() {
		Map<String, Object> m = new HashMap<>();
		m.put(Keys.LEGACY_LAUNCH_ON_STARTUP, true);
		m.put(Keys.LEGACY_MUSIC_APP, "com.removed.app");
		m.put(Keys.LEGACY_NAVIGATION_APP, "com.waze");
		m.put(Keys.LEGACY_BLUETOOTH_APP, "com.syu.carlink");
		m.put("cameraWidth", "1500");
		return m;
	}

	@Test
	public void freshInstallOnlyStampsSchema() {
		Migration.Result r = Migration.migrate(new HashMap<String, Object>(), RESOLVER);
		assertEquals(1, r.writes.size());
		assertEquals(Keys.CURRENT_SCHEMA_VERSION, r.writes.get(Keys.SCHEMA_VERSION));
		assertFalse(r.needsBackup);
	}

	@Test
	public void legacyStartupAndBluetoothAreCarriedOver() {
		Migration.Result r = Migration.migrate(legacy(), RESOLVER);
		assertTrue(r.needsBackup);
		assertEquals(true, r.writes.get(Keys.STARTUP_ENABLED));
		assertEquals("SPLIT", r.writes.get(Keys.STARTUP_MODE));
		assertEquals("BOTH", r.writes.get(Keys.STARTUP_TRIGGER));
		// Unresolvable package stays as is (shown as "cannot launch"), never replaced.
		assertEquals("com.removed.app", r.writes.get(Keys.STARTUP_PRIMARY));
		assertEquals("com.waze/com.waze.FreeMapAppActivity", r.writes.get(Keys.STARTUP_SECONDARY));
		assertEquals(true, r.writes.get(Keys.BT_LAUNCH_ENABLED));
		assertEquals(true, r.writes.get(Keys.BT_LAUNCH_ON_DISCONNECT));
		assertTrue(r.notices.contains(Migration.NOTICE_STARTUP_MIGRATED));
		assertTrue(r.notices.contains(Migration.NOTICE_UNRESOLVED_PREFIX + "com.removed.app"));
		assertTrue(r.notices.contains(Migration.NOTICE_ROOT_ASSIST_OFF));
		assertFalse("never re-enables root without consent", r.writes.containsKey(Keys.ROOT_ACCESSIBILITY_ASSIST));
	}

	@Test
	public void tSt22_migrationIsIdempotent() {
		Map<String, Object> prefs = legacy();
		prefs.putAll(Migration.migrate(prefs, RESOLVER).writes);
		Map<String, Object> afterFirst = new HashMap<>(prefs);

		Migration.Result second = Migration.migrate(prefs, RESOLVER);
		assertTrue(second.writes.isEmpty());
		assertEquals(afterFirst, prefs);
		// Unrelated legacy values are untouched.
		assertEquals("1500", prefs.get("cameraWidth"));

		AppSettings s = AppSettings.read(prefs);
		assertFalse(s.derivedFromLegacy);
		assertTrue(s.startupActive());
	}

	@Test
	public void legacyUserWithBluetoothExplicitlyOff() {
		Map<String, Object> m = new HashMap<>();
		m.put(Keys.LEGACY_LAUNCH_ON_BLUETOOTH, false);
		m.put(Keys.LEGACY_BLUETOOTH_APP, "com.syu.carlink");
		Migration.Result r = Migration.migrate(m, RESOLVER);
		assertFalse(r.writes.containsKey(Keys.BT_LAUNCH_ENABLED));
		m.putAll(r.writes);
		assertFalse(AppSettings.read(m).bluetoothLaunchActive());
	}

	@Test
	public void tSt22_accessibilityListIsPreserved() {
		String ours = "com.pijulius.xposedteyes/com.pijulius.xposedteyes.LauncherService";
		assertEquals(ours, AccessibilityServices.withService(null, ours));
		assertEquals(ours, AccessibilityServices.withService("", ours));
		assertEquals("a.b/.S:" + ours, AccessibilityServices.withService("a.b/.S", ours));
		String both = "a.b/.S:com.pijulius.xposedteyes/.LauncherService";
		assertEquals(both, AccessibilityServices.withService(both, ours));
		assertTrue(AccessibilityServices.contains(both, ours));
		assertFalse(AccessibilityServices.contains("a.b/.S", ours));
	}
}
