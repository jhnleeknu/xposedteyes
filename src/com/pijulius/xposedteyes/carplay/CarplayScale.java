package com.pijulius.xposedteyes.carplay;

/**
 * CarPlay UI size relative to CarLink's default.
 *
 * CarLink (2.22 - 2.25) sends the CarPlay screen description to its native
 * CarPlay service as command 218 with an int[11]:
 * [2],[3] display width/height px, [4],[5] the same, [6] physical width mm,
 * [7] physical height mm, [10] FPS (docs/research/carplay.md).
 * The iPhone sizes the UI from pixels per mm, so reporting a smaller physical
 * size makes the UI larger and vice versa. Pixels are not touched, so the
 * video size and touch mapping stay the same.
 */
public final class CarplayScale {
	private CarplayScale() {}

	public static final int COMMAND_SCREEN_INFO = 218;
	static final int PHYSICAL_WIDTH = 6;
	static final int PHYSICAL_HEIGHT = 7;

	public static final int DEFAULT_PERCENT = 100;
	public static final int MIN_PERCENT = 50;
	public static final int MAX_PERCENT = 200;

	public static int parsePercent(String value) {
		try {
			int p = Integer.parseInt(value.trim());
			return p >= MIN_PERCENT && p <= MAX_PERCENT ? p : DEFAULT_PERCENT;
		} catch (RuntimeException e) {
			return DEFAULT_PERCENT;
		}
	}

	/**
	 * Scales the physical size in a command 218 parameter array in place.
	 * Returns false (array untouched) when it is not that command, the layout
	 * is unexpected or the percent is the default.
	 */
	public static boolean apply(int command, int[] params, int percent) {
		if (command != COMMAND_SCREEN_INFO || params == null || params.length != 11)
			return false;
		if (percent == DEFAULT_PERCENT || percent < MIN_PERCENT || percent > MAX_PERCENT)
			return false;

		int width = params[PHYSICAL_WIDTH];
		int height = params[PHYSICAL_HEIGHT];
		if (width <= 0 || height <= 0 || width > 2000 || height > 2000)
			return false;

		params[PHYSICAL_WIDTH] = Math.max(1, Math.round(width * 100f / percent));
		params[PHYSICAL_HEIGHT] = Math.max(1, Math.round(height * 100f / percent));
		return true;
	}
}
