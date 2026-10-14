package com.pijulius.xposedteyes;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.pijulius.xposedteyes.settings.Keys;
import com.pijulius.xposedteyes.startup.StartupStatus;

/**
 * Receives status reports from the SystemUI hook. The manifest restricts
 * senders to holders of STATUS_BAR_SERVICE (SystemUI / platform apps).
 * Only known enum values are stored.
 */
public class StatusReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		if (!Keys.ACTION_STARTUP_STATUS.equals(intent.getAction()))
			return;

		String kind = safeToken(intent.getStringExtra("kind"));
		String cause = safeToken(intent.getStringExtra("cause"));
		String status = safeToken(intent.getStringExtra("status"));
		String detail = safeToken(intent.getStringExtra("detail"));
		int attempt = intent.getIntExtra("attempt", 0);

		if (!"STARTUP".equals(kind) && !"BLUETOOTH".equals(kind))
			return;
		try {
			StartupStatus.valueOf(status);
		} catch (IllegalArgumentException e) {
			return;
		}

		String summary = status + (detail.isEmpty() ? "" : " (" + detail + ")") + " · " + cause +
			(attempt > 0 ? " · #" + attempt : "");
		StatusStore.saveLast(context, kind, summary);
	}

	static String safeToken(String s) {
		if (s == null)
			return "";
		s = s.replaceAll("[^A-Za-z0-9_.:]", "");
		return s.length() > 80 ? s.substring(0, 80) : s;
	}
}
