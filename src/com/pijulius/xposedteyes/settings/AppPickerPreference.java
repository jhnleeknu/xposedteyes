package com.pijulius.xposedteyes.settings;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.preference.Preference;
import android.util.AttributeSet;

import com.pijulius.xposedteyes.R;
import com.pijulius.xposedteyes.core.settings.LaunchTarget;
import com.pijulius.xposedteyes.startup.Targets;

/**
 * Shows the selected app by name and opens {@link AppPickerActivity}; no package
 * name typing. The value is written by the picker as "package/activity" (or just
 * the package for the legacy split screen shortcut keys).
 */
public class AppPickerPreference extends Preference {
	private final boolean storePackageOnly;

	public AppPickerPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AppPickerPreference);
		storePackageOnly = a.getBoolean(R.styleable.AppPickerPreference_storePackageOnly, false);
		a.recycle();
		setPersistent(true);
	}

	@Override
	protected void onClick() {
		Intent intent = new Intent(getContext(), AppPickerActivity.class);
		intent.putExtra(AppPickerActivity.EXTRA_KEY, getKey());
		intent.putExtra(AppPickerActivity.EXTRA_TITLE, String.valueOf(getTitle()));
		intent.putExtra(AppPickerActivity.EXTRA_PACKAGE_ONLY, storePackageOnly);
		getContext().startActivity(intent);
	}

	/** Re-reads the stored value and shows label / "cannot launch" state. */
	public void refreshSummary() {
		String stored = getPersistedString("");
		LaunchTarget target = LaunchTarget.parse(stored);
		Context c = getContext();

		if (target == null) {
			setSummary(stored.isEmpty() ? c.getString(R.string.app_not_selected)
				: c.getString(R.string.app_invalid_value, stored));
			return;
		}

		CharSequence label = Targets.label(c, target);
		if (label == null)
			setSummary(c.getString(R.string.app_cannot_launch, target.packageName));
		else
			setSummary(c.getString(R.string.app_selected, label, target.packageName));
	}

	@Override
	protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
		refreshSummary();
	}
}
