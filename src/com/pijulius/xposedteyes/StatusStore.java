package com.pijulius.xposedteyes;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.Context;
import android.content.SharedPreferences;

import com.pijulius.xposedteyes.logging.LocalLog;

/**
 * Last startup status and the local diagnostics log. Private to the module
 * app (not the world readable settings file) and never sent anywhere.
 */
public final class StatusStore {
	private StatusStore() {}

	private static final String FILE = "xposedteyes_status";
	private static LocalLog log;

	static synchronized LocalLog log(Context context) {
		if (log == null)
			log = new LocalLog(context.getApplicationContext().getFilesDir());
		return log;
	}

	static SharedPreferences prefs(Context context) {
		return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
	}

	public static void append(Context context, String line) {
		String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
		log(context).append(time + " " + line);
	}

	public static void saveLast(Context context, String kind, String summary) {
		prefs(context).edit()
			.putString("last_" + kind, summary)
			.putLong("last_" + kind + "_time", System.currentTimeMillis())
			.apply();
	}

	public static String last(Context context, String kind) {
		SharedPreferences p = prefs(context);
		String s = p.getString("last_" + kind, null);
		if (s == null)
			return null;
		long t = p.getLong("last_" + kind + "_time", 0);
		return new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date(t)) + "  " + s;
	}
}
