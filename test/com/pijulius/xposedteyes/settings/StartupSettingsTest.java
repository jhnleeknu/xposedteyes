package com.pijulius.xposedteyes.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class StartupSettingsTest {

	@Test
	public void defaults() {
		StartupSettings s = StartupSettings.from(new HashMap<String, Object>());
		assertFalse(s.enabled);
		assertEquals(StartupSettings.Trigger.COLD_BOOT, s.trigger);
		assertNull(s.app);
		assertEquals(5000, s.delayMs);
		assertEquals(1, s.retryCount);
		assertEquals(5000, s.retryDelayMs);
		assertEquals(StartupSettings.ForegroundPolicy.ALWAYS, s.foregroundPolicy);
		assertFalse(s.btEnabled);
		assertEquals(3000, s.btDelayMs);
		assertEquals(StartupSettings.ConflictPolicy.STARTUP_FIRST, s.conflictPolicy);
	}

	/** T-ST-17: invalid stored values fall back to the default. */
	@Test
	public void invalidValues_useDefault() {
		Map<String, Object> m = new HashMap<>();
		m.put(Keys.STARTUP_DELAY_S, 61);
		m.put(Keys.STARTUP_RETRY_COUNT, "9");
		m.put(Keys.STARTUP_RETRY_DELAY_S, 0);
		m.put(Keys.STARTUP_TRIGGER, "SOMETIMES");
		m.put(Keys.STARTUP_APP, "not a component");
		m.put(Keys.BT_LAUNCH_DELAY_S, "abc");

		StartupSettings s = StartupSettings.from(m);
		assertEquals(5000, s.delayMs);
		assertEquals(1, s.retryCount);
		assertEquals(5000, s.retryDelayMs);
		assertEquals(StartupSettings.Trigger.COLD_BOOT, s.trigger);
		assertNull(s.app);
		assertEquals(3000, s.btDelayMs);
	}

	@Test
	public void storedValuesRead() {
		Map<String, Object> m = new HashMap<>();
		m.put(Keys.STARTUP_DELAY_S, 0);
		m.put(Keys.STARTUP_RETRY_COUNT, "3");
		m.put(Keys.STARTUP_TRIGGER, "BOTH");
		m.put(Keys.STARTUP_APP, "com.a/.Main");
		StartupSettings s = StartupSettings.from(m);
		assertEquals(0, s.delayMs);
		assertEquals(3, s.retryCount);
		assertTrue(s.triggersOnAcc() && s.triggersOnColdBoot());
		assertEquals("com.a/com.a.Main", s.app.flatten());
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
