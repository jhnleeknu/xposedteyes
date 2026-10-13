package com.pijulius.xposedteyes.settings;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;

import com.pijulius.xposedteyes.BuildConfig;
import com.pijulius.xposedteyes.ModulePrefs;
import com.pijulius.xposedteyes.core.settings.Keys;
import com.pijulius.xposedteyes.core.settings.Migration;
import com.pijulius.xposedteyes.core.settings.SettingsTransfer;
import com.pijulius.xposedteyes.diag.LocalLog;

/** Migration, backup/restore and SystemUI notification for the settings UI. */
public final class SettingsSync {
	private SettingsSync() {}

	/** Private copy of the values before a migration, an import or a reset. */
	static final String BACKUP_FILE = "settings_backup";

	/** Tells SystemUI to re-check pending work. It never starts anything new. */
	public static void notifySystemUi(Context context) {
		Intent intent = new Intent(ModulePrefs.ACTION_SETTINGS_CHANGED);
		intent.setPackage("com.android.systemui");
		context.sendBroadcast(intent);
	}

	public static Migration.Result migrateIfNeeded(final Context context, SharedPreferences prefs) {
		Map<String, ?> all = prefs.getAll();
		Migration.Result result = Migration.migrate(all, new Migration.LauncherResolver() {
			@Override
			public String launcherActivity(String packageName) {
				Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName);
				List<ResolveInfo> list = context.getPackageManager().queryIntentActivities(q, 0);
				return list != null && list.size() == 1 ? list.get(0).activityInfo.name : null;
			}
		});
		if (result.writes.isEmpty())
			return result;

		if (result.needsBackup)
			backup(context, all);

		SharedPreferences.Editor editor = prefs.edit();
		putAll(editor, result.writes);
		editor.commit();
		LocalLog.add(context, "settings: migrated to schema " + Keys.CURRENT_SCHEMA_VERSION +
			" notices=" + Migration.join(result.notices));
		return result;
	}

	static void backup(Context context, Map<String, ?> values) {
		SharedPreferences.Editor b = context.getSharedPreferences(BACKUP_FILE, Context.MODE_PRIVATE).edit().clear();
		putAll(b, values);
		b.commit();
	}

	static void putAll(SharedPreferences.Editor editor, Map<String, ?> values) {
		for (Map.Entry<String, ?> e : values.entrySet()) {
			Object v = e.getValue();
			if (v instanceof Boolean)
				editor.putBoolean(e.getKey(), (Boolean)v);
			else if (v instanceof Integer)
				editor.putInt(e.getKey(), (Integer)v);
			else if (v instanceof Long)
				editor.putLong(e.getKey(), (Long)v);
			else if (v instanceof Float)
				editor.putFloat(e.getKey(), (Float)v);
			else if (v != null)
				editor.putString(e.getKey(), String.valueOf(v));
		}
	}

	/** Startup app defaults: removes every startup / bluetooth launch key. */
	public static void restoreLaunchDefaults(Context context, SharedPreferences prefs) {
		backup(context, prefs.getAll());
		SharedPreferences.Editor e = prefs.edit();
		for (String key : Keys.LAUNCH_FEATURE_KEYS)
			e.remove(key);
		e.commit();
		notifySystemUi(context);
	}

	public static void restoreAllDefaults(Context context, SharedPreferences prefs) {
		backup(context, prefs.getAll());
		prefs.edit().clear().putInt(Keys.SCHEMA_VERSION, Keys.CURRENT_SCHEMA_VERSION).commit();
		notifySystemUi(context);
	}

	/** Settings backup as JSON. Contains settings only: no device or account identifiers. */
	public static String exportJson(SharedPreferences prefs) throws JSONException {
		JSONObject root = new JSONObject();
		root.put("format", SettingsTransfer.FORMAT);
		root.put("schemaVersion", Keys.CURRENT_SCHEMA_VERSION);
		root.put("appVersion", BuildConfig.VERSION_NAME);
		JSONObject values = new JSONObject();
		for (Map.Entry<String, Object> e : SettingsTransfer.exportable(prefs.getAll()).entrySet())
			values.put(e.getKey(), e.getValue());
		root.put("values", values);
		return root.toString(2);
	}

	public static SettingsTransfer.ImportResult parseImport(String json) {
		try {
			JSONObject root = new JSONObject(json);
			JSONObject values = root.optJSONObject("values");
			Map<String, Object> map = null;
			if (values != null) {
				map = new HashMap<>();
				Iterator<String> keys = values.keys();
				while (keys.hasNext()) {
					String k = keys.next();
					map.put(k, values.get(k));
				}
			}
			Integer schema = root.has("schemaVersion") && root.get("schemaVersion") instanceof Integer
				? root.getInt("schemaVersion") : null;
			return SettingsTransfer.validateImport(root.optString("format", null), schema, map);
		} catch (JSONException e) {
			return SettingsTransfer.validateImport(null, null, null);
		}
	}

	public static void applyImport(Context context, SharedPreferences prefs, Map<String, Object> accepted) {
		backup(context, prefs.getAll());
		SharedPreferences.Editor e = prefs.edit();
		putAll(e, accepted);
		e.putInt(Keys.SCHEMA_VERSION, Keys.CURRENT_SCHEMA_VERSION);
		e.commit();
		notifySystemUi(context);
		LocalLog.add(context, "settings: imported " + accepted.size() + " values");
	}
}
