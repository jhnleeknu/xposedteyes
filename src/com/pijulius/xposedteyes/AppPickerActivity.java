package com.pijulius.xposedteyes;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

/**
 * Lists every launchable activity (system and user apps) with icon, label and
 * package, searchable. Returns the exact component in "package/class" form.
 * The list never leaves the device.
 */
public class AppPickerActivity extends Activity {
	public static final String EXTRA_COMPONENT = "component";
	public static final String EXTRA_TITLE = "title";

	static final class Item {
		final String label;
		final String component;
		final String packageName;
		final Drawable icon;

		Item(String label, String component, String packageName, Drawable icon) {
			this.label = label;
			this.component = component;
			this.packageName = packageName;
			this.icon = icon;
		}
	}

	private final List<Item> all = new ArrayList<>();
	private final List<Item> shown = new ArrayList<>();
	private Adapter adapter;
	private EditText search;

	@Override
	protected void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		setContentView(R.layout.app_picker);

		String title = getIntent().getStringExtra(EXTRA_TITLE);
		if (title != null)
			setTitle(title);

		search = findViewById(R.id.search);
		ListView list = findViewById(R.id.list);
		adapter = new Adapter();
		list.setAdapter(adapter);
		list.setEmptyView(findViewById(R.id.empty));

		list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Intent result = new Intent();
				result.putExtra(EXTRA_COMPONENT, shown.get(position).component);
				setResult(RESULT_OK, result);
				finish();
			}
		});

		findViewById(R.id.clear_selection).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent result = new Intent();
				result.putExtra(EXTRA_COMPONENT, "");
				setResult(RESULT_OK, result);
				finish();
			}
		});

		search.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}

			@Override
			public void afterTextChanged(Editable s) {
				filter();
			}
		});

		final PackageManager pm = getPackageManager();
		final Handler main = new Handler(Looper.getMainLooper());
		new Thread(new Runnable() {
			@Override
			public void run() {
				final List<Item> items = load(pm);
				main.post(new Runnable() {
					@Override
					public void run() {
						if (isFinishing())
							return;
						all.clear();
						all.addAll(items);
						findViewById(R.id.progress).setVisibility(View.GONE);
						filter();
					}
				});
			}
		}).start();
	}

	static List<Item> load(PackageManager pm) {
		Intent query = new Intent(Intent.ACTION_MAIN);
		query.addCategory(Intent.CATEGORY_LAUNCHER);

		List<Item> items = new ArrayList<>();
		for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
			ActivityInfo ai = ri.activityInfo;
			if (ai == null || !ai.exported)
				continue;
			CharSequence label = ri.loadLabel(pm);
			items.add(new Item(label == null ? ai.packageName : label.toString(),
				ai.packageName + "/" + ai.name, ai.packageName, ri.loadIcon(pm)));
		}

		final Collator collator = Collator.getInstance();
		Collections.sort(items, new Comparator<Item>() {
			@Override
			public int compare(Item a, Item b) {
				int c = collator.compare(a.label, b.label);
				return c != 0 ? c : a.component.compareTo(b.component);
			}
		});
		return items;
	}

	private void filter() {
		String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
		shown.clear();
		for (Item i : all)
			if (q.isEmpty() || i.label.toLowerCase(Locale.ROOT).contains(q) ||
				i.packageName.toLowerCase(Locale.ROOT).contains(q))
			{
				shown.add(i);
			}
		adapter.notifyDataSetChanged();
	}

	private final class Adapter extends BaseAdapter {
		@Override
		public int getCount() {
			return shown.size();
		}

		@Override
		public Object getItem(int position) {
			return shown.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View view, ViewGroup parent) {
			if (view == null)
				view = getLayoutInflater().inflate(R.layout.app_picker_item, parent, false);

			Item item = shown.get(position);
			((ImageView)view.findViewById(R.id.icon)).setImageDrawable(item.icon);
			((TextView)view.findViewById(R.id.label)).setText(item.label);
			// Package (and class for apps with several entry points) tell
			// apps with the same name apart.
			((TextView)view.findViewById(R.id.component)).setText(item.component);
			return view;
		}
	}
}
