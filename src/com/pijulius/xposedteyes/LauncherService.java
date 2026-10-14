package com.pijulius.xposedteyes;

import java.util.List;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import com.pijulius.xposedteyes.settings.Target;
import com.pijulius.xposedteyes.startup.SystemUiStartupHook;

public class LauncherService extends AccessibilityService {
	/** Split launch requested by the startup app manager (via SplitLaunchActivity). */
	public static final String ACTION_SPLIT = "com.pijulius.xposedteyes.SPLIT";

	int state = 0;
	boolean inSplitScreen = false;

	String musicApp;
	String navigationApp;
	String videosApp;
	String bluetoothApp;
	String firstApp;
	String secondApp;
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
			!ACTION_SPLIT.equals(action))
		{
			return result;
		}

		loadPreferences();

		firstApp = musicApp;
		secondApp = navigationApp;

		if ((packageName+".NavigationVideosActivity").equals(action)) {
			firstApp = navigationApp;
			secondApp = videosApp;
		}

		if (ACTION_SPLIT.equals(action)) {
			firstApp = intent.getStringExtra(SystemUiStartupHook.EXTRA_PRIMARY);
			secondApp = intent.getStringExtra(SystemUiStartupHook.EXTRA_SECONDARY);

			if (!Target.isValid(firstApp) || !Target.isValid(secondApp)) {
				firstApp = null;
				secondApp = null;
			}
		}

		if (firstApp == null || secondApp == null || firstApp.isEmpty() || secondApp.isEmpty()) {
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

			Intent first = launchIntent(firstApp);

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

		if (state == 1 && packageOf(firstApp).equals(eventPackageName)) {
			state = 2;

			if (!inSplitScreen)
				performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN);

		} else if (state == 2 || (state == 1 && inSplitScreen)) {
			state = 0;

			final Intent second = launchIntent(secondApp);

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
						if (bluetoothApp == null || !packageOf(bluetoothApp).equals(runningApp))
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
		SharedPreferences preferences = Prefs.get(this);

		musicApp = preferences.getString("launcherMusicApp", "");
		navigationApp = preferences.getString("launcherNavigationApp", "");
		videosApp = preferences.getString("launcherVideosApp", "");
		bluetoothApp = preferences.getString("launcherBluetoothApp", "");
	}

	/** Launch intent for a stored package name or "package/class" component. */
	private Intent launchIntent(String value) {
		Target target = Target.parse(value);
		if (target == null)
			return null;

		if (target.isPackageOnly())
			return getPackageManager().getLaunchIntentForPackage(target.packageName);

		Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.setClassName(target.packageName, target.className);
		if (getPackageManager().resolveActivity(intent, 0) == null)
			return getPackageManager().getLaunchIntentForPackage(target.packageName);
		return intent;
	}

	private static String packageOf(String value) {
		Target target = Target.parse(value);
		return target == null ? "" : target.packageName;
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