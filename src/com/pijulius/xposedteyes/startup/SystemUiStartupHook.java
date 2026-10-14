package com.pijulius.xposedteyes.startup;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Application;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.provider.Settings;

import com.pijulius.xposedteyes.settings.Keys;
import com.pijulius.xposedteyes.settings.StartupSettings;
import com.pijulius.xposedteyes.settings.Target;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

/**
 * Android side of the startup app manager, running inside SystemUI.
 *
 * Every event is posted to one background HandlerThread so neither disk
 * reads (XSharedPreferences.reload) nor PackageManager queries run on the
 * SystemUI main thread.
 */
public final class SystemUiStartupHook {
	private SystemUiStartupHook() {}

	/** ACC broadcasts seen on FYT based units (CC3). Matched exactly. */
	static final String[] ACC_ON = { "com.fyt.boot.ACCON", "com.glsx.boot.ACCON" };
	static final String[] ACC_OFF = { "com.fyt.boot.ACCOFF", "com.glsx.boot.ACCOFF" };

	private static final String TAG = "XposedTeyes startup: ";

	private static Handler handler;
	private static StartupCoordinator coordinator;
	private static Boolean bufferedBluetooth;

	// registerReceiver flags (RECEIVER_EXPORTED) exist from API 33; this runs on Android 10.
	@SuppressLint("UnspecifiedRegisterReceiverFlag")
	public static synchronized void install(final Application app) {
		if (handler != null)
			return;

		HandlerThread thread = new HandlerThread("XposedTeyesStartup");
		thread.start();
		handler = new Handler(thread.getLooper());

		final XSharedPreferences prefs = new XSharedPreferences(Keys.PACKAGE);
		final Env env = new Env(app, prefs);

		Context deviceProtected = app.createDeviceProtectedStorageContext();
		final StateStore store = new PrefsStateStore(
			deviceProtected.getSharedPreferences("xposedteyes_startup_state", Context.MODE_PRIVATE));

		handler.post(new Runnable() {
			@Override
			public void run() {
				coordinator = new StartupCoordinator(env, new HandlerScheduler(handler), store);
				coordinator.onHookInit(readBootId(app), isBootCompleted());

				if (bufferedBluetooth != null) {
					coordinator.onBluetoothState(bufferedBluetooth);
					bufferedBluetooth = null;
				}
			}
		});

		IntentFilter events = new IntentFilter(Intent.ACTION_BOOT_COMPLETED);
		for (String a : ACC_ON)
			events.addAction(a);
		for (String a : ACC_OFF)
			events.addAction(a);

		app.registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();
				if (action == null || coordinator == null)
					return;

				if (Intent.ACTION_BOOT_COMPLETED.equals(action))
					coordinator.onBootCompleted();
				else if (matches(action, ACC_ON))
					coordinator.onAccOn();
				else if (matches(action, ACC_OFF))
					coordinator.onAccOff();
			}
		}, events, null, handler);

		// Only the module app (same signature) holds this permission. The
		// intent carries no data: SystemUI just re-reads the stored settings.
		app.registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (coordinator != null)
					coordinator.onSettingsChanged();
			}
		}, new IntentFilter(Keys.ACTION_SETTINGS_CHANGED), Keys.PERMISSION_CONTROL, handler);
	}

	/** From the BluetoothControl hook; may run on any thread. */
	public static void onBluetoothState(final boolean connected) {
		Handler h = handler;
		if (h == null) {
			bufferedBluetooth = connected;
			return;
		}

		h.post(new Runnable() {
			@Override
			public void run() {
				if (coordinator == null)
					bufferedBluetooth = connected;
				else
					coordinator.onBluetoothState(connected);
			}
		});
	}

	static boolean matches(String action, String[] actions) {
		for (String a : actions)
			if (a.equals(action))
				return true;
		return false;
	}

	/**
	 * Identifies the current boot without exposing it: Settings.Global
	 * boot_count, else the kernel boot_id. Null if neither is readable.
	 */
	static String readBootId(Context context) {
		try {
			int count = Settings.Global.getInt(context.getContentResolver(), "boot_count", -1);
			if (count >= 0)
				return "count:" + count;
		} catch (RuntimeException e) {}

		BufferedReader r = null;
		try {
			r = new BufferedReader(new FileReader("/proc/sys/kernel/random/boot_id"));
			String id = r.readLine();
			if (id != null && !id.trim().isEmpty())
				return "kernel:" + id.trim();
		} catch (Exception e) {
		} finally {
			try {
				if (r != null)
					r.close();
			} catch (Exception e) {}
		}
		return null;
	}

	// SystemUI is a platform app; reading sys.boot_completed is permitted there.
	@SuppressLint("PrivateApi")
	static boolean isBootCompleted() {
		try {
			Class<?> sp = Class.forName("android.os.SystemProperties");
			Object v = sp.getMethod("get", String.class, String.class).invoke(null, "sys.boot_completed", "");
			return "1".equals(v);
		} catch (Exception e) {
			// Unknown: treat as completed so a missed event never launches
			// unless this boot was seen before completion.
			return true;
		}
	}

	private static final class HandlerScheduler implements Scheduler {
		private final Handler handler;

		HandlerScheduler(Handler handler) {
			this.handler = handler;
		}

		@Override
		public Task schedule(final Runnable r, long delayMs) {
			// Wrap so removeCallbacks only removes this exact task.
			final Runnable wrapped = new Runnable() {
				@Override
				public void run() {
					r.run();
				}
			};
			handler.postDelayed(wrapped, delayMs);
			return new Task() {
				@Override
				public void cancel() {
					handler.removeCallbacks(wrapped);
				}
			};
		}

		@Override
		public long nowMs() {
			return SystemClock.uptimeMillis();
		}
	}

	private static final class PrefsStateStore implements StateStore {
		private final SharedPreferences prefs;

		PrefsStateStore(SharedPreferences prefs) {
			this.prefs = prefs;
		}

		@Override
		public String get(String key) {
			return prefs.getString(key, null);
		}

		@Override
		public void put(String key, String value) {
			prefs.edit().putString(key, value).apply();
		}

		@Override
		public void clear() {
			prefs.edit().clear().apply();
		}
	}

	static final class Env implements StartupEnv {
		private final Application app;
		private final XSharedPreferences prefs;

		Env(Application app, XSharedPreferences prefs) {
			this.app = app;
			this.prefs = prefs;
		}

		@Override
		public StartupSettings loadSettings() {
			prefs.reload();
			return StartupSettings.from(prefs.getAll());
		}

		@Override
		public Target resolve(Target target) {
			PackageManager pm = app.getPackageManager();
			Intent query = new Intent(Intent.ACTION_MAIN);
			query.addCategory(Intent.CATEGORY_LAUNCHER);
			query.setPackage(target.packageName);

			// Only enabled launcher entries of enabled apps are returned.
			List<ResolveInfo> list = pm.queryIntentActivities(query, 0);
			if (list == null || list.isEmpty())
				return null;

			Target first = null;
			for (ResolveInfo ri : list) {
				ActivityInfo ai = ri.activityInfo;
				if (ai == null || !ai.exported)
					continue;
				Target t = Target.parse(ai.packageName + "/" + ai.name);
				if (t == null)
					continue;
				if (first == null)
					first = t;
				if (target.className != null && target.className.equals(ai.name))
					return t;
			}

			// Package-only target, or the selected entry point disappeared in
			// an update: use the same app's launcher activity.
			return first;
		}

		@Override
		public LaunchOutcome launchSingle(Target target) {
			Intent intent = new Intent(Intent.ACTION_MAIN);
			intent.addCategory(Intent.CATEGORY_LAUNCHER);
			intent.setComponent(new ComponentName(target.packageName, target.className));
			// Bring an existing task to the front instead of a second instance.
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
			return start(intent);
		}

		private LaunchOutcome start(Intent intent) {
			try {
				app.startActivity(intent);
				return LaunchOutcome.ACCEPTED;
			} catch (ActivityNotFoundException e) {
				XposedBridge.log(TAG + "launch failed: " + e);
				return LaunchOutcome.FAILED_PERMANENT;
			} catch (SecurityException e) {
				XposedBridge.log(TAG + "launch refused: " + e);
				return LaunchOutcome.FAILED_PERMANENT;
			} catch (RuntimeException e) {
				XposedBridge.log(TAG + "launch error: " + e);
				return LaunchOutcome.FAILED_RETRYABLE;
			}
		}

		@Override
		@SuppressWarnings("deprecation")
		public String foregroundPackage() {
			try {
				ActivityManager am = (ActivityManager)app.getSystemService(Context.ACTIVITY_SERVICE);
				List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
				if (tasks == null || tasks.isEmpty() || tasks.get(0).topActivity == null)
					return null;
				return tasks.get(0).topActivity.getPackageName();
			} catch (RuntimeException e) {
				return null;
			}
		}

		@Override
		public String homePackage() {
			try {
				Intent home = new Intent(Intent.ACTION_MAIN);
				home.addCategory(Intent.CATEGORY_HOME);
				ResolveInfo ri = app.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
				if (ri == null || ri.activityInfo == null)
					return null;
				String pkg = ri.activityInfo.packageName;
				// "android" is the chooser: no default home set.
				return "android".equals(pkg) ? null : pkg;
			} catch (RuntimeException e) {
				return null;
			}
		}

		@Override
		public void report(StatusReport r) {
			XposedBridge.log(TAG + r);

			try {
				Intent intent = new Intent(Keys.ACTION_STARTUP_STATUS);
				intent.setPackage(Keys.PACKAGE);
				intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
				intent.putExtra("kind", r.kind.name());
				intent.putExtra("cause", r.cause.name());
				intent.putExtra("status", r.status.name());
				intent.putExtra("detail", r.detail);
				intent.putExtra("attempt", r.attempt);
				app.sendBroadcast(intent);
			} catch (RuntimeException e) {
				XposedBridge.log(TAG + "status broadcast failed: " + e);
			}
		}
	}
}
