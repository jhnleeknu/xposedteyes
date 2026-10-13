package com.pijulius.xposedteyes.core.logging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

public class RingLogTest {
	@Test
	public void sizeStaysBounded() throws Exception {
		File dir = Files.createTempDirectory("ringlog").toFile();
		RingLog log = new RingLog(dir, "t", 1024);
		for (int i = 0; i < 500; i++)
			log.append("line " + i + " xxxxxxxxxxxxxxxxxxxxxxxx");
		assertTrue(log.sizeOnDisk() <= 1024);
		String all = log.readAll();
		assertTrue(all.contains("line 499"));
		assertFalse(all.contains("line 0 "));
		log.clear();
		assertEquals(0, log.sizeOnDisk());
	}

	@Test
	public void tPriv01_sensitiveValuesAreMasked() {
		String s = LogSanitizer.sanitize(
			"call +82 10-1234-5678 from 00:11:22:AA:BB:CC imei 356938035643809 mail a.b@example.com\nnext");
		assertFalse(s.contains("1234"));
		assertFalse(s.contains("AA:BB"));
		assertFalse(s.contains("356938035643809"));
		assertFalse(s.contains("example.com"));
		assertFalse(s.contains("\n"));
		// Ordinary short numbers (delays, versions) survive.
		assertEquals("delay 5000 ms v3.1", LogSanitizer.sanitize("delay 5000 ms v3.1"));
	}
}
