package com.pijulius.xposedteyes.core.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class SettingsTransferTest {
	@Test
	public void exportContainsOnlyWhitelistedValidKeys() {
		Map<String, Object> prefs = new HashMap<>();
		prefs.put(Keys.STARTUP_ENABLED, true);
		prefs.put(Keys.STARTUP_DELAY_MS, 3000);
		prefs.put("cameraWidth", "1570");
		prefs.put("someOtherInternalKey", "x");
		prefs.put(Keys.MIGRATION_NOTICE, "STARTUP_MIGRATED");
		Map<String, Object> out = SettingsTransfer.exportable(prefs);
		assertEquals(3, out.size());
		assertFalse(out.containsKey("someOtherInternalKey"));
	}

	@Test
	public void tSt17_corruptOrForeignFilesAreRejected() {
		Map<String, Object> v = new HashMap<>();
		v.put(Keys.STARTUP_ENABLED, true);
		assertFalse(SettingsTransfer.validateImport("other", 1, v).usable());
		assertFalse(SettingsTransfer.validateImport(SettingsTransfer.FORMAT, 99, v).usable());
		assertFalse(SettingsTransfer.validateImport(SettingsTransfer.FORMAT, null, v).usable());
		assertFalse(SettingsTransfer.validateImport(SettingsTransfer.FORMAT, 1, null).usable());
	}

	@Test
	public void tSt17_invalidEntriesAreDroppedValidOnesKept() {
		Map<String, Object> v = new HashMap<>();
		v.put(Keys.STARTUP_ENABLED, true);
		v.put(Keys.STARTUP_DELAY_MS, 61_000);          // out of range
		v.put(Keys.STARTUP_RETRY_COUNT, 2.0);          // JSON number, integral
		v.put(Keys.STARTUP_RETRY_DELAY_MS, 1.5);       // not integral
		v.put(Keys.STARTUP_TRIGGER, "NEVER");          // unknown enum
		v.put(Keys.STARTUP_PRIMARY, "$(reboot)");      // not a component
		v.put(Keys.BT_LAUNCH_TARGET, "com.syu.carlink/.Main");
		v.put("su", "reboot");                         // unknown key
		v.put("cameraWidth", "12a");
		v.put("carlinkResolution", "1920x1080");
		SettingsTransfer.ImportResult r = SettingsTransfer.validateImport(SettingsTransfer.FORMAT, 1, v);
		assertTrue(r.usable());
		assertEquals(true, r.accepted.get(Keys.STARTUP_ENABLED));
		assertEquals(2, r.accepted.get(Keys.STARTUP_RETRY_COUNT));
		assertEquals("com.syu.carlink/com.syu.carlink.Main", r.accepted.get(Keys.BT_LAUNCH_TARGET));
		assertEquals("1920x1080", r.accepted.get("carlinkResolution"));
		assertEquals(4, r.accepted.size());
		assertEquals(6, r.errors.size());
	}
}
