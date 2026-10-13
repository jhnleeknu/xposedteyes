package com.pijulius.xposedteyes.diag;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.pijulius.xposedteyes.ModulePrefs;
import com.pijulius.xposedteyes.core.startup.LaunchCause;
import com.pijulius.xposedteyes.core.startup.LaunchStatus;

/**
 * Receives launch status reports from the SystemUI hook. The manifest requires
 * the sender to hold STATUS_BAR_SERVICE (SystemUI does, ordinary apps cannot).
 * Values are only displayed and logged, and every field is validated.
 */
public class StatusReceiver extends BroadcastReceiver {
	public static final String STATE_FILE = "runtime_status";

	@Override
	public void onReceive(Context context, Intent intent) {
		if (!ModulePrefs.ACTION_LAUNCH_STATUS.equals(intent.getAction()))
			return;

		LaunchCause cause = parse(LaunchCause.class, intent.getStringExtra("cause"));
		LaunchStatus status = parse(LaunchStatus.class, intent.getStringExtra("status"));
		if (cause == null || status == null)
			return;

		String reason = clean(intent.getStringExtra("reason"));
		String target = clean(intent.getStringExtra("target"));
		int attempts = Math.max(0, Math.min(10, intent.getIntExtra("attempts", 0)));
		boolean isFinal = intent.getBooleanExtra("final", true);
		long time = intent.getLongExtra("time", System.currentTimeMillis());

		String line = "systemui: " + cause + " " + status +
			(reason == null ? "" : " (" + reason + ")") + " attempts=" + attempts +
			(target == null ? "" : " target=" + target) + (isFinal ? "" : " [pending]");
		LocalLog.add(context, line);

		if (isFinal) {
			SharedPreferences state = context.getSharedPreferences(STATE_FILE, Context.MODE_PRIVATE);
			state.edit()
				.putLong("time", time)
				.putString("cause", cause.name())
				.putString("status", status.name())
				.putString("reason", reason)
				.putString("target", target)
				.putInt("attempts", attempts)
				.apply();
		}
	}

	private static <E extends Enum<E>> E parse(Class<E> type, String value) {
		if (value == null)
			return null;
		try {
			return Enum.valueOf(type, value);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** Allows only short identifier-like text (component names, reason codes). */
	private static String clean(String s) {
		if (s == null)
			return null;
		if (s.length() > 300 || !s.matches("[A-Za-z0-9._$/ +:_-]*"))
			return "<invalid>";
		return s;
	}
}
