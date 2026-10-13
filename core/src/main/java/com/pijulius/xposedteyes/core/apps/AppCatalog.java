package com.pijulius.xposedteyes.core.apps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.pijulius.xposedteyes.core.settings.LaunchTarget;

/**
 * Sorting, de-duplication and search for the app picker. Input is the result of
 * an ACTION_MAIN/CATEGORY_LAUNCHER query, which already excludes services and
 * internal activities without a launcher entry.
 */
public final class AppCatalog {
	private final List<AppEntry> entries;

	public AppCatalog(List<AppEntry> raw) {
		// The same component can be reported twice (e.g. an activity-alias resolving
		// to itself); keep one entry per exact component.
		Map<String, AppEntry> unique = new LinkedHashMap<>();
		for (AppEntry e : raw) {
			if (!unique.containsKey(e.target.flatten()))
				unique.put(e.target.flatten(), e);
		}

		Map<String, Integer> labelCounts = new HashMap<>();
		for (AppEntry e : unique.values()) {
			String k = e.label.toLowerCase(Locale.ROOT);
			Integer c = labelCounts.get(k);
			labelCounts.put(k, c == null ? 1 : c + 1);
		}

		List<AppEntry> list = new ArrayList<>();
		for (AppEntry e : unique.values())
			list.add(e.withAmbiguous(labelCounts.get(e.label.toLowerCase(Locale.ROOT)) > 1));

		Collections.sort(list, new Comparator<AppEntry>() {
			@Override
			public int compare(AppEntry a, AppEntry b) {
				int c = a.label.compareToIgnoreCase(b.label);
				return c != 0 ? c : a.target.flatten().compareTo(b.target.flatten());
			}
		});
		this.entries = Collections.unmodifiableList(list);
	}

	public List<AppEntry> all() {
		return entries;
	}

	/** Case-insensitive match on label or package name. */
	public List<AppEntry> search(String query) {
		if (query == null || query.trim().isEmpty())
			return entries;
		String q = query.trim().toLowerCase(Locale.ROOT);
		List<AppEntry> out = new ArrayList<>();
		for (AppEntry e : entries) {
			if (e.label.toLowerCase(Locale.ROOT).contains(q) ||
				e.target.packageName.toLowerCase(Locale.ROOT).contains(q))
			{
				out.add(e);
			}
		}
		return out;
	}

	/**
	 * Finds the entry for a stored target. A package-only (legacy) target, or one
	 * whose activity no longer exists after an app update, matches the package's
	 * entry only when that package has exactly one launcher entry; otherwise null
	 * so the UI shows "cannot launch" instead of silently picking another activity.
	 */
	public AppEntry find(LaunchTarget target) {
		if (target == null)
			return null;
		AppEntry onlyForPackage = null;
		int packageMatches = 0;
		for (AppEntry e : entries) {
			if (e.target.equals(target))
				return e;
			if (e.target.packageName.equals(target.packageName)) {
				packageMatches++;
				onlyForPackage = e;
			}
		}
		return packageMatches == 1 ? onlyForPackage : null;
	}
}
