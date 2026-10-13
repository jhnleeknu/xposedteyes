package com.pijulius.xposedteyes.startup;

import java.util.List;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.pijulius.xposedteyes.core.settings.LaunchTarget;

/** Resolves stored {@link LaunchTarget}s to launch intents. Used in SystemUI and in the app. */
public final class Targets {
	private Targets() {}

	/**
	 * Returns a launcher intent for the target, or null when it cannot be launched
	 * (not installed, disabled, or ambiguous). An exact component is used when it
	 * still resolves; a package-only value, or a component that vanished after an
	 * app update, falls back to the package's launcher activity only when the
	 * package has exactly one, so another entry point is never guessed.
	 */
	public static Intent launchIntent(Context context, LaunchTarget target) {
		if (target == null)
			return null;
		PackageManager pm = context.getPackageManager();

		try {
			ApplicationInfo ai = pm.getApplicationInfo(target.packageName, 0);
			if (!ai.enabled)
				return null;
		} catch (PackageManager.NameNotFoundException e) {
			return null;
		}

		if (!target.isPackageOnly()) {
			Intent exact = baseIntent().setComponent(new ComponentName(target.packageName, target.className));
			ResolveInfo ri = pm.resolveActivity(exact, 0);
			if (ri != null && ri.activityInfo != null && ri.activityInfo.enabled && ri.activityInfo.exported)
				return exact;
		}

		Intent query = baseIntent().setPackage(target.packageName);
		List<ResolveInfo> list = pm.queryIntentActivities(query, 0);
		if (list == null || list.size() != 1)
			return null;
		ResolveInfo only = list.get(0);
		return baseIntent().setComponent(
			new ComponentName(only.activityInfo.packageName, only.activityInfo.name));
	}

	public static CharSequence label(Context context, LaunchTarget target) {
		PackageManager pm = context.getPackageManager();
		Intent intent = launchIntent(context, target);
		if (intent != null) {
			ResolveInfo ri = pm.resolveActivity(intent, 0);
			if (ri != null)
				return ri.loadLabel(pm);
		}
		return null;
	}

	private static Intent baseIntent() {
		return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
	}
}
