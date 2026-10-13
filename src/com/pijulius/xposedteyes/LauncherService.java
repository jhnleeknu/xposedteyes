package com.pijulius.xposedteyes;

import java.util.List;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import com.pijulius.xposedteyes.core.settings.AppSettings;
import com.pijulius.xposedteyes.startup.Targets;

public class LauncherService extends AccessibilityService {
	/** Split screen pair from the startup settings (see StartupSplitActivity). */
	public static final String ACTION_STARTUP_SPLIT = "com.pijulius.xposedteyes.action.STARTUP_SPLIT";

	int state = 0;
	boolean inSplitScreen = false;

	String musicApp;
	String navigationApp;
	String videosApp;
	String bluetoothApp;
	String firstApp;
	String secondApp;
	Intent firstIntent;
	Intent secondIntent;
	String packageName;
	String runningApp;

	@Override
	public void onCreate() {
		packageName = getPackageName();

		loadPreferences();
	}

	@Override
	protected void onServiceConnected() {
		loadPreferences();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		int result = super.onStartCommand(intent, flags, startId);

		if (intent == null)
			return result;

		String action = intent.getAction();

		if (!(packageName+".MusicNavigationActivity").equals(action) &&
			!(packageName+".NavigationVideosActivity").equals(action) &&
			!ACTION_STARTUP_SPLIT.equals(action))
		{
			return result;
		}

		loadPreferences();

		if (ACTION_STARTUP_SPLIT.equals(action)) {
			AppSettings settings = ModulePrefs.settings(this);
			firstIntent = Targets.launchIntent(this, settings.startup.primary);
			secondIntent = Targets.launchIntent(this, settings.startup.secondary);
		} else {
			firstApp = musicApp;
			secondApp = navigationApp;

			if ((packageName+".NavigationVideosActivity").equals(action)) {
				firstApp = navigationApp;
				secondApp = videosApp;
			}

			firstIntent = firstApp.isEmpty() ? null : getPackageManager().getLaunchIntentForPackage(firstApp);
			secondIntent = secondApp.isEmpty() ? null : getPackageManager().getLaunchIntentForPackage(secondApp);
		}

		firstApp = firstIntent == null ? null : firstIntent.getComponent().getPackageName();
		secondApp = secondIntent == null ? null : secondIntent.getComponent().getPackageName();

		if (firstApp == null || secondApp == null) {
			Toast.makeText(
					getBaseContext(), getString(R.string.toast_app_not_found), Toast.LENGTH_SHORT)
				.show();

			return result;
		}

		state = -1;
		AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOWS_CHANGED);
		onAccessibilityEvent(event);

		return result;
	}

	@Override
	public void onAccessibilityEvent(AccessibilityEvent event) {
		String eventPackageName = (String)event.getPackageName();

		if ((event.getEventType() & AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) != 0) {
			if (eventPackageName != null && !eventPackageName.equals(runningApp) &&
				!"com.android.systemui".equals(eventPackageName))
			{
				runningApp = eventPackageName;
			}

			if (inSplitScreenMode(getWindows())) {
				inSplitScreen = true;
			} else {
				inSplitScreen = false;
			}
		}

		if (firstApp == null || secondApp == null || firstApp.isEmpty() || secondApp.isEmpty())
			return;

		if (state == -1 && event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
			state = 1;

			Intent first = firstIntent == null ? null : new Intent(firstIntent);

			if (first == null) {
				Toast.makeText(
					getBaseContext(), getString(R.string.toast_app_not_found), Toast.LENGTH_SHORT)
				.show();

				return;
			}

			first.addCategory(Intent.CATEGORY_LAUNCHER);
			first.setFlags(
				Intent.FLAG_ACTIVITY_NEW_TASK |
				Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

			startActivity(first);
			return;
		}

		if (state == 1 && firstApp.equals(eventPackageName)) {
			state = 2;

			if (!inSplitScreen)
				performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN);

		} else if (state == 2 || (state == 1 && inSplitScreen)) {
			state = 0;

			final Intent second = secondIntent == null ? null : new Intent(secondIntent);

			if (second == null) {
				Toast.makeText(
					getBaseContext(), getString(R.string.toast_app_not_found), Toast.LENGTH_SHORT)
				.show();

				return;
			}

			second.addCategory(Intent.CATEGORY_LAUNCHER);
			second.setFlags(
				Intent.FLAG_ACTIVITY_NEW_TASK |
				Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

			new android.os.Handler().postDelayed(
				new Runnable() {
					@Override
					public void run() {
						if (bluetoothApp == null || !bluetoothApp.equals(runningApp))
							startActivity(second);
					}
				}
			, (inSplitScreen?300:1000));
		}
	}

	@Override
	public void onInterrupt() {
	}

	public void loadPreferences() {
		SharedPreferences preferences = getSharedPreferences(getPackageName()+"_preferences", Context.MODE_PRIVATE);

		musicApp = preferences.getString("launcherMusicApp", "");
		navigationApp = preferences.getString("launcherNavigationApp", "");
		videosApp = preferences.getString("launcherVideosApp", "");

		AppSettings settings = AppSettings.read(preferences.getAll());
		bluetoothApp = settings.bluetooth.enabled && settings.bluetooth.target != null
			? settings.bluetooth.target.packageName : null;
	}

	private boolean inSplitScreenMode(List<AccessibilityWindowInfo> windows) {
		for (AccessibilityWindowInfo window : windows) {
			if (window.getType() == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER) {
				return true;
			}
		}
		return false;
	}
}