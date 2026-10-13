package com.pijulius.xposedteyes.core.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.pijulius.xposedteyes.core.compat.Feature;
import com.pijulius.xposedteyes.core.compat.FeatureStatus;

public class FeatureStatusTest {
	@Test
	public void tSt23_unverifiedAdvancedFeaturesAreNotControllable() {
		assertFalse(Feature.POST_LAUNCH_RECOVERY.controllable());
		assertFalse(Feature.LAST_APP_RESTORE.controllable());
		assertFalse(Feature.CARPLAY_DISPLAY.controllable());
		assertFalse(Feature.BT_MIC_CONTROLS.controllable());
		assertTrue(Feature.STARTUP_LAUNCH.controllable());
	}

	@Test
	public void nothingClaimsDeviceVerificationWithoutEvidence() {
		for (Feature f : Feature.values())
			assertTrue(f.name(), f.device != FeatureStatus.DEVICE_VERIFIED);
	}
}
