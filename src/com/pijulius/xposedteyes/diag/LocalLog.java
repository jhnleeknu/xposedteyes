package com.pijulius.xposedteyes.diag;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.Context;

import com.pijulius.xposedteyes.core.logging.RingLog;

/** The module app's bounded (1 MiB) local log. Never uploaded; exported only by the user. */
public final class LocalLog {
	private LocalLog() {}

	private static RingLog log;

	public static synchronized RingLog get(Context context) {
		if (log == null)
			log = new RingLog(context.getApplicationContext().getFilesDir(), "xposedteyes", RingLog.DEFAULT_MAX_BYTES);
		return log;
	}

	public static void add(Context context, String message) {
		String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date());
		try {
			get(context).append(time + " " + message);
		} catch (IOException ignored) {
			// Logging must never break a feature.
		}
	}
}
