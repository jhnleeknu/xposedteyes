package com.pijulius.xposedteyes;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import com.pijulius.xposedteyes.settings.Keys;

/** The shared settings file, readable by the hooks through XSharedPreferences. */
public final class Prefs {
	private Prefs() {}

	public static final String NAME = Keys.PACKAGE + "_preferences";

	/**
	 * Result of the first world readable attempt in this process. Android
	 * checks the mode only when it first creates the preferences object, so
	 * later calls return the cached object and cannot tell.
	 */
	private static Boolean worldReadable;

	@SuppressWarnings("deprecation")
	@SuppressLint("WorldReadableFiles")
	public static SharedPreferences get(Context context) {
		try {
			// LSPosed (xposedsharedprefs) makes MODE_WORLD_READABLE work for modules.
			SharedPreferences prefs = context.getSharedPreferences(NAME, Context.MODE_WORLD_READABLE);
			synchronized (Prefs.class) {
				if (worldReadable == null)
					worldReadable = true;
			}
			return prefs;
		} catch (SecurityException e) {
			synchronized (Prefs.class) {
				worldReadable = false;
			}
			// Module not active: settings still editable, hooks will not see them.
			return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
		}
	}

	/** True when the module framework allows sharing the settings with the hooks. */
	public static boolean isWorldReadable(Context context) {
		get(context);
		synchronized (Prefs.class) {
			return Boolean.TRUE.equals(worldReadable);
		}
	}

	/** Tells SystemUI to re-read the settings (cancels pending work that no longer matches). */
	public static void notifyChanged(Context context) {
		android.content.Intent intent = new android.content.Intent(Keys.ACTION_SETTINGS_CHANGED);
		intent.setPackage("com.android.systemui");
		context.sendBroadcast(intent);
	}
}
