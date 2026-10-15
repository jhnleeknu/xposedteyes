package com.pijulius.xposedteyes.carplay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CarplayMicTest {
	@Test
	public void gain() {
		assertEquals(5, CarplayMic.parseGain(""));
		assertEquals(5, CarplayMic.parseGain("x"));
		assertEquals(7, CarplayMic.parseGain(" 7 "));
		assertTrue(CarplayMic.isValidGain(3));
		assertTrue(CarplayMic.isValidGain(8));
		assertFalse(CarplayMic.isValidGain(9));
		assertFalse(CarplayMic.isValidGain(2));
	}

	@Test
	public void denoiseKeepsOtherFields() {
		assertEquals("0,2,3", CarplayMic.withDenoise("1,2,3", false));
		assertEquals("1,2,3", CarplayMic.withDenoise("0,2,3", true));
		assertEquals("0,0,0", CarplayMic.withDenoise("", false));
		assertEquals("1,0,0", CarplayMic.withDenoise("garbage;reboot", true));
		assertTrue(CarplayMic.isDenoiseOn(""));
		assertFalse(CarplayMic.isDenoiseOn("0,0,0"));
	}
}
