package com.pijulius.xposedteyes;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import com.pijulius.xposedteyes.settings.Keys;

/** The shared settings file, readable by the hooks through XSharedPreferences. */
public final class Prefs {
	private Prefs() {}

	public static final String NAME = Keys.PACKAGE + "_preferences";

	@SuppressWarnings("deprecation")
	@SuppressLint("WorldReadableFiles")
	public static SharedPreferences get(Context context) {
		try {
			// LSPosed (xposedsharedprefs) makes MODE_WORLD_READABLE work for modules.
			return context.getSharedPreferences(NAME, Context.MODE_WORLD_READABLE);
		} catch (SecurityException e) {
			// Module not active: settings still editable, hooks will not see them.
			return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
		}
	}

	/** True when the module framework allows sharing the settings with the hooks. */
	@SuppressWarnings("deprecation")
	@SuppressLint("WorldReadableFiles")
	public static boolean isWorldReadable(Context context) {
		try {
			context.getSharedPreferences(NAME, Context.MODE_WORLD_READABLE);
			return true;
		} catch (SecurityException e) {
			return false;
		}
	}

	/** Tells SystemUI to re-read the settings (cancels pending work that no longer matches). */
	public static void notifyChanged(Context context) {
		android.content.Intent intent = new android.content.Intent(Keys.ACTION_SETTINGS_CHANGED);
		intent.setPackage("com.android.systemui");
		context.sendBroadcast(intent);
	}
}
