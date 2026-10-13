package com.pijulius.xposedteyes;

import java.util.Map;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import com.pijulius.xposedteyes.core.settings.AppSettings;

/**
 * Access to the module preferences inside the module's own process. The file is
 * opened MODE_WORLD_READABLE so LSPosed (xposedsharedprefs) can expose it to
 * XSharedPreferences in the hooked processes; without an active LSPosed that
 * mode throws, and the private mode is used instead (hooks are inactive anyway).
 */
public final class ModulePrefs {
	private ModulePrefs() {}

	public static final String PACKAGE = "com.pijulius.xposedteyes";
	public static final String FILE = PACKAGE + "_preferences";

	/** Sent to SystemUI after a settings change; requires {@link #PERMISSION_SETTINGS_SYNC}. */
	public static final String ACTION_SETTINGS_CHANGED = PACKAGE + ".action.SETTINGS_CHANGED";
	public static final String PERMISSION_SETTINGS_SYNC = PACKAGE + ".permission.SETTINGS_SYNC";

	/** Status report from SystemUI to {@link com.pijulius.xposedteyes.diag.StatusReceiver}. */
	public static final String ACTION_LAUNCH_STATUS = PACKAGE + ".action.LAUNCH_STATUS";

	public static final String LAUNCHER_SERVICE = PACKAGE + "/" + PACKAGE + ".LauncherService";

	@SuppressLint("WorldReadableFiles")
	@SuppressWarnings("deprecation")
	public static SharedPreferences get(Context context) {
		try {
			return context.getSharedPreferences(FILE, Context.MODE_WORLD_READABLE);
		} catch (SecurityException e) {
			return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
		}
	}

	public static AppSettings settings(Context context) {
		Map<String, ?> all = get(context).getAll();
		return AppSettings.read(all);
	}
}
