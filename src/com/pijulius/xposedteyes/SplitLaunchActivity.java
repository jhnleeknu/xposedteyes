package com.pijulius.xposedteyes;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.pijulius.xposedteyes.settings.Keys;
import com.pijulius.xposedteyes.settings.Target;
import com.pijulius.xposedteyes.startup.SystemUiStartupHook;

/**
 * Entry point for a split screen startup launch. Started by SystemUI only
 * (guarded by a signature permission in the manifest) or by "Launch now".
 *
 * Split screen needs the LauncherService accessibility service. Without it
 * only the first app is launched and the user is told why.
 */
public class SplitLaunchActivity extends Activity {

	@Override
	protected void onCreate(Bundle bundle) {
		super.onCreate(bundle);

		Intent in = getIntent();
		final String primary = in.getStringExtra(SystemUiStartupHook.EXTRA_PRIMARY);
		final String secondary = in.getStringExtra(SystemUiStartupHook.EXTRA_SECONDARY);
		final Context app = getApplicationContext();

		if (Target.parse(primary) == null || Target.parse(secondary) == null) {
			finish();
			return;
		}

		if (AccessibilityHelper.isEnabled(this)) {
			startSplit(app, primary, secondary);
			finish();
			return;
		}

		boolean rootAutoEnable = getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE)
			.getBoolean(Keys.A11Y_ROOT_AUTO_ENABLE, false);

		if (!rootAutoEnable) {
			fallbackSingle(primary);
			finish();
			return;
		}

		// Root activation blocks; never on the main thread. The service is
		// bound by the system afterwards and may launch apps itself.
		new Thread(new Runnable() {
			@Override
			public void run() {
				final int result = AccessibilityHelper.enableViaRoot();
				try {
					Thread.sleep(1500);
				} catch (InterruptedException e) {}

				new Handler(Looper.getMainLooper()).post(new Runnable() {
					@Override
					public void run() {
						if (result == AccessibilityHelper.RESULT_OK && AccessibilityHelper.isEnabled(app)) {
							startSplit(app, primary, secondary);
						} else {
							StatusStore.append(app, "SPLIT a11y_root_failed result=" + result);
							Toast.makeText(app, R.string.toast_split_service_off, Toast.LENGTH_LONG).show();
						}
					}
				});
			}
		}).start();

		finish();
	}

	static void startSplit(Context context, String primary, String secondary) {
		Intent intent = new Intent(context, LauncherService.class);
		intent.setAction(LauncherService.ACTION_SPLIT);
		intent.putExtra(SystemUiStartupHook.EXTRA_PRIMARY, primary);
		intent.putExtra(SystemUiStartupHook.EXTRA_SECONDARY, secondary);
		context.startService(intent);
	}

	/** Split screen unavailable: launch only the first app (while this activity is in front). */
	private void fallbackSingle(String primary) {
		Target t = Target.parse(primary);
		Intent launch;
		if (t.isPackageOnly()) {
			launch = getPackageManager().getLaunchIntentForPackage(t.packageName);
		} else {
			launch = new Intent(Intent.ACTION_MAIN);
			launch.addCategory(Intent.CATEGORY_LAUNCHER);
			launch.setClassName(t.packageName, t.className);
		}

		StatusStore.append(this, "SPLIT a11y_off fallback=first_app");
		Toast.makeText(this, R.string.toast_split_service_off, Toast.LENGTH_LONG).show();

		if (launch == null)
			return;
		launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
		try {
			startActivity(launch);
		} catch (RuntimeException e) {
			StatusStore.append(this, "SPLIT fallback_failed " + e.getClass().getSimpleName());
		}
	}
}
