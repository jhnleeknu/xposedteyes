package com.pijulius.xposedteyes;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.pijulius.xposedteyes.core.settings.AppSettings;
import com.pijulius.xposedteyes.core.settings.LaunchMode;
import com.pijulius.xposedteyes.diag.LocalLog;
import com.pijulius.xposedteyes.startup.Targets;

/**
 * Starts the configured split screen pair. Exported so SystemUI can start it,
 * but it reads nothing from the incoming intent: it only ever launches the apps
 * the user selected in the settings.
 */
public class StartupSplitActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final AppSettings settings = ModulePrefs.settings(this);
		final Intent first = Targets.launchIntent(this, settings.startup.primary);
		final Intent second = Targets.launchIntent(this, settings.startup.secondary);

		if (settings.startup.mode != LaunchMode.SPLIT || first == null || second == null) {
			LocalLog.add(this, "split: targets not launchable");
			Toast.makeText(this, R.string.toast_app_not_found, Toast.LENGTH_SHORT).show();
			finish();
			return;
		}

		if (RootAccessibility.isEnabled(this)) {
			startSplit();
			finish();
			return;
		}

		if (!settings.rootAccessibilityAssist || settings.killSwitch) {
			fallbackToFirst(first, R.string.toast_split_needs_accessibility);
			return;
		}

		// Consent was given: try to re-enable the service off the main thread.
		new Thread(new Runnable() {
			@Override
			public void run() {
				final int result = RootAccessibility.enable(StartupSplitActivity.this);
				LocalLog.add(StartupSplitActivity.this, "split: root accessibility " + RootAccessibility.describe(result));
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (result == RootAccessibility.OK || result == RootAccessibility.ALREADY_ENABLED) {
							// Give the freshly enabled service a moment to connect.
							getWindow().getDecorView().postDelayed(new Runnable() {
								@Override
								public void run() {
									startSplit();
									finish();
								}
							}, 1500);
						} else {
							fallbackToFirst(first, R.string.toast_root_accessibility_failed);
						}
					}
				});
			}
		}, "XposedTeyesRootA11y").start();
	}

	private void startSplit() {
		Intent intent = new Intent(this, LauncherService.class);
		intent.setAction(LauncherService.ACTION_STARTUP_SPLIT);
		startService(intent);
	}

	/** Documented fallback: without the accessibility service only the first app is shown. */
	private void fallbackToFirst(Intent first, int messageRes) {
		LocalLog.add(this, "split: accessibility service unavailable, launching first app only");
		Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show();
		first.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
		try {
			startActivity(first);
		} catch (RuntimeException e) {
			LocalLog.add(this, "split: fallback launch failed " + e.getClass().getSimpleName());
		}
		finish();
	}
}
