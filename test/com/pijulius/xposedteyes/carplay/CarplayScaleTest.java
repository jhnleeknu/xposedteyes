package com.pijulius.xposedteyes.carplay;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CarplayScaleTest {
	/** Layout as built by CarLink 2.25: 1280x720, 221 mm wide, 60 fps. */
	private static int[] params() {
		return new int[] { 0, 0, 1280, 720, 1280, 720, 221, 124, 0, 0x53667073, 60 };
	}

	@Test
	public void largerUiReportsSmallerPhysicalSize() {
		int[] p = params();
		assertTrue(CarplayScale.apply(218, p, 125));
		assertEquals(177, p[6]);
		assertEquals(99, p[7]);
		assertEquals(1280, p[2]);
		assertEquals(60, p[10]);
	}

	@Test
	public void smallerUiReportsLargerPhysicalSize() {
		int[] p = params();
		assertTrue(CarplayScale.apply(218, p, 80));
		assertEquals(276, p[6]);
		assertEquals(155, p[7]);
	}

	@Test
	public void untouchedWhenDefaultOrUnexpected() {
		int[] p = params();
		assertFalse(CarplayScale.apply(218, p, 100));
		assertFalse(CarplayScale.apply(214, p, 125));
		assertFalse(CarplayScale.apply(218, new int[8], 125));
		assertFalse(CarplayScale.apply(218, null, 125));
		assertArrayEquals(params(), p);
	}

	@Test
	public void parsePercent() {
		assertEquals(120, CarplayScale.parsePercent("120"));
		assertEquals(100, CarplayScale.parsePercent("abc"));
		assertEquals(100, CarplayScale.parsePercent("10"));
		assertEquals(100, CarplayScale.parsePercent("999"));
	}
}
