package com.pijulius.xposedteyes;

import java.io.File;
import java.util.List;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.widget.BaseAdapter;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.pijulius.xposedteyes.settings.Keys;
import com.pijulius.xposedteyes.settings.StartupSettings;
import com.pijulius.xposedteyes.settings.Target;

@SuppressWarnings("deprecation")
public class SettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	private static final int REQUEST_PICK_STARTUP = 1;
	private static final int REQUEST_PICK_BT = 2;

	/** Keys whose change must reach SystemUI (cancel pending launches). */
	private static final String[] RUNTIME_KEYS = {
		Keys.STARTUP_ENABLED, Keys.STARTUP_TRIGGER, Keys.STARTUP_APP, Keys.STARTUP_DELAY_S,
		Keys.STARTUP_RETRY_COUNT, Keys.STARTUP_RETRY_DELAY_S, Keys.STARTUP_FOREGROUND_POLICY,
		Keys.BT_LAUNCH_ENABLED, Keys.BT_LAUNCH_TARGET, Keys.BT_LAUNCH_DELAY_S, Keys.BT_CONFLICT_POLICY,
	};

	private final Handler handler = new Handler(Looper.getMainLooper());
	private final Runnable notifySystemUi = new Runnable() {
		@Override
		public void run() {
			Prefs.notifyChanged(getApplicationContext());
		}
	};
	private String sharedPreferencesPath;
	private SharedPreferences preferences;

	/** Replaced by the module's own hook with the XSharedPreferences path when active. */
	public String getSharedPreferencesPath() {
		return null;
	}

	public void fixSharedPreferencesPermission() {
		if (sharedPreferencesPath == null)
			return;

		AsyncTask.execute(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(500);
				} catch (Throwable t) {}

				File sharedPrefsFile = new File(sharedPreferencesPath);

				if (sharedPrefsFile.exists())
					sharedPrefsFile.setReadable(true, false);
			}
		});
	}

	@Override
	public SharedPreferences getSharedPreferences(String name, int mode) {
		if (Prefs.NAME.equals(name))
			return Prefs.get(getBaseContext());
		return super.getSharedPreferences(name, mode);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		preferences = Prefs.get(this);
		sharedPreferencesPath = getSharedPreferencesPath();
		addPreferencesFromResource(R.xml.settings);
		preferences.registerOnSharedPreferenceChangeListener(this);

		bindActions();
		refresh();
	}

	@Override
	protected void onResume() {
		super.onResume();
		refresh();
	}

	@Override
	protected void onPause() {
		super.onPause();
		// No finish() here (3.0 did): the app picker and the accessibility
		// settings screen return to this activity.
		fixSharedPreferencesPermission();
	}

	@Override
	protected void onDestroy() {
		preferences.unregisterOnSharedPreferenceChangeListener(this);
		super.onDestroy();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		fixSharedPreferencesPermission();

		for (String k : RUNTIME_KEYS) {
			if (k.equals(key)) {
				// After apply() reached the disk. SystemUI also re-reads the
				// settings when a launch fires, so a late notice is harmless.
				handler.removeCallbacks(notifySystemUi);
				handler.postDelayed(notifySystemUi, 700);
				break;
			}
		}
		refresh();
	}

	// -------------------------------------------------------------- actions

	private void onClick(String key, Preference.OnPreferenceClickListener listener) {
		Preference p = findPreference(key);
		if (p != null)
			p.setOnPreferenceClickListener(listener);
	}

	private void bindActions() {
		onClick(Keys.STARTUP_APP, pickApp(REQUEST_PICK_STARTUP, R.string.startup_primary));
		onClick(Keys.BT_LAUNCH_TARGET, pickApp(REQUEST_PICK_BT, R.string.bt_target));

		onClick("startup_launch_now", new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				launchNow();
				return true;
			}
		});

		onClick("a11y_status", new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				showAccessibilityDialog();
				return true;
			}
		});

		onClick("btmic_diagnose", new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				showText(getString(R.string.btmic_diagnose), BtMicDiagnostics.run(SettingsActivity.this));
				return true;
			}
		});
	}

	private Preference.OnPreferenceClickListener pickApp(final int request, final int title) {
		return new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				Intent intent = new Intent(SettingsActivity.this, AppPickerActivity.class);
				intent.putExtra(AppPickerActivity.EXTRA_TITLE, getString(title));
				startActivityForResult(intent, request);
				return true;
			}
		};
	}

	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);
		if (result != RESULT_OK || data == null)
			return;
		if (request != REQUEST_PICK_STARTUP && request != REQUEST_PICK_BT)
			return;

		String component = data.getStringExtra(AppPickerActivity.EXTRA_COMPONENT);
		if (component == null || !(component.isEmpty() || Target.parse(component) != null))
			return;

		preferences.edit()
			.putString(request == REQUEST_PICK_STARTUP ? Keys.STARTUP_APP : Keys.BT_LAUNCH_TARGET, component)
			.apply();
	}

	/** Manual launch from this screen; independent of the automatic launches. */
	private void launchNow() {
		StartupSettings s = StartupSettings.from(preferences.getAll());
		if (s.app == null) {
			toast(R.string.app_not_selected);
			return;
		}

		ResolveInfo ri = resolve(getPackageManager(), s.app);
		if (ri == null) {
			toast(R.string.app_not_launchable);
			return;
		}

		Intent launch = new Intent(Intent.ACTION_MAIN);
		launch.addCategory(Intent.CATEGORY_LAUNCHER);
		launch.setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
		launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
		try {
			startActivity(launch);
		} catch (RuntimeException e) {
			toast(R.string.app_not_launchable);
		}
	}

	/** Same rule as the SystemUI side: exact entry, else the same app's launcher entry, never another app. */
	static ResolveInfo resolve(PackageManager pm, Target target) {
		Intent query = new Intent(Intent.ACTION_MAIN);
		query.addCategory(Intent.CATEGORY_LAUNCHER);
		query.setPackage(target.packageName);
		List<ResolveInfo> list = pm.queryIntentActivities(query, 0);
		ResolveInfo first = null;
		for (ResolveInfo ri : list) {
			ActivityInfo ai = ri.activityInfo;
			if (ai == null || !ai.exported)
				continue;
			if (first == null)
				first = ri;
			if (target.className != null && target.className.equals(ai.name))
				return ri;
		}
		return first;
	}

	/** For the 3.0 split screen shortcuts (Music & Nav, Nav & Videos). */
	private void showAccessibilityDialog() {
		new AlertDialog.Builder(this)
			.setTitle(R.string.a11y_status)
			.setMessage(R.string.a11y_dialog_message)
			.setNegativeButton(android.R.string.cancel, null)
			.setNeutralButton(R.string.a11y_root, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					enableAccessibilityViaRoot();
				}
			})
			.setPositiveButton(R.string.a11y_open_settings, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					try {
						startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
					} catch (ActivityNotFoundException e) {
						toast(R.string.toast_accessibility_service_failed);
					}
				}
			})
			.show();
	}

	private void enableAccessibilityViaRoot() {
		toast(R.string.toast_accessibility_service_request);
		final Context app = getApplicationContext();
		new Thread(new Runnable() {
			@Override
			public void run() {
				final int result = AccessibilityHelper.enableViaRoot();
				handler.post(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(app, result == AccessibilityHelper.RESULT_OK
							? R.string.toast_accessibility_service_succeeded
							: R.string.toast_accessibility_service_failed, Toast.LENGTH_SHORT).show();
						if (!isFinishing())
							refresh();
					}
				});
			}
		}).start();
	}

	// --------------------------------------------------------------- refresh

	private void refresh() {
		if (findPreference(Keys.STARTUP_ENABLED) == null)
			return;

		StartupSettings s = StartupSettings.from(preferences.getAll());
		PackageManager pm = getPackageManager();

		setAppSummary(Keys.STARTUP_APP, s.app, pm);
		setAppSummary(Keys.BT_LAUNCH_TARGET, s.btTarget, pm);

		setSummary(Keys.STARTUP_TRIGGER, listEntry(Keys.STARTUP_TRIGGER));
		setSummary(Keys.STARTUP_RETRY_COUNT, listEntry(Keys.STARTUP_RETRY_COUNT));
		setSummary(Keys.STARTUP_FOREGROUND_POLICY, listEntry(Keys.STARTUP_FOREGROUND_POLICY));
		setSummary(Keys.BT_CONFLICT_POLICY, listEntry(Keys.BT_CONFLICT_POLICY));
		setSummary(Keys.STARTUP_DELAY_S, seconds(s.delayMs));
		setSummary(Keys.STARTUP_RETRY_DELAY_S, seconds(s.retryDelayMs));
		setSummary(Keys.BT_LAUNCH_DELAY_S, seconds(s.btDelayMs));

		String last = StatusStore.last(this, "STARTUP");
		setSummary("startup_last", last == null ? getString(R.string.state_no_report) : last);
		String lastBt = StatusStore.last(this, "BLUETOOTH");
		setSummary("bt_last", lastBt == null ? getString(R.string.state_no_report) : lastBt);

		setSummary("a11y_status", getString(AccessibilityHelper.isEnabled(this) ? R.string.a11y_on : R.string.a11y_off));

		setSummary("screen_startup", s.enabled
			? getString(R.string.state_on) + " · " + listEntry(Keys.STARTUP_TRIGGER) + " · " + appLabel(s.app, pm)
			: getString(R.string.state_off));

		// Nested screens are dialogs; their adapters do not see summary changes.
		for (String screen : new String[] { "screen_startup", "screen_btmic" }) {
			Preference p = findPreference(screen);
			if (p instanceof PreferenceScreen && ((PreferenceScreen)p).getRootAdapter() instanceof BaseAdapter)
				((BaseAdapter)((PreferenceScreen)p).getRootAdapter()).notifyDataSetChanged();
		}
		if (getPreferenceScreen().getRootAdapter() instanceof BaseAdapter)
			((BaseAdapter)getPreferenceScreen().getRootAdapter()).notifyDataSetChanged();
	}

	private String seconds(long ms) {
		int n = (int)(ms / 1000);
		return getResources().getQuantityString(R.plurals.seconds_long, n, n);
	}

	private void setAppSummary(String key, Target target, PackageManager pm) {
		Preference p = findPreference(key);
		if (p == null)
			return;

		if (target == null) {
			p.setSummary(R.string.app_not_selected);
			p.setIcon(null);
			return;
		}

		ResolveInfo ri = resolve(pm, target);
		if (ri == null) {
			p.setSummary(getString(R.string.app_not_launchable_named, target.flatten()));
			p.setIcon(null);
			return;
		}

		p.setSummary(ri.loadLabel(pm) + "\n" + ri.activityInfo.packageName + "/" + ri.activityInfo.name);
		p.setIcon(ri.loadIcon(pm));
	}

	private String appLabel(Target target, PackageManager pm) {
		if (target == null)
			return getString(R.string.app_not_selected);
		ResolveInfo ri = resolve(pm, target);
		return ri == null ? getString(R.string.app_not_launchable) : ri.loadLabel(pm).toString();
	}

	private String listEntry(String key) {
		Preference p = findPreference(key);
		if (p instanceof ListPreference) {
			CharSequence entry = ((ListPreference)p).getEntry();
			return entry == null ? "" : entry.toString();
		}
		return "";
	}

	private void setSummary(String key, CharSequence summary) {
		Preference p = findPreference(key);
		if (p != null)
			p.setSummary(summary);
	}

	private void showText(String title, String text) {
		TextView tv = new TextView(this);
		int pad = (int)(16 * getResources().getDisplayMetrics().density);
		tv.setPadding(pad, pad, pad, pad);
		tv.setTextIsSelectable(true);
		tv.setText(text);
		ScrollView sv = new ScrollView(this);
		sv.addView(tv);

		new AlertDialog.Builder(this)
			.setTitle(title)
			.setView(sv)
			.setPositiveButton(android.R.string.ok, null)
			.show();
	}

	private void toast(int res) {
		Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
	}
}
