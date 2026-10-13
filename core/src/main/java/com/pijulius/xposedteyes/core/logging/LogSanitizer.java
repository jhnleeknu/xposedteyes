package com.pijulius.xposedteyes.core.logging;

import java.util.regex.Pattern;

/**
 * Removes values that must never reach a log or an exported report: phone
 * number like digit runs, MAC addresses, IMEI/serial like long numbers and
 * e-mail addresses. Applied both when writing and when exporting.
 */
public final class LogSanitizer {
	private LogSanitizer() {}

	private static final Pattern MAC = Pattern.compile("(?i)\\b[0-9a-f]{2}(?:[:-][0-9a-f]{2}){5}\\b");
	private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	/** 7+ digits, optionally separated by spaces/dashes/dots, optional leading +. */
	private static final Pattern LONG_NUMBER = Pattern.compile("\\+?\\d(?:[\\s.-]?\\d){6,}");

	public static String sanitize(String text) {
		if (text == null)
			return null;
		String out = MAC.matcher(text).replaceAll("<mac>");
		out = EMAIL.matcher(out).replaceAll("<email>");
		out = LONG_NUMBER.matcher(out).replaceAll("<number>");
		return out.replace('\n', ' ').replace('\r', ' ');
	}
}
