package com.pijulius.xposedteyes.startup;

import java.util.List;

import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.pijulius.xposedteyes.ModulePrefs;
import com.pijulius.xposedteyes.core.settings.LaunchMode;
import com.pijulius.xposedteyes.core.startup.LaunchPlan;
import com.pijulius.xposedteyes.core.startup.Ports;

/**
 * Launch requests from the SystemUI process. A request is a normal activity
 * start: nothing is killed, no overlay is drawn and nothing polls to keep the
 * app in front. Reverse camera and call screens keep the system's priority.
 */
final class SystemUiLauncher implements Ports.Launcher {
	static final String SPLIT_ACTIVITY = ModulePrefs.PACKAGE + ".StartupSplitActivity";

	private final Context context;

	SystemUiLauncher(Context context) {
		this.context = context;
	}

	@Override
	public boolean isLaunchable(LaunchPlan plan) {
		if (Targets.launchIntent(context, plan.primary) == null)
			return false;
		return plan.mode != LaunchMode.SPLIT || Targets.launchIntent(context, plan.secondary) != null;
	}

	@Override
	public Ports.Foreground foreground(LaunchPlan plan) {
		String top = topPackage();
		if (top == null)
			return Ports.Foreground.UNKNOWN;
		// In split mode an app in front does not mean both halves are shown.
		if (plan.mode == LaunchMode.SINGLE && top.equals(plan.primary.packageName))
			return Ports.Foreground.TARGET;
		String home = homePackage();
		if (home == null)
			return Ports.Foreground.UNKNOWN;
		return top.equals(home) ? Ports.Foreground.HOME : Ports.Foreground.OTHER;
	}

	@Override
	public Ports.RequestResult request(LaunchPlan plan) {
		Intent intent;
		if (plan.mode == LaunchMode.SPLIT) {
			intent = new Intent().setComponent(new ComponentName(ModulePrefs.PACKAGE, SPLIT_ACTIVITY));
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
		} else {
			intent = Targets.launchIntent(context, plan.primary);
			if (intent == null)
				return Ports.RequestResult.INVALID_TARGET;
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
		}

		try {
			context.startActivity(intent);
			return Ports.RequestResult.ACCEPTED;
		} catch (ActivityNotFoundException e) {
			return Ports.RequestResult.INVALID_TARGET;
		} catch (SecurityException e) {
			return Ports.RequestResult.FAILED_PERMANENT;
		} catch (RuntimeException e) {
			// e.g. the activity manager not being ready yet.
			return Ports.RequestResult.FAILED_RETRYABLE;
		}
	}

	@Override
	public boolean confirmStarted(LaunchPlan plan) {
		String top = topPackage();
		if (top == null)
			return false;
		if (top.equals(plan.primary.packageName))
			return true;
		return plan.mode == LaunchMode.SPLIT && plan.secondary != null &&
			top.equals(plan.secondary.packageName);
	}

	/** Package of the top task, or null if it cannot be determined. */
	@SuppressWarnings("deprecation")
	private String topPackage() {
		try {
			ActivityManager am = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
			List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
			if (tasks == null || tasks.isEmpty() || tasks.get(0).topActivity == null)
				return null;
			return tasks.get(0).topActivity.getPackageName();
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** The default home app, or null when none is set (chooser) or on error. */
	private String homePackage() {
		try {
			Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
			ResolveInfo ri = context.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
			if (ri == null || ri.activityInfo == null || "android".equals(ri.activityInfo.packageName))
				return null;
			return ri.activityInfo.packageName;
		} catch (RuntimeException e) {
			return null;
		}
	}
}
