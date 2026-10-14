package com.pijulius.xposedteyes;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.provider.Settings;

/**
 * Enabling the split screen LauncherService accessibility service.
 *
 * The preferred way is the system accessibility settings screen. The root
 * path is an explicit opt-in and keeps every service that was already enabled
 * (3.0 overwrote the list with only this service).
 */
public final class AccessibilityHelper {
	private AccessibilityHelper() {}

	public static final String SERVICE =
		"com.pijulius.xposedteyes/com.pijulius.xposedteyes.LauncherService";
	private static final String SERVICE_SHORT =
		"com.pijulius.xposedteyes/.LauncherService";

	public static final int RESULT_OK = 0;
	public static final int RESULT_TIMEOUT = -2;
	public static final int RESULT_ERROR = -1;
	public static final int RESULT_BAD_STATE = -3;

	static final long ROOT_TIMEOUT_S = 10;

	public static boolean isEnabled(Context context) {
		try {
			int on = Settings.Secure.getInt(context.getContentResolver(),
				Settings.Secure.ACCESSIBILITY_ENABLED, 0);
			String list = Settings.Secure.getString(context.getContentResolver(),
				Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
			return on == 1 && containsService(list);
		} catch (RuntimeException e) {
			return false;
		}
	}

	static boolean containsService(String list) {
		for (String s : split(list))
			if (s.equalsIgnoreCase(SERVICE) || s.equalsIgnoreCase(SERVICE_SHORT))
				return true;
		return false;
	}

	private static List<String> split(String list) {
		List<String> out = new ArrayList<>();
		if (list == null)
			return out;
		for (String s : list.split(":")) {
			s = s.trim();
			if (!s.isEmpty() && !"null".equals(s))
				out.add(s);
		}
		return out;
	}

	/** Returns the colon separated list with our service appended once; existing entries kept in order. */
	public static String merge(String existing) {
		List<String> items = split(existing);
		if (!containsService(existing))
			items.add(SERVICE);
		StringBuilder sb = new StringBuilder();
		for (String s : items) {
			if (sb.length() > 0)
				sb.append(':');
			sb.append(s);
		}
		return sb.toString();
	}

	/** Only component names may be passed to the shell. */
	static boolean isSafeList(String list) {
		return list.matches("[A-Za-z0-9_.$/:]*");
	}

	/**
	 * Enables the service through su, preserving the enabled list. Blocks up
	 * to {@link #ROOT_TIMEOUT_S} seconds per command; call off the main thread.
	 */
	public static int enableViaRoot() {
		String current = runRoot("settings get secure enabled_accessibility_services");
		if (current == null)
			return RESULT_ERROR;

		String merged = merge(current.trim());
		if (!isSafeList(merged))
			return RESULT_BAD_STATE;

		String out = runRoot(
			"settings put secure enabled_accessibility_services '" + merged + "' && " +
			"settings put secure accessibility_enabled 1 && echo ok");
		if (out == null)
			return RESULT_ERROR;
		return out.trim().endsWith("ok") ? RESULT_OK : RESULT_ERROR;
	}

	/** Runs one command as root; returns stdout or null on failure/timeout. */
	private static String runRoot(String command) {
		Process p = null;
		try {
			p = Runtime.getRuntime().exec("su");
			DataOutputStream os = new DataOutputStream(p.getOutputStream());
			os.writeBytes(command + "\n");
			os.writeBytes("exit\n");
			os.flush();

			if (!p.waitFor(ROOT_TIMEOUT_S, TimeUnit.SECONDS))
				return null;
			if (p.exitValue() != 0)
				return null;

			StringBuilder sb = new StringBuilder();
			BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line;
			while ((line = r.readLine()) != null)
				sb.append(line).append('\n');
			return sb.toString();
		} catch (Exception e) {
			return null;
		} finally {
			if (p != null)
				p.destroy();
		}
	}
}
