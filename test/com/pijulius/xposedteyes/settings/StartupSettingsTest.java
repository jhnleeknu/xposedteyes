package com.pijulius.xposedteyes.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class StartupSettingsTest {

	private static Map<String, Object> migrated() {
		Map<String, Object> m = new HashMap<>();
		m.put(Keys.SCHEMA_VERSION, Keys.CURRENT_SCHEMA);
		return m;
	}

	/** T-ST-17: out of range values fall back to the default and are reported. */
	@Test
	public void outOfRangeValues_useDefaultAndAreListed() {
		Map<String, Object> m = migrated();
		m.put(Keys.STARTUP_DELAY_S, 61);
		m.put(Keys.STARTUP_RETRY_COUNT, "9");
		m.put(Keys.STARTUP_RETRY_DELAY_S, 0);
		m.put(Keys.STARTUP_TRIGGER, "SOMETIMES");
		m.put(Keys.STARTUP_PRIMARY, "not a component");
		m.put(Keys.BT_LAUNCH_DELAY_S, "abc");

		StartupSettings s = StartupSettings.from(m);
		assertEquals(5000, s.delayMs);
		assertEquals(1, s.retryCount);
		assertEquals(5000, s.retryDelayMs);
		assertEquals(StartupSettings.Trigger.COLD_BOOT, s.trigger);
		assertNull(s.primary);
		assertEquals(3000, s.btDelayMs);
		assertEquals(6, s.invalidKeys.size());
	}

	@Test
	public void boundaries() {
		assertTrue(StartupSettings.isValidValue(Keys.STARTUP_DELAY_S, 0));
		assertTrue(StartupSettings.isValidValue(Keys.STARTUP_DELAY_S, "60"));
		assertFalse(StartupSettings.isValidValue(Keys.STARTUP_DELAY_S, -1));
		assertFalse(StartupSettings.isValidValue(Keys.STARTUP_DELAY_S, 61));
		assertTrue(StartupSettings.isValidValue(Keys.STARTUP_RETRY_DELAY_S, 1));
		assertTrue(StartupSettings.isValidValue(Keys.STARTUP_RETRY_DELAY_S, 30));
		assertFalse(StartupSettings.isValidValue(Keys.STARTUP_RETRY_DELAY_S, 31));
		assertFalse(StartupSettings.isValidValue(Keys.STARTUP_RECOVERY_DELAY_S, 4));
		assertFalse(StartupSettings.isValidValue(Keys.STARTUP_RECOVERY_LIMIT, 4));
		assertTrue(StartupSettings.isValidValue(Keys.STARTUP_MODE, "SPLIT"));
		assertFalse(StartupSettings.isValidValue(Keys.STARTUP_MODE, "split"));
		assertTrue(StartupSettings.isValidValue(Keys.STARTUP_PRIMARY, ""));
		assertFalse(StartupSettings.isValidValue(Keys.STARTUP_PRIMARY, "a b/c"));
	}

	@Test
	public void killSwitchDisablesBothFeatures() {
		Map<String, Object> m = migrated();
		m.put(Keys.STARTUP_ENABLED, true);
		m.put(Keys.BT_LAUNCH_ENABLED, true);
		m.put(Keys.NEW_FEATURES_DISABLED, true);
		StartupSettings s = StartupSettings.from(m);
		assertFalse(s.startupActive());
		assertFalse(s.bluetoothActive());
	}

	@Test
	public void fingerprintIgnoresSecondaryInSingleMode() {
		Map<String, Object> m = migrated();
		m.put(Keys.STARTUP_PRIMARY, "a.b/a.b.C");
		String fp = StartupSettings.from(m).startupFingerprint();
		m.put(Keys.STARTUP_SECONDARY, "c.d/c.d.E");
		assertEquals(fp, StartupSettings.from(m).startupFingerprint());
		m.put(Keys.STARTUP_MODE, "SPLIT");
		assertNotEquals(fp, StartupSettings.from(m).startupFingerprint());
	}

	@Test
	public void targetParsing() {
		assertEquals("com.a/com.a.Main", Target.parse("com.a/.Main").flatten());
		assertTrue(Target.parse("com.a").isPackageOnly());
		assertNull(Target.parse("com.a/"));
		assertNull(Target.parse("/com.a.Main"));
		assertNull(Target.parse("noDots"));
		assertNull(Target.parse("com.a/com.a.Main;rm -rf"));
		assertEquals(Target.parse("com.a/com.a.Main"), Target.parse(" com.a/.Main "));
	}
}
