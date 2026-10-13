package com.pijulius.xposedteyes.startup;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.Map;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.provider.Settings;

import com.pijulius.xposedteyes.ModulePrefs;
import com.pijulius.xposedteyes.core.logging.LogSanitizer;
import com.pijulius.xposedteyes.core.settings.AppSettings;
import com.pijulius.xposedteyes.core.startup.LaunchCoordinator;
import com.pijulius.xposedteyes.core.startup.LaunchRecord;
import com.pijulius.xposedteyes.core.startup.Ports;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

/**
 * Hosts the {@link LaunchCoordinator} inside SystemUI: owns its thread, turns
 * broadcasts and the bluetooth hook into coordinator events, and stops the
 * feature after repeated errors instead of disturbing SystemUI.
 */
public final class StartupRuntime {
	public static final String[] ACC_ON_ACTIONS = {"com.fyt.boot.ACCON", "com.glsx.boot.ACCON"};
	public static final String[] ACC_OFF_ACTIONS = {"com.fyt.boot.ACCOFF", "com.glsx.boot.ACCOFF"};

	/** BluetoothControl.updata() state values with a known meaning; others are ignored. */
	public static final int BT_STATE_DISCONNECTED = 0;
	public static final int BT_STATE_CONNECTED = 2;

	private static final int MAX_ERRORS = 5;
	private static final String TAG = "XposedTeyes/startup: ";

	private static StartupRuntime instance;

	private final Application app;
	private final XSharedPreferences prefs;
	private final Handler handler;
	private final LaunchCoordinator coordinator;
	private int errors;
	private volatile boolean stopped;

	public static synchronized void install(Application app) {
		if (instance != null)
			return;
		try {
			instance = new StartupRuntime(app);
			instance.start();
		} catch (Throwable t) {
			XposedBridge.log(TAG + "disabled, install failed: " + t);
			instance = null;
		}
	}

	/** Called from the BluetoothControl hook; unknown states are logged and ignored. */
	public static void onBluetoothState(final int state) {
		final StartupRuntime rt = instance;
		if (rt == null)
			return;
		if (state != BT_STATE_CONNECTED && state != BT_STATE_DISCONNECTED) {
			XposedBridge.log(TAG + "ignored bluetooth state " + state);
			return;
		}
		rt.post(new Runnable() {
			@Override
			public void run() {
				rt.coordinator.onBluetooth(state == BT_STATE_CONNECTED);
			}
		});
	}

	private StartupRuntime(Application app) {
		this.app = app;
		this.prefs = new XSharedPreferences(ModulePrefs.PACKAGE);
		HandlerThread thread = new HandlerThread("XposedTeyesStartup");
		thread.start();
		this.handler = new Handler(thread.getLooper());

		Context deviceStorage = app.createDeviceProtectedStorageContext();
		final SharedPreferences runtimeState =
			deviceStorage.getSharedPreferences("xposedteyes_startup_state", Context.MODE_PRIVATE);

		this.coordinator = new LaunchCoordinator(
			new Ports.Scheduler() {
				@Override
				public Ports.Cancellable schedule(final Runnable task, long delayMs) {
					final Runnable guarded = guard(task);
					// Handler delays use uptimeMillis, a monotonic clock.
					handler.postDelayed(guarded, delayMs);
					return new Ports.Cancellable() {
						@Override
						public void cancel() {
							handler.removeCallbacks(guarded);
						}
					};
				}
			},
			new Ports.SettingsSource() {
				@Override
				public AppSettings load() {
					prefs.reload();
					Map<String, ?> all = prefs.getAll();
					return AppSettings.read(all);
				}
			},
			new Ports.RuntimeStore() {
				@Override
				public String get(String key) {
					return runtimeState.getString(key, null);
				}

				@Override
				public void put(String key, String value) {
					// commit(): this runs on the worker thread and must survive a crash right after.
					runtimeState.edit().putString(key, value).commit();
				}
			},
			new SystemUiLauncher(app),
			new Ports.StatusSink() {
				@Override
				public void report(LaunchRecord record) {
					reportStatus(record);
				}
			});
	}

	private void start() {
		post(new Runnable() {
			@Override
			public void run() {
				coordinator.init(bootKey(), isBootCompleted());
			}
		});

		IntentFilter system = new IntentFilter(Intent.ACTION_BOOT_COMPLETED);
		for (String a : ACC_ON_ACTIONS)
			system.addAction(a);
		for (String a : ACC_OFF_ACTIONS)
			system.addAction(a);

		app.registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				final String action = intent.getAction();
				if (action == null)
					return;
				post(new Runnable() {
					@Override
					public void run() {
						dispatch(action);
					}
				});
			}
		}, system, null, handler);

		// Only senders holding the module's signature permission (the module app) may poke this.
		app.registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				post(new Runnable() {
					@Override
					public void run() {
						coordinator.onSettingsChanged();
					}
				});
			}
		}, new IntentFilter(ModulePrefs.ACTION_SETTINGS_CHANGED), ModulePrefs.PERMISSION_SETTINGS_SYNC, handler);
	}

	/** Exact action matching only; unknown vendor actions are never guessed. */
	void dispatch(String action) {
		if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
			coordinator.onBootCompleted();
			return;
		}
		for (String a : ACC_ON_ACTIONS) {
			if (a.equals(action)) {
				coordinator.onAccOn();
				return;
			}
		}
		for (String a : ACC_OFF_ACTIONS) {
			if (a.equals(action)) {
				coordinator.onAccOff();
				return;
			}
		}
	}

	private void post(Runnable r) {
		handler.post(guard(r));
	}

	private Runnable guard(final Runnable r) {
		return new Runnable() {
			@Override
			public void run() {
				if (stopped)
					return;
				try {
					r.run();
				} catch (Throwable t) {
					onError(t);
				}
			}
		};
	}

	private void onError(Throwable t) {
		errors++;
		XposedBridge.log(TAG + "error " + errors + "/" + MAX_ERRORS + ": " + t);
		if (errors < MAX_ERRORS)
			return;
		try {
			coordinator.shutdown("error_limit");
		} catch (Throwable ignored) {
		}
		stopped = true;
		XposedBridge.log(TAG + "stopped for this SystemUI process after repeated errors");
	}

	private void reportStatus(LaunchRecord r) {
		XposedBridge.log(TAG + LogSanitizer.sanitize(r.toString()));
		try {
			Intent intent = new Intent(ModulePrefs.ACTION_LAUNCH_STATUS);
			intent.setComponent(new ComponentName(ModulePrefs.PACKAGE,
				ModulePrefs.PACKAGE + ".diag.StatusReceiver"));
			intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
			intent.putExtra("session", r.sessionId);
			intent.putExtra("cause", r.cause.name());
			intent.putExtra("status", r.status.name());
			intent.putExtra("reason", r.reason);
			intent.putExtra("attempts", r.attempts);
			intent.putExtra("target", r.target);
			intent.putExtra("final", r.isFinal);
			intent.putExtra("time", System.currentTimeMillis());
			app.sendBroadcast(intent);
		} catch (RuntimeException e) {
			XposedBridge.log(TAG + "status broadcast failed: " + e);
		}
	}

	/**
	 * Identifies the current boot for de-duplication. Stays on the device; never
	 * exported. Prefers the boot counter, then the kernel boot id, then the boot
	 * wall clock time in minutes.
	 */
	private String bootKey() {
		try {
			int count = Settings.Global.getInt(app.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
			if (count >= 0)
				return "count:" + count;
		} catch (RuntimeException ignored) {
		}

		try (BufferedReader r = new BufferedReader(new FileReader("/proc/sys/kernel/random/boot_id"))) {
			String id = r.readLine();
			if (id != null && !id.trim().isEmpty())
				return "id:" + id.trim();
		} catch (Exception ignored) {
		}

		return "rt:" + ((System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60_000L);
	}

	/** sys.boot_completed as set by the system; false when it cannot be read. */
	private static boolean isBootCompleted() {
		try {
			Class<?> sp = Class.forName("android.os.SystemProperties");
			Method get = sp.getMethod("get", String.class, String.class);
			return "1".equals(get.invoke(null, "sys.boot_completed", "0"));
		} catch (Throwable t) {
			return false;
		}
	}
}
