package com.pijulius.xposedteyes;

import java.io.DataOutputStream;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import android.content.Context;
import android.provider.Settings;

import com.pijulius.xposedteyes.core.settings.AccessibilityServices;

/**
 * Optional, consent based re-enabling of {@link LauncherService} through su.
 * The previous release overwrote the whole enabled service list; this keeps
 * every service already enabled and only appends ours.
 */
public final class RootAccessibility {
	private RootAccessibility() {}

	public static final int OK = 0;
	public static final int ALREADY_ENABLED = 1;
	public static final int FAILED = -1;
	public static final int TIMEOUT = -2;
	public static final int UNSAFE_VALUE = -3;

	private static final long TIMEOUT_SECONDS = 10;
	private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._$/:]+");

	public static boolean isEnabled(Context context) {
		String list = Settings.Secure.getString(context.getContentResolver(),
			Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
		return AccessibilityServices.contains(list, ModulePrefs.LAUNCHER_SERVICE);
	}

	/** Blocking; never call on the main thread. */
	public static int enable(Context context) {
		String current = Settings.Secure.getString(context.getContentResolver(),
			Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
		if (AccessibilityServices.contains(current, ModulePrefs.LAUNCHER_SERVICE))
			return ALREADY_ENABLED;

		String updated = AccessibilityServices.withService(current, ModulePrefs.LAUNCHER_SERVICE);
		if (!SAFE.matcher(updated).matches())
			return UNSAFE_VALUE;

		Process p = null;
		try {
			p = Runtime.getRuntime().exec("su");
			DataOutputStream os = new DataOutputStream(p.getOutputStream());
			os.writeBytes("settings put secure enabled_accessibility_services '" + updated + "'\n");
			os.writeBytes("settings put secure accessibility_enabled 1\n");
			os.writeBytes("exit\n");
			os.flush();

			if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS))
				return TIMEOUT;
			if (p.exitValue() != 0)
				return FAILED;
			return isEnabled(context) ? OK : FAILED;
		} catch (Exception e) {
			return FAILED;
		} finally {
			if (p != null)
				p.destroy();
		}
	}

	public static String describe(int code) {
		switch (code) {
			case OK: return "OK";
			case ALREADY_ENABLED: return "ALREADY_ENABLED";
			case TIMEOUT: return "TIMEOUT";
			case UNSAFE_VALUE: return "UNSAFE_VALUE";
			default: return "FAILED";
		}
	}
}
