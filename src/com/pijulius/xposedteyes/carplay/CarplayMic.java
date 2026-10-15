package com.pijulius.xposedteyes.carplay;

import java.util.regex.Pattern;

/**
 * CarPlay call microphone properties read by CarLink 2.22 - 2.25
 * (LsecGetConfigs in libcarplayplugin_*.so / libAirPlay.so, see
 * docs/research/bt-mic.md). They do not affect normal Bluetooth calls.
 */
public final class CarplayMic {
	private CarplayMic() {}

	/** Mic volume of the blink echo canceller ("cpblink volume"), CarLink default 5. */
	public static final String PROP_GAIN = "persist.btmic.gain";
	public static final int GAIN_DEFAULT = 5;
	public static final int GAIN_MIN = 3;
	public static final int GAIN_MAX = 8;

	/** "denoise,?,?" - only the first field (noise suppression on/off) is changed. */
	public static final String PROP_NOISE = "persist.lsec.cp.noise";
	public static final String NOISE_DEFAULT = "1,0,0";

	private static final Pattern NOISE = Pattern.compile("-?\\d{1,4},-?\\d{1,4},-?\\d{1,4}");

	public static boolean isValidGain(int gain) {
		return gain >= GAIN_MIN && gain <= GAIN_MAX;
	}

	/** Current gain, or the CarLink default when unset or not a number. */
	public static int parseGain(String value) {
		try {
			return Integer.parseInt(value.trim());
		} catch (RuntimeException e) {
			return GAIN_DEFAULT;
		}
	}

	/** Current noise property, or the CarLink default when unset or malformed. */
	public static String normalizeNoise(String value) {
		return value != null && NOISE.matcher(value.trim()).matches() ? value.trim() : NOISE_DEFAULT;
	}

	public static boolean isDenoiseOn(String noiseValue) {
		return !normalizeNoise(noiseValue).startsWith("0,");
	}

	/** Same value with only the first (denoise) field replaced. */
	public static String withDenoise(String current, boolean on) {
		String v = normalizeNoise(current);
		return (on ? "1" : "0") + v.substring(v.indexOf(','));
	}
}
