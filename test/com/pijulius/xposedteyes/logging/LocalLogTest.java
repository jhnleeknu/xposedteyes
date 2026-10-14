package com.pijulius.xposedteyes.logging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

public class LocalLogTest {
	@Test
	public void sizeStaysBounded() throws Exception {
		File dir = Files.createTempDirectory("log").toFile();
		LocalLog log = new LocalLog(dir, 10_000);
		for (int i = 0; i < 2_000; i++)
			log.append("line " + i + " xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
		assertTrue(log.totalBytes() <= 10_000);
		assertTrue(log.readAll().contains("line 1999"));
		assertFalse(log.readAll().contains("line 0 "));
		log.clear();
		assertEquals("", log.readAll());
	}

	@Test
	public void defaultLimitIsOneMiB() {
		assertEquals(1024 * 1024, LocalLog.MAX_TOTAL_BYTES);
	}

	@Test
	public void sanitizeMasksNumbersAndMacsAndNewlines() {
		String s = LocalLog.sanitize("call +82 1012345678 from AA:BB:CC:11:22:33\nnext");
		assertFalse(s.contains("1012345678"));
		assertFalse(s.contains("AA:BB:CC"));
		assertFalse(s.contains("\n"));
		assertTrue(s.contains("STARTED") || s.contains("next"));
		assertTrue(LocalLog.sanitize(new String(new char[2000]).replace('\0', 'a')).length() <= LocalLog.MAX_LINE + 3);
	}
}
