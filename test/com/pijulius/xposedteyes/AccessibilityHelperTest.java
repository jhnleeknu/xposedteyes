package com.pijulius.xposedteyes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Part of T-ST-22: the existing accessibility service list is never lost. */
public class AccessibilityHelperTest {
	@Test
	public void mergeKeepsExistingServices() {
		assertEquals("a.b/.X:" + AccessibilityHelper.SERVICE, AccessibilityHelper.merge("a.b/.X"));
		assertEquals(AccessibilityHelper.SERVICE, AccessibilityHelper.merge(""));
		assertEquals(AccessibilityHelper.SERVICE, AccessibilityHelper.merge("null"));
		assertEquals(AccessibilityHelper.SERVICE, AccessibilityHelper.merge(null));
	}

	@Test
	public void mergeIsIdempotent() {
		String once = AccessibilityHelper.merge("a.b/.X:c.d/c.d.Y");
		assertEquals(once, AccessibilityHelper.merge(once));
		assertEquals("com.pijulius.xposedteyes/.LauncherService:a.b/.X",
			AccessibilityHelper.merge("com.pijulius.xposedteyes/.LauncherService:a.b/.X"));
	}

	@Test
	public void shellSafety() {
		assertTrue(AccessibilityHelper.isSafeList(AccessibilityHelper.merge("a.b/.X")));
		assertFalse(AccessibilityHelper.isSafeList("a.b/.X';reboot;'"));
	}
}
