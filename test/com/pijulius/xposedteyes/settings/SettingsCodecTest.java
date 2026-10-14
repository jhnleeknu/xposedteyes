package com.pijulius.xposedteyes.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/** T-ST-17 (import part). */
public class SettingsCodecTest {

	private static Map<String, Object> sample() {
		Map<String, Object> m = new HashMap<>(StartupSettings.defaults());
		m.put(Keys.STARTUP_ENABLED, true);
		m.put(Keys.STARTUP_PRIMARY, "com.a/com.a.Main");
		m.put("screenRatio", "0.33");
		m.put("carlinkResolution", "1920x1080");
		m.put("cameraX", "430");
		m.put(Keys.LEGACY_MUSIC_APP, "com.audials");
		m.put("someInternalKey", "x");
		return m;
	}

	@Test
	public void roundTrip() {
		Map<String, Object> m = sample();
		SettingsCodec.ImportResult r = SettingsCodec.parse(SettingsCodec.export(m));
		assertTrue(r.error, r.ok());
		m.remove("someInternalKey");
		for (Map.Entry<String, Object> e : m.entrySet())
			assertEquals(e.getKey(), e.getValue(), r.values.get(e.getKey()));
		assertFalse(r.values.containsKey("someInternalKey"));
	}

	@Test
	public void rejectsOutOfRangeAndWrongTypes() {
		assertFalse(SettingsCodec.parse(json("\"startupDelaySeconds\": 61")).ok());
		assertFalse(SettingsCodec.parse(json("\"startupDelaySeconds\": \"5\"")).ok());
		assertFalse(SettingsCodec.parse(json("\"startupEnabled\": \"true\"")).ok());
		assertFalse(SettingsCodec.parse(json("\"startupMode\": \"TRIPLE\"")).ok());
		assertFalse(SettingsCodec.parse(json("\"startupPrimary\": \"com.a/com.a.Main; reboot\"")).ok());
		assertFalse(SettingsCodec.parse(json("\"screenRatio\": \"7\"")).ok());
		assertFalse(SettingsCodec.parse(json("\"launcherMusicApp\": \"com.a/com.a.Main\"")).ok());
	}

	@Test
	public void rejectsCorruptFiles() {
		assertFalse(SettingsCodec.parse(null).ok());
		assertFalse(SettingsCodec.parse("").ok());
		assertFalse(SettingsCodec.parse("{").ok());
		assertFalse(SettingsCodec.parse("[]").ok());
		assertFalse(SettingsCodec.parse("{\"format\":\"other\",\"schemaVersion\":1,\"values\":{}}").ok());
		assertFalse(SettingsCodec.parse("{\"format\":\"xposedteyes-settings\",\"schemaVersion\":2,\"values\":{}}").ok());
	}

	@Test
	public void unknownKeysSkipped() {
		SettingsCodec.ImportResult r = SettingsCodec.parse(json("\"evil.className\": \"x\", \"startupDelaySeconds\": 10"));
		assertTrue(r.ok());
		assertEquals(1, r.skippedUnknown);
		assertEquals(10, r.values.get(Keys.STARTUP_DELAY_S));
	}

	private static String json(String values) {
		return "{\"format\":\"xposedteyes-settings\",\"schemaVersion\":1,\"values\":{" + values + "}}";
	}
}
