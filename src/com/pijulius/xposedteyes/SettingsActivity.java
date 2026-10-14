package com.pijulius.xposedteyes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import android.annotation.SuppressLint;
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
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.widget.BaseAdapter;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.pijulius.xposedteyes.compat.FeatureStatus;
import com.pijulius.xposedteyes.settings.Keys;
import com.pijulius.xposedteyes.settings.SettingsCodec;
import com.pijulius.xposedteyes.settings.SettingsMigration;
import com.pijulius.xposedteyes.settings.StartupSettings;
import com.pijulius.xposedteyes.settings.Target;

// commit() is used where SystemUI is notified right after the write.
@SuppressLint("ApplySharedPref")
@SuppressWarnings("deprecation")
public class SettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	private static final int REQUEST_PICK_PRIMARY = 1;
	private static final int REQUEST_PICK_SECONDARY = 2;
	private static final int REQUEST_PICK_BT = 3;
	private static final int REQUEST_EXPORT_SETTINGS = 10;
	private static final int REQUEST_IMPORT_SETTINGS = 11;
	private static final int REQUEST_EXPORT_LOG = 12;

	/** Keys whose change must reach SystemUI (cancel pending launches). */
	private static final String[] RUNTIME_KEYS = {
		Keys.NEW_FEATURES_DISABLED, Keys.STARTUP_ENABLED, Keys.STARTUP_TRIGGER, Keys.STARTUP_MODE,
		Keys.STARTUP_PRIMARY, Keys.STARTUP_SECONDARY, Keys.STARTUP_DELAY_S, Keys.STARTUP_RETRY_COUNT,
		Keys.STARTUP_RETRY_DELAY_S, Keys.STARTUP_FOREGROUND_POLICY, Keys.STARTUP_POST_LAUNCH_POLICY,
		Keys.STARTUP_RECOVERY_LIMIT, Keys.STARTUP_RECOVERY_DELAY_S, Keys.BT_LAUNCH_ENABLED,
		Keys.BT_LAUNCH_TARGET, Keys.BT_LAUNCH_DELAY_S, Keys.BT_CONFLICT_POLICY, Keys.BT_LAUNCH_ON_DISCONNECT,
	};

	private final Handler handler = new Handler(Looper.getMainLooper());
	private boolean notifyPending;
	private final Runnable notifySystemUi = new Runnable() {
		@Override
		public void run() {
			notifyPending = false;
			Prefs.notifyChanged(getApplicationContext());
		}
	};
	private String sharedPreferencesPath;
	private SharedPreferences preferences;
	private boolean worldReadable;
	private String pendingLogExport;

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
		// Not through this activity: its getSharedPreferences override hides the failure.
		worldReadable = Prefs.isWorldReadable(getApplicationContext());

		migrate();

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
		// No finish() here (3.0 did): the app picker, file pickers and the
		// accessibility settings screen return to this activity.
		fixSharedPreferencesPermission();
	}

	@Override
	protected void onDestroy() {
		// Deliver a pending notification now rather than dropping it.
		if (notifyPending) {
			handler.removeCallbacks(notifySystemUi);
			notifySystemUi.run();
		}
		preferences.unregisterOnSharedPreferenceChangeListener(this);
		super.onDestroy();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		fixSharedPreferencesPermission();

		for (String k : RUNTIME_KEYS) {
			if (k.equals(key)) {
				scheduleNotify();
				break;
			}
		}
		refresh();
	}

	/**
	 * Tells SystemUI to re-read the settings once the write reached the disk
	 * (PreferenceActivity uses apply()). Coalesces bursts of changes. SystemUI
	 * also re-checks the settings when a launch fires, so a lost or early
	 * notification cannot launch with outdated settings.
	 */
	private void scheduleNotify() {
		handler.removeCallbacks(notifySystemUi);
		handler.postDelayed(notifySystemUi, 700);
		notifyPending = true;
	}

	// ------------------------------------------------------------ migration

	private void migrate() {
		Map<String, ?> stored = preferences.getAll();
		if (!SettingsMigration.needsMigration(stored))
			return;

		// Backup of the pre-migration values, private to this app.
		try {
			writeFile(new File(getFilesDir(), "settings_backup_pre_v" + Keys.CURRENT_SCHEMA + ".json"),
				SettingsCodec.export(stored));
		} catch (Exception e) {
			StatusStore.append(this, "MIGRATION backup_failed " + e.getClass().getSimpleName());
		}

		SharedPreferences.Editor editor = preferences.edit();
		for (Map.Entry<String, Object> e : SettingsMigration.changes(stored).entrySet())
			put(editor, e.getKey(), e.getValue());
		// Synchronous on purpose: the hooks must see a complete migration.
		editor.commit();
		StatusStore.append(this, "MIGRATION schema=" + Keys.CURRENT_SCHEMA);
	}

	private static void put(SharedPreferences.Editor editor, String key, Object value) {
		if (value instanceof Boolean)
			editor.putBoolean(key, (Boolean)value);
		else if (value instanceof Integer)
			editor.putInt(key, (Integer)value);
		else if (value != null)
			editor.putString(key, value.toString());
	}

	// -------------------------------------------------------------- actions

	private void onClick(String key, Preference.OnPreferenceClickListener listener) {
		Preference p = findPreference(key);
		if (p != null)
			p.setOnPreferenceClickListener(listener);
	}

	private void bindActions() {
		onClick(Keys.STARTUP_PRIMARY, pickApp(REQUEST_PICK_PRIMARY, R.string.startup_primary));
		onClick(Keys.STARTUP_SECONDARY, pickApp(REQUEST_PICK_SECONDARY, R.string.startup_secondary));
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

		onClick("startup_restore_defaults", new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				confirm(R.string.restore_defaults, R.string.startup_restore_defaults_confirm, new Runnable() {
					@Override
					public void run() {
						restoreStartupDefaults(preferences.edit()).commit();
						scheduleNotify();
						recreate();
					}
				});
				return true;
			}
		});

		onClick("all_restore_defaults", new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				confirm(R.string.all_restore_defaults, R.string.all_restore_defaults_confirm, new Runnable() {
					@Override
					public void run() {
						restoreStartupDefaults(preferences.edit())
							.putBoolean(Keys.NEW_FEATURES_DISABLED, false)
							.putBoolean(Keys.DIAG_CARLINK_VIDEO, false)
							.putBoolean(Keys.DIAG_AUDIO_PATH, false)
							.commit();
						scheduleNotify();
						recreate();
					}
				});
				return true;
			}
		});

		onClick("compat_status", new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				showText(getString(R.string.compat_status), compatibilityReport());
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

		onClick("log_view", new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				showLog();
				return true;
			}
		});

		onClick("log_clear", new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				StatusStore.log(SettingsActivity.this).clear();
				toast(R.string.log_cleared);
				return true;
			}
		});

		onClick("settings_export", new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				createDocument("application/json", "xposedteyes-settings.json", REQUEST_EXPORT_SETTINGS);
				return true;
			}
		});

		onClick("settings_import", new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
				intent.addCategory(Intent.CATEGORY_OPENABLE);
				intent.setType("*/*");
				startForResult(intent, REQUEST_IMPORT_SETTINGS);
				return true;
			}
		});

		onClick("migration_notice", new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				preferences.edit().remove(Keys.MIGRATION_NOTICE).apply();
				return true;
			}
		});
	}

	private static SharedPreferences.Editor restoreStartupDefaults(SharedPreferences.Editor editor) {
		for (Map.Entry<String, Object> e : StartupSettings.defaults().entrySet())
			put(editor, e.getKey(), e.getValue());
		return editor;
	}

	private Preference.OnPreferenceClickListener pickApp(final int request, final int title) {
		return new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				Intent intent = new Intent(SettingsActivity.this, AppPickerActivity.class);
				intent.putExtra(AppPickerActivity.EXTRA_TITLE, getString(title));
				startForResult(intent, request);
				return true;
			}
		};
	}

	private void startForResult(Intent intent, int request) {
		try {
			startActivityForResult(intent, request);
		} catch (ActivityNotFoundException e) {
			toast(R.string.no_app_for_action);
		}
	}

	private void createDocument(String type, String name, int request) {
		Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType(type);
		intent.putExtra(Intent.EXTRA_TITLE, name);
		startForResult(intent, request);
	}

	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);
		if (result != RESULT_OK || data == null)
			return;

		switch (request) {
		case REQUEST_PICK_PRIMARY:
		case REQUEST_PICK_SECONDARY:
		case REQUEST_PICK_BT:
			String component = data.getStringExtra(AppPickerActivity.EXTRA_COMPONENT);
			if (component == null || !Target.isValid(component))
				return;
			String key = request == REQUEST_PICK_PRIMARY ? Keys.STARTUP_PRIMARY
				: request == REQUEST_PICK_SECONDARY ? Keys.STARTUP_SECONDARY : Keys.BT_LAUNCH_TARGET;
			preferences.edit().putString(key, component).apply();
			break;

		case REQUEST_EXPORT_SETTINGS:
			try {
				writeUri(data.getData(), SettingsCodec.export(preferences.getAll()));
				toast(R.string.settings_exported);
			} catch (Exception e) {
				toast(R.string.file_error);
			}
			break;

		case REQUEST_IMPORT_SETTINGS:
			importSettings(data.getData());
			break;

		case REQUEST_EXPORT_LOG:
			try {
				if (pendingLogExport != null)
					writeUri(data.getData(), pendingLogExport);
				toast(R.string.log_exported);
			} catch (Exception e) {
				toast(R.string.file_error);
			}
			pendingLogExport = null;
			break;
		}
	}

	private void importSettings(Uri uri) {
		String text;
		try {
			text = readUri(uri, 256 * 1024);
		} catch (Exception e) {
			toast(R.string.file_error);
			return;
		}

		final SettingsCodec.ImportResult r = SettingsCodec.parse(text);
		if (!r.ok()) {
			showText(getString(R.string.settings_import), getString(R.string.settings_import_rejected, r.error));
			return;
		}

		new AlertDialog.Builder(this)
			.setTitle(R.string.settings_import)
			.setMessage(getString(R.string.settings_import_confirm, r.values.size() - 1, r.skippedUnknown))
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					SharedPreferences.Editor editor = preferences.edit();
					for (Map.Entry<String, Object> e : r.values.entrySet())
						put(editor, e.getKey(), e.getValue());
					editor.remove(Keys.MIGRATION_NOTICE);
					editor.commit();
					scheduleNotify();
					StatusStore.append(SettingsActivity.this, "SETTINGS imported keys=" + (r.values.size() - 1));
					recreate();
				}
			})
			.show();
	}

	/** Manual launch from this screen; independent of the automatic sessions. */
	private void launchNow() {
		StartupSettings s = StartupSettings.from(preferences.getAll());

		if (s.primary == null) {
			toast(R.string.app_not_selected);
			return;
		}

		if (s.mode == StartupSettings.Mode.SPLIT) {
			if (s.secondary == null) {
				toast(R.string.app_not_selected);
				return;
			}
			Intent intent = new Intent(this, SplitLaunchActivity.class);
			intent.putExtra(com.pijulius.xposedteyes.startup.SystemUiStartupHook.EXTRA_PRIMARY, s.primary.flatten());
			intent.putExtra(com.pijulius.xposedteyes.startup.SystemUiStartupHook.EXTRA_SECONDARY, s.secondary.flatten());
			startActivity(intent);
			return;
		}

		Intent launch = launchIntent(getPackageManager(), s.primary);
		if (launch == null) {
			toast(R.string.app_not_launchable);
			return;
		}
		launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
		try {
			startActivity(launch);
		} catch (RuntimeException e) {
			toast(R.string.app_not_launchable);
		}
	}

	static Intent launchIntent(PackageManager pm, Target target) {
		ResolveInfo ri = resolve(pm, target);
		if (ri == null)
			return null;
		Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.addCategory(Intent.CATEGORY_LAUNCHER);
		intent.setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
		return intent;
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
					startForResult(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), 0);
				}
			})
			.show();
	}

	private void enableAccessibilityViaRoot() {
		toast(R.string.toast_accessibility_service_request);
		final Context app = getApplicationContext();
		final Handler main = new Handler(Looper.getMainLooper());
		new Thread(new Runnable() {
			@Override
			public void run() {
				final int result = AccessibilityHelper.enableViaRoot();
				main.post(new Runnable() {
					@Override
					public void run() {
						StatusStore.append(app, "A11Y root_enable result=" + result);
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

	private void showLog() {
		String log = StatusStore.log(this).readAll();
		if (log.isEmpty())
			log = getString(R.string.log_empty);
		final String content = log;
		String shownText = content.length() > 20000 ? content.substring(content.length() - 20000) : content;

		new AlertDialog.Builder(this)
			.setTitle(R.string.log_view)
			.setView(scrollingText(shownText))
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(R.string.log_export, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					// The user has seen exactly what will be written.
					pendingLogExport = content;
					createDocument("text/plain", "xposedteyes-log.txt", REQUEST_EXPORT_LOG);
				}
			})
			.show();
	}

	private String compatibilityReport() {
		StringBuilder sb = new StringBuilder();
		sb.append(getString(R.string.compat_prefs_sharing)).append(": ")
			.append(getString(worldReadable ? R.string.state_active : R.string.state_inactive)).append('\n');
		String last = StatusStore.last(this, "STARTUP");
		sb.append(getString(R.string.compat_systemui_hook)).append(": ")
			.append(last == null ? getString(R.string.state_no_report) : last).append("\n\n");

		for (FeatureStatus.Row row : FeatureStatus.ROWS) {
			sb.append("• ").append(getString(row.name)).append('\n')
				.append("   ").append(getString(R.string.compat_code)).append(": ").append(row.code)
				.append(" · ").append(getString(R.string.compat_device)).append(": ").append(row.device)
				.append('\n');
		}
		sb.append('\n').append(getString(R.string.compat_footer));
		return sb.toString();
	}

	// --------------------------------------------------------------- refresh

	private void refresh() {
		if (findPreference(Keys.STARTUP_ENABLED) == null)
			return;

		StartupSettings s = StartupSettings.from(preferences.getAll());
		PackageManager pm = getPackageManager();

		String notice = preferences.getString(Keys.MIGRATION_NOTICE, null);
		Preference noticePref = findPreference("migration_notice");
		PreferenceGroup newCat = (PreferenceGroup)findPreference("cat_new");
		if (noticePref != null) {
			if (notice == null || notice.isEmpty())
				newCat.removePreference(noticePref);
			else
				noticePref.setSummary(migrationNoticeText(notice));
		}

		setAppSummary(Keys.STARTUP_PRIMARY, s.primary, pm);
		setAppSummary(Keys.STARTUP_SECONDARY, s.secondary, pm);
		setAppSummary(Keys.BT_LAUNCH_TARGET, s.btTarget, pm);

		boolean split = s.mode == StartupSettings.Mode.SPLIT;
		setEnabled(Keys.STARTUP_SECONDARY, split);

		// No reliable exit signal yet: post-launch recovery stays off (docs/COMPATIBILITY.md).
		Preference post = findPreference(Keys.STARTUP_POST_LAUNCH_POLICY);
		post.setEnabled(false);
		post.setSummary(R.string.startup_post_launch_unsupported);
		setEnabled(Keys.STARTUP_RECOVERY_LIMIT, false);
		setEnabled(Keys.STARTUP_RECOVERY_DELAY_S, false);

		setSummary(Keys.STARTUP_TRIGGER, listEntry(Keys.STARTUP_TRIGGER));
		setSummary(Keys.STARTUP_MODE, listEntry(Keys.STARTUP_MODE));
		setSummary(Keys.STARTUP_RETRY_COUNT, listEntry(Keys.STARTUP_RETRY_COUNT));
		setSummary(Keys.STARTUP_FOREGROUND_POLICY, listEntry(Keys.STARTUP_FOREGROUND_POLICY));
		setSummary(Keys.BT_CONFLICT_POLICY, listEntry(Keys.BT_CONFLICT_POLICY));
		setSummary(Keys.STARTUP_DELAY_S, seconds((int)(s.delayMs / 1000)));
		setSummary(Keys.STARTUP_RETRY_DELAY_S, seconds((int)(s.retryDelayMs / 1000)));
		setSummary(Keys.STARTUP_RECOVERY_LIMIT, String.valueOf(s.recoveryLimit));
		setSummary(Keys.STARTUP_RECOVERY_DELAY_S, seconds((int)(s.recoveryDelayMs / 1000)));
		setSummary(Keys.BT_LAUNCH_DELAY_S, seconds((int)(s.btDelayMs / 1000)));

		String last = StatusStore.last(this, "STARTUP");
		setSummary("startup_last", last == null ? getString(R.string.state_no_report) : last);
		String lastBt = StatusStore.last(this, "BLUETOOTH");
		setSummary("bt_last", lastBt == null ? getString(R.string.state_no_report) : lastBt);

		boolean a11y = AccessibilityHelper.isEnabled(this);
		setSummary("a11y_status", getString(a11y ? R.string.a11y_on : R.string.a11y_off));

		String screenSummary;
		if (s.newFeaturesDisabled)
			screenSummary = getString(R.string.new_features_disabled);
		else if (!s.enabled)
			screenSummary = getString(R.string.state_off);
		else
			screenSummary = getString(R.string.state_on) + " · " + listEntry(Keys.STARTUP_TRIGGER) + " · " +
				appLabel(s.primary, pm);
		setSummary("screen_startup", screenSummary);

		if (!s.invalidKeys.isEmpty())
			setSummary("startup_last", getString(R.string.invalid_values, s.invalidKeys.toString()));

		// Nested screens are dialogs; their adapters do not see summary changes.
		for (String screen : new String[] { "screen_startup", "screen_carplay", "screen_btmic", "screen_advanced" }) {
			Preference p = findPreference(screen);
			if (p instanceof PreferenceScreen && ((PreferenceScreen)p).getRootAdapter() instanceof BaseAdapter)
				((BaseAdapter)((PreferenceScreen)p).getRootAdapter()).notifyDataSetChanged();
		}
		if (getPreferenceScreen().getRootAdapter() instanceof BaseAdapter)
			((BaseAdapter)getPreferenceScreen().getRootAdapter()).notifyDataSetChanged();
	}

	private String seconds(int n) {
		return getResources().getQuantityString(R.plurals.seconds_long, n, n);
	}

	private String migrationNoticeText(String notice) {
		StringBuilder sb = new StringBuilder();
		for (String part : notice.split(",")) {
			int res = SettingsMigration.NOTICE_STARTUP.equals(part) ? R.string.migration_startup
				: SettingsMigration.NOTICE_STARTUP_INVALID.equals(part) ? R.string.migration_startup_invalid
				: SettingsMigration.NOTICE_BLUETOOTH.equals(part) ? R.string.migration_bluetooth : 0;
			if (res == 0)
				continue;
			if (sb.length() > 0)
				sb.append(' ');
			sb.append(getString(res));
		}
		return sb.append(' ').append(getString(R.string.migration_dismiss)).toString();
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

	private void setEnabled(String key, boolean enabled) {
		Preference p = findPreference(key);
		if (p != null)
			p.setEnabled(enabled);
	}

	// ---------------------------------------------------------------- utils

	private ScrollView scrollingText(String text) {
		TextView tv = new TextView(this);
		int pad = (int)(16 * getResources().getDisplayMetrics().density);
		tv.setPadding(pad, pad, pad, pad);
		tv.setTextIsSelectable(true);
		tv.setText(text);
		ScrollView sv = new ScrollView(this);
		sv.addView(tv);
		return sv;
	}

	private void showText(String title, String text) {
		new AlertDialog.Builder(this)
			.setTitle(title)
			.setView(scrollingText(text))
			.setPositiveButton(android.R.string.ok, null)
			.show();
	}

	private void confirm(int title, int message, final Runnable action) {
		new AlertDialog.Builder(this)
			.setTitle(title)
			.setMessage(message)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					action.run();
				}
			})
			.show();
	}

	private void toast(int res) {
		Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
	}

	private void writeUri(Uri uri, String text) throws Exception {
		OutputStream os = getContentResolver().openOutputStream(uri, "wt");
		if (os == null)
			throw new IllegalStateException("no stream");
		try {
			os.write(text.getBytes(StandardCharsets.UTF_8));
		} finally {
			os.close();
		}
	}

	private static void writeFile(File file, String text) throws Exception {
		OutputStream os = new FileOutputStream(file);
		try {
			os.write(text.getBytes(StandardCharsets.UTF_8));
		} finally {
			os.close();
		}
	}

	private String readUri(Uri uri, int maxBytes) throws Exception {
		InputStream is = getContentResolver().openInputStream(uri);
		if (is == null)
			throw new IllegalStateException("no stream");
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			int n;
			while ((n = is.read(buf)) > 0) {
				out.write(buf, 0, n);
				if (out.size() > maxBytes)
					throw new IllegalStateException("too large");
			}
			return new String(out.toByteArray(), StandardCharsets.UTF_8);
		} finally {
			is.close();
		}
	}
}
