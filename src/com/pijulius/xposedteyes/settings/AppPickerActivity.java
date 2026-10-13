package com.pijulius.xposedteyes.settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.pijulius.xposedteyes.ModulePrefs;
import com.pijulius.xposedteyes.R;
import com.pijulius.xposedteyes.core.apps.AppCatalog;
import com.pijulius.xposedteyes.core.apps.AppEntry;
import com.pijulius.xposedteyes.core.settings.LaunchTarget;

/**
 * Lists launchable system and user apps (ACTION_MAIN + CATEGORY_LAUNCHER, which
 * leaves out services and internal activities) with icon, name and search. The
 * list is built on the device and never leaves it.
 */
public class AppPickerActivity extends Activity {
	public static final String EXTRA_KEY = "key";
	public static final String EXTRA_TITLE = "title";
	public static final String EXTRA_PACKAGE_ONLY = "packageOnly";

	private String key;
	private boolean packageOnly;
	private AppCatalog catalog;
	private List<AppEntry> shown = new ArrayList<>();
	private final Map<String, ResolveInfo> infos = new HashMap<>();
	private final Map<String, Drawable> icons = new HashMap<>();
	private Adapter adapter;
	private TextView emptyView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.app_picker);

		key = getIntent().getStringExtra(EXTRA_KEY);
		packageOnly = getIntent().getBooleanExtra(EXTRA_PACKAGE_ONLY, false);
		if (key == null) {
			finish();
			return;
		}
		String title = getIntent().getStringExtra(EXTRA_TITLE);
		if (title != null)
			setTitle(title);

		ListView list = findViewById(R.id.appList);
		emptyView = findViewById(R.id.appListEmpty);
		list.setEmptyView(emptyView);
		adapter = new Adapter();
		list.setAdapter(adapter);
		list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				select(position == 0 ? null : shown.get(position - 1).target);
			}
		});

		EditText search = findViewById(R.id.appSearch);
		search.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}

			@Override
			public void afterTextChanged(Editable s) {
				if (catalog == null)
					return;
				shown = catalog.search(s.toString());
				adapter.notifyDataSetChanged();
			}
		});

		emptyView.setText(R.string.app_picker_loading);
		new Thread(new Runnable() {
			@Override
			public void run() {
				final AppCatalog loaded = load();
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						catalog = loaded;
						shown = loaded.all();
						emptyView.setText(R.string.app_picker_empty);
						adapter.notifyDataSetChanged();
					}
				});
			}
		}, "XposedTeyesAppPicker").start();
	}

	private AppCatalog load() {
		PackageManager pm = getPackageManager();
		Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
		List<ResolveInfo> results = pm.queryIntentActivities(query, 0);
		List<AppEntry> entries = new ArrayList<>();
		for (ResolveInfo ri : results) {
			if (ri.activityInfo == null || !ri.activityInfo.enabled)
				continue;
			LaunchTarget t = new LaunchTarget(ri.activityInfo.packageName, ri.activityInfo.name);
			boolean system = (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
			CharSequence label = ri.loadLabel(pm);
			entries.add(new AppEntry(label == null ? null : label.toString(), t, system));
			synchronized (infos) {
				infos.put(t.flatten(), ri);
			}
		}
		return new AppCatalog(entries);
	}

	private void select(LaunchTarget target) {
		SharedPreferences prefs = ModulePrefs.get(this);
		String value = target == null ? "" : (packageOnly ? target.packageName : target.flatten());
		prefs.edit().putString(key, value).apply();
		finish();
	}

	private final class Adapter extends BaseAdapter {
		@Override
		public int getCount() {
			return catalog == null ? 0 : shown.size() + 1;
		}

		@Override
		public Object getItem(int position) {
			return position == 0 ? null : shown.get(position - 1);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View v = convertView != null ? convertView
				: getLayoutInflater().inflate(R.layout.app_picker_item, parent, false);
			ImageView icon = v.findViewById(R.id.appIcon);
			TextView label = v.findViewById(R.id.appLabel);
			TextView detail = v.findViewById(R.id.appDetail);

			if (position == 0) {
				icon.setImageDrawable(null);
				label.setText(R.string.app_picker_none);
				detail.setText(R.string.app_picker_none_sum);
				return v;
			}

			AppEntry e = shown.get(position - 1);
			label.setText(e.label);
			String kind = getString(e.system ? R.string.app_kind_system : R.string.app_kind_user);
			// Same name twice: the package (and activity for aliases) tells them apart.
			detail.setText(e.ambiguousLabel ? kind + " · " + e.target.flatten() : kind + " · " + e.target.packageName);
			icon.setImageDrawable(icon(e.target));
			return v;
		}

		private Drawable icon(LaunchTarget t) {
			String k = t.flatten();
			Drawable d = icons.get(k);
			if (d == null) {
				ResolveInfo ri;
				synchronized (infos) {
					ri = infos.get(k);
				}
				if (ri != null) {
					d = ri.loadIcon(getPackageManager());
					icons.put(k, d);
				}
			}
			return d;
		}
	}
}
