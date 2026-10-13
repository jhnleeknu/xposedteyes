package com.pijulius.xposedteyes;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.provider.Settings;
import android.widget.Toast;

import com.pijulius.xposedteyes.core.compat.Feature;
import com.pijulius.xposedteyes.core.settings.AppSettings;
import com.pijulius.xposedteyes.core.settings.Keys;
import com.pijulius.xposedteyes.core.settings.LaunchMode;
import com.pijulius.xposedteyes.core.settings.Migration;
import com.pijulius.xposedteyes.core.settings.PostLaunchPolicy;
import com.pijulius.xposedteyes.core.settings.SettingsTransfer;
import com.pijulius.xposedteyes.diag.DiagnosticsActivity;
import com.pijulius.xposedteyes.diag.LocalLog;
import com.pijulius.xposedteyes.diag.StatusReceiver;
import com.pijulius.xposedteyes.settings.AppPickerPreference;
import com.pijulius.xposedteyes.settings.IntPickerPreference;
import com.pijulius.xposedteyes.settings.SettingsSync;
import com.pijulius.xposedteyes.startup.Targets;

@SuppressWarnings("deprecation")
public class SettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	private static final int REQUEST_EXPORT = 1;
	private static final int REQUEST_IMPORT = 2;
	private static final long NOTIFY_DEBOUNCE_MS = 700;

	private String sharedPreferencesPath;
	private SharedPreferences preferences;
	private final Handler handler = new Handler();
	private String pendingExport;

	private final Runnable notifySystemUi = new Runnable() {
		@Override
		public void run() {
			SettingsSync.notifySystemUi(SettingsActivity.this);
		}
	};

	/** Replaced by the module's own hook with the XSharedPreferences file path. */
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

	@SuppressLint("WorldReadableFiles")
	@Override
	public SharedPreferences getSharedPreferences(String name, int mode) {
		// Only the module settings file is redirected; backups and status files stay private.
		if (!ModulePrefs.FILE.equals(name))
			return super.getSharedPreferences(name, mode);
		try {
			// World readable so LSPosed can expose the file to XSharedPreferences in the hooks.
			return super.getSharedPreferences(ModulePrefs.FILE, MODE_WORLD_READABLE);
		} catch (SecurityException e) {
			// MODE_WORLD_READABLE is only allowed while LSPosed is active.
			return super.getSharedPreferences(ModulePrefs.FILE, MODE_PRIVATE);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		preferences = getSharedPreferences(ModulePrefs.FILE, MODE_PRIVATE);
		sharedPreferencesPath = getSharedPreferencesPath();

		SettingsSync.migrateIfNeeded(this, preferences);
		fixSharedPreferencesPermission();

		addPreferencesFromResource(R.xml.settings);
		preferences.registerOnSharedPreferenceChangeListener(this);

		onClick("startupLaunchNow", new Runnable() { public void run() { launchNow(); } });
		onClick("startupRestoreDefaults", new Runnable() { public void run() { confirmRestore(false); } });
		onClick("restoreAllDefaults", new Runnable() { public void run() { confirmRestore(true); } });
		onClick("exportSettings", new Runnable() { public void run() { exportSettings(); } });
		onClick("importSettings", new Runnable() { public void run() { importSettings(); } });
		onClick("accessibilityStatus", new Runnable() { public void run() { openAccessibilitySettings(); } });
		onClick("migrationNoticeView", new Runnable() { public void run() { dismissMigrationNotice(); } });
		onClick("compatibility", diagnostics(DiagnosticsActivity.MODE_COMPAT));
		onClick("localLog", diagnostics(DiagnosticsActivity.MODE_LOG));
		onClick("carlinkInfo", diagnostics(DiagnosticsActivity.MODE_CARLINK));
		onClick("btmicDiagnostics", diagnostics(DiagnosticsActivity.MODE_BTMIC));

		Preference root = findPreference("rootAccessibilityAssist");
		root.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if (Boolean.TRUE.equals(newValue)) {
					confirmRootAssist((CheckBoxPreference)preference);
					return false;
				}
				return true;
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		refresh();
	}

	@Override
	protected void onPause() {
		super.onPause();
		// No finish() here: the app picker and file pickers return to this screen.
		fixSharedPreferencesPermission();
	}

	@Override
	protected void onDestroy() {
		preferences.unregisterOnSharedPreferenceChangeListener(this);
		handler.removeCallbacks(notifySystemUi);
		super.onDestroy();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		fixSharedPreferencesPermission();

		// Debounced so SystemUI reads the file after it has been written.
		handler.removeCallbacks(notifySystemUi);
		handler.postDelayed(notifySystemUi, NOTIFY_DEBOUNCE_MS);
		refresh();
	}

	/** Updates every summary and enabled state that depends on other values. */
	private void refresh() {
		AppSettings s = AppSettings.read(preferences.getAll());

		refreshPickers(getPreferenceScreen());

		boolean split = s.startup.mode == LaunchMode.SPLIT;
		Preference secondary = findPreference(Keys.STARTUP_SECONDARY);
		secondary.setEnabled(split);
		if (!split)
			secondary.setSummary(R.string.pref_startup_secondary_single);

		// Post launch recovery has no verified exit/crash signal yet (BLOCKED).
		boolean recovery = Feature.POST_LAUNCH_RECOVERY.controllable();
		findPreference(Keys.STARTUP_POST_LAUNCH_POLICY).setEnabled(recovery);
		findPreference(Keys.STARTUP_POST_LAUNCH_POLICY).setSummary(recovery ? "%s"
			: getString(R.string.pref_post_launch_blocked));
		findPreference(Keys.STARTUP_RECOVERY_LIMIT).setEnabled(recovery && s.startup.postLaunchPolicy != null &&
			s.startup.postLaunchPolicy != PostLaunchPolicy.NONE);
		findPreference(Keys.STARTUP_RECOVERY_DELAY_MS).setEnabled(findPreference(Keys.STARTUP_RECOVERY_LIMIT).isEnabled());

		boolean btOn = s.bluetooth.enabled;
		findPreference(Keys.BT_LAUNCH_TARGET).setEnabled(btOn);
		findPreference(Keys.BT_LAUNCH_DELAY_MS).setEnabled(btOn || s.bluetooth.launchOnDisconnect);

		boolean a11y = RootAccessibility.isEnabled(this);
		findPreference("accessibilityStatus").setSummary(getString(a11y
			? R.string.pref_accessibility_enabled : R.string.pref_accessibility_disabled) +
			(split ? "" : "\n" + getString(R.string.pref_accessibility_single_note)));

		Preference notice = findPreference("migrationNoticeView");
		String stored = preferences.getString(Keys.MIGRATION_NOTICE, "");
		PreferenceGroup startup = (PreferenceGroup)findPreference("screenStartup");
		if (stored.isEmpty()) {
			if (notice != null)
				startup.removePreference(notice);
		} else if (notice != null) {
			notice.setSummary(describeNotices(Migration.splitNotices(stored)));
		}

		if (s.killSwitch)
			findPreference("screenStartup").setSummary(R.string.pref_kill_switch_active);
		else
			findPreference("screenStartup").setSummary(R.string.pref_startup_screen_sum);

		findPreference("startupLastResult").setSummary(lastResult());
	}

	private void refreshPickers(PreferenceGroup group) {
		for (int i = 0; i < group.getPreferenceCount(); i++) {
			Preference p = group.getPreference(i);
			if (p instanceof AppPickerPreference)
				((AppPickerPreference)p).refreshSummary();
			else if (p instanceof IntPickerPreference)
				((IntPickerPreference)p).updateSummary();
			else if (p instanceof PreferenceGroup)
				refreshPickers((PreferenceGroup)p);
		}
	}

	private String describeNotices(List<String> notices) {
		StringBuilder sb = new StringBuilder();
		for (String n : notices) {
			if (sb.length() > 0)
				sb.append('\n');
			if (Migration.NOTICE_STARTUP_MIGRATED.equals(n))
				sb.append(getString(R.string.notice_startup_migrated));
			else if (Migration.NOTICE_BLUETOOTH_MIGRATED.equals(n))
				sb.append(getString(R.string.notice_bluetooth_migrated));
			else if (Migration.NOTICE_ROOT_ASSIST_OFF.equals(n))
				sb.append(getString(R.string.notice_root_assist_off));
			else if (n.startsWith(Migration.NOTICE_UNRESOLVED_PREFIX))
				sb.append(getString(R.string.notice_unresolved, n.substring(Migration.NOTICE_UNRESOLVED_PREFIX.length())));
			else
				sb.append(n);
		}
		sb.append('\n').append(getString(R.string.notice_tap_to_dismiss));
		return sb.toString();
	}

	private void dismissMigrationNotice() {
		preferences.edit().remove(Keys.MIGRATION_NOTICE).apply();
	}

	private String lastResult() {
		SharedPreferences state = getSharedPreferences(StatusReceiver.STATE_FILE, MODE_PRIVATE);
		long time = state.getLong("time", 0);
		if (time == 0)
			return getString(R.string.pref_last_result_none);
		String reason = state.getString("reason", null);
		return getString(R.string.pref_last_result_value,
			DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(new Date(time)),
			state.getString("cause", "?"), state.getString("status", "?"),
			reason == null ? "" : " (" + reason + ")");
	}

	private Runnable diagnostics(final String mode) {
		return new Runnable() {
			@Override
			public void run() {
				startActivity(new Intent(SettingsActivity.this, DiagnosticsActivity.class)
					.putExtra(DiagnosticsActivity.EXTRA_MODE, mode));
			}
		};
	}

	private void onClick(String key, final Runnable action) {
		Preference p = findPreference(key);
		if (p == null)
			return;
		p.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				action.run();
				return true;
			}
		});
	}

	/** Explicit, user initiated launch; independent of the automatic sessions. */
	private void launchNow() {
		AppSettings s = AppSettings.read(preferences.getAll());
		if (s.startup.mode == LaunchMode.SPLIT) {
			startActivity(new Intent(this, StartupSplitActivity.class));
			LocalLog.add(this, "manual: split launch requested");
			return;
		}

		Intent intent = Targets.launchIntent(this, s.startup.primary);
		if (intent == null) {
			Toast.makeText(this, R.string.toast_target_not_launchable, Toast.LENGTH_LONG).show();
			LocalLog.add(this, "manual: INVALID_TARGET");
			return;
		}
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
		try {
			startActivity(intent);
			LocalLog.add(this, "manual: REQUEST_ACCEPTED target=" + intent.getComponent().flattenToShortString());
		} catch (ActivityNotFoundException | SecurityException e) {
			Toast.makeText(this, R.string.toast_target_not_launchable, Toast.LENGTH_LONG).show();
			LocalLog.add(this, "manual: REQUEST_FAILED " + e.getClass().getSimpleName());
		}
	}

	private void openAccessibilitySettings() {
		try {
			startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
		} catch (ActivityNotFoundException e) {
			Toast.makeText(this, R.string.toast_no_accessibility_settings, Toast.LENGTH_LONG).show();
		}
	}

	private void confirmRootAssist(final CheckBoxPreference preference) {
		new AlertDialog.Builder(this)
			.setTitle(R.string.pref_root_assist)
			.setMessage(R.string.dialog_root_assist)
			.setPositiveButton(R.string.dialog_agree, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					preference.setChecked(true);
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show();
	}

	private void confirmRestore(final boolean all) {
		new AlertDialog.Builder(this)
			.setTitle(all ? R.string.pref_restore_all : R.string.pref_restore_startup)
			.setMessage(all ? R.string.dialog_restore_all : R.string.dialog_restore_startup)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					if (all)
						SettingsSync.restoreAllDefaults(SettingsActivity.this, preferences);
					else
						SettingsSync.restoreLaunchDefaults(SettingsActivity.this, preferences);
					// Recreate so every widget shows the restored value.
					recreate();
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show();
	}

	private void exportSettings() {
		final String json;
		try {
			json = SettingsSync.exportJson(preferences);
		} catch (Exception e) {
			Toast.makeText(this, R.string.toast_export_failed, Toast.LENGTH_LONG).show();
			return;
		}

		// Preview first: the user sees exactly what will be written.
		new AlertDialog.Builder(this)
			.setTitle(R.string.pref_export)
			.setMessage(json)
			.setPositiveButton(R.string.dialog_save, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					pendingExport = json;
					Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
						.addCategory(Intent.CATEGORY_OPENABLE)
						.setType("application/json")
						.putExtra(Intent.EXTRA_TITLE, "xposedteyes-settings.json");
					try {
						startActivityForResult(intent, REQUEST_EXPORT);
					} catch (ActivityNotFoundException e) {
						Toast.makeText(SettingsActivity.this, R.string.toast_no_file_picker, Toast.LENGTH_LONG).show();
					}
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show();
	}

	private void importSettings() {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
			.addCategory(Intent.CATEGORY_OPENABLE)
			.setType("*/*");
		try {
			startActivityForResult(intent, REQUEST_IMPORT);
		} catch (ActivityNotFoundException e) {
			Toast.makeText(this, R.string.toast_no_file_picker, Toast.LENGTH_LONG).show();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode != RESULT_OK || data == null || data.getData() == null)
			return;

		if (requestCode == REQUEST_EXPORT && pendingExport != null) {
			boolean ok = write(this, data.getData(), pendingExport);
			pendingExport = null;
			Toast.makeText(this, ok ? R.string.toast_export_done : R.string.toast_export_failed, Toast.LENGTH_LONG).show();
		} else if (requestCode == REQUEST_IMPORT) {
			String json = read(data.getData());
			final SettingsTransfer.ImportResult result =
				json == null ? SettingsSync.parseImport("") : SettingsSync.parseImport(json);
			if (!result.usable() || result.accepted.isEmpty()) {
				new AlertDialog.Builder(this)
					.setTitle(R.string.pref_import)
					.setMessage(R.string.dialog_import_invalid)
					.setPositiveButton(android.R.string.ok, null)
					.show();
				return;
			}
			String message = getString(R.string.dialog_import_summary, result.accepted.size(), result.errors.size()) +
				(result.errors.isEmpty() ? "" : "\n" + getString(R.string.dialog_import_rejected, result.errors.toString()));
			new AlertDialog.Builder(this)
				.setTitle(R.string.pref_import)
				.setMessage(message)
				.setPositiveButton(R.string.dialog_apply, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						SettingsSync.applyImport(SettingsActivity.this, preferences, result.accepted);
						recreate();
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
		}
	}

	/** Writes text to a user chosen document. */
	public static boolean write(Context context, Uri uri, String text) {
		try (OutputStream out = context.getContentResolver().openOutputStream(uri, "wt")) {
			if (out == null)
				return false;
			out.write(text.getBytes(StandardCharsets.UTF_8));
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private String read(Uri uri) {
		try (InputStream in = getContentResolver().openInputStream(uri)) {
			if (in == null)
				return null;
			BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			char[] buf = new char[4096];
			int n;
			while ((n = r.read(buf)) > 0) {
				sb.append(buf, 0, n);
				if (sb.length() > 256 * 1024)
					return null; // far larger than any valid backup
			}
			return sb.toString();
		} catch (Exception e) {
			return null;
		}
	}
}
