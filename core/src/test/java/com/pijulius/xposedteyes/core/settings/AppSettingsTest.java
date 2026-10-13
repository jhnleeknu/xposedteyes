package com.pijulius.xposedteyes.core.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class AppSettingsTest {
	@Test
	public void defaultsMatchSpec() {
		AppSettings s = AppSettings.read(new HashMap<String, Object>());
		assertFalse(s.startup.enabled);
		assertEquals(StartupTrigger.COLD_BOOT, s.startup.trigger);
		assertEquals(LaunchMode.SINGLE, s.startup.mode);
		assertNull(s.startup.primary);
		assertNull(s.startup.secondary);
		assertEquals(5_000, s.startup.delayMs);
		assertEquals(1, s.startup.retryCount);
		assertEquals(5_000, s.startup.retryDelayMs);
		assertEquals(PostLaunchPolicy.NONE, s.startup.postLaunchPolicy);
		assertEquals(1, s.startup.recoveryLimit);
		assertEquals(5_000, s.startup.recoveryDelayMs);
		assertEquals(ForegroundPolicy.ALWAYS, s.startup.foregroundPolicy);
		assertFalse(s.bluetooth.enabled);
		assertEquals(3_000, s.bluetooth.delayMs);
		assertEquals(ConflictPolicy.STARTUP_FIRST, s.bluetooth.conflictPolicy);
		assertFalse(s.bluetooth.launchOnDisconnect);
		assertFalse(s.rootAccessibilityAssist);
		assertFalse(s.killSwitch);
	}

	@Test
	public void tSt16_storedValuesRoundTripInBothRepresentations() {
		Map<String, Object> m = new HashMap<>();
		m.put(Keys.SCHEMA_VERSION, 1);
		m.put(Keys.STARTUP_ENABLED, true);
		m.put(Keys.STARTUP_TRIGGER, "BOTH");
		m.put(Keys.STARTUP_MODE, "SPLIT");
		m.put(Keys.STARTUP_PRIMARY, "a.b/a.b.C");
		m.put(Keys.STARTUP_SECONDARY, "x.y/.Main");
		m.put(Keys.STARTUP_DELAY_MS, 15_000);
		m.put(Keys.STARTUP_RETRY_COUNT, "3"); // ListPreference stores strings
		m.put(Keys.BT_CONFLICT_POLICY, "LAST_EVENT");
		AppSettings s = AppSettings.read(m);
		assertTrue(s.startupActive());
		assertEquals(StartupTrigger.BOTH, s.startup.trigger);
		assertEquals("a.b/a.b.C", s.startup.primary.flatten());
		assertEquals("x.y/x.y.Main", s.startup.secondary.flatten());
		assertEquals(15_000, s.startup.delayMs);
		assertEquals(3, s.startup.retryCount);
		assertEquals(ConflictPolicy.LAST_EVENT, s.bluetooth.conflictPolicy);
	}

	@Test
	public void tSt17_outOfRangeAndGarbageFallBackSafely() {
		Map<String, Object> m = new HashMap<>();
		m.put(Keys.SCHEMA_VERSION, 1);
		m.put(Keys.STARTUP_DELAY_MS, 600_000);
		m.put(Keys.STARTUP_RETRY_COUNT, -4);
		m.put(Keys.STARTUP_RETRY_DELAY_MS, "abc");
		m.put(Keys.STARTUP_TRIGGER, "SOMETIMES");
		m.put(Keys.STARTUP_PRIMARY, "rm -rf /;");
		m.put(Keys.STARTUP_ENABLED, "maybe");
		AppSettings s = AppSettings.read(m);
		assertEquals(60_000, s.startup.delayMs);
		assertEquals(0, s.startup.retryCount);
		assertEquals(5_000, s.startup.retryDelayMs);
		assertEquals(StartupTrigger.COLD_BOOT, s.startup.trigger);
		assertNull(s.startup.primary);
		assertFalse(s.startup.enabled);
	}

	@Test
	public void killSwitchOverridesEverything() {
		Map<String, Object> m = new HashMap<>();
		m.put(Keys.SCHEMA_VERSION, 1);
		m.put(Keys.STARTUP_ENABLED, true);
		m.put(Keys.BT_LAUNCH_ENABLED, true);
		m.put(Keys.BT_LAUNCH_ON_DISCONNECT, true);
		m.put(Keys.KILL_SWITCH, true);
		AppSettings s = AppSettings.read(m);
		assertFalse(s.startupActive());
		assertFalse(s.bluetoothLaunchActive());
		assertFalse(s.bluetoothDisconnectActive());
	}

	@Test
	public void legacyReadReproducesPreviousBehaviour() {
		Map<String, Object> m = new HashMap<>();
		m.put(Keys.LEGACY_LAUNCH_ON_STARTUP, true);
		m.put(Keys.LEGACY_MUSIC_APP, "com.audials");
		m.put(Keys.LEGACY_NAVIGATION_APP, "com.waze");
		m.put(Keys.LEGACY_BLUETOOTH_APP, "com.syu.carlink");
		AppSettings s = AppSettings.read(m);
		assertTrue(s.derivedFromLegacy);
		assertTrue(s.startupActive());
		assertEquals(LaunchMode.SPLIT, s.startup.mode);
		assertEquals(StartupTrigger.BOTH, s.startup.trigger);
		assertEquals(1_000, s.startup.delayMs);
		assertTrue(s.startup.primary.isPackageOnly());
		// launchOnBluetooth was never saved: the old hook defaulted to on.
		assertTrue(s.bluetoothLaunchActive());
		assertTrue(s.bluetoothDisconnectActive());
		assertEquals(ConflictPolicy.BLUETOOTH_FIRST, s.bluetooth.conflictPolicy);
	}

	@Test
	public void launchTargetParsing() {
		assertNull(LaunchTarget.parse(""));
		assertNull(LaunchTarget.parse("a b"));
		assertNull(LaunchTarget.parse("a/"));
		assertNull(LaunchTarget.parse("/a.B"));
		assertNull(LaunchTarget.parse("a..b"));
		assertEquals("com.waze", LaunchTarget.parse(" com.waze ").flatten());
		assertTrue(LaunchTarget.parse("com.waze").isPackageOnly());
		assertEquals("a.b/a.b.Alias$1", LaunchTarget.parse("a.b/.Alias$1").flatten());
	}
}
