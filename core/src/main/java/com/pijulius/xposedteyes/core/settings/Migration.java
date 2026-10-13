package com.pijulius.xposedteyes.core.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Moves schema 0 (legacy) preferences to schema 1. Pure and idempotent: a map
 * that already carries a schema version produces no writes.
 */
public final class Migration {
	private Migration() {}

	/** Resolves a package to its main launcher activity class, or null if none. */
	public interface LauncherResolver {
		String launcherActivity(String packageName);
	}

	public static final String NOTICE_STARTUP_MIGRATED = "STARTUP_MIGRATED";
	public static final String NOTICE_BLUETOOTH_MIGRATED = "BLUETOOTH_MIGRATED";
	public static final String NOTICE_ROOT_ASSIST_OFF = "ROOT_ACCESSIBILITY_ASSIST_OFF";
	public static final String NOTICE_UNRESOLVED_PREFIX = "UNRESOLVED:";

	public static final class Result {
		/** Keys to write, in order. Empty when nothing needs migrating. */
		public final Map<String, Object> writes;
		public final List<String> notices;
		/** Whether the caller should keep a backup copy of the old values first. */
		public final boolean needsBackup;

		Result(Map<String, Object> writes, List<String> notices, boolean needsBackup) {
			this.writes = Collections.unmodifiableMap(writes);
			this.notices = Collections.unmodifiableList(notices);
			this.needsBackup = needsBackup;
		}
	}

	public static Result migrate(Map<String, ?> current, LauncherResolver resolver) {
		Map<String, Object> writes = new LinkedHashMap<>();
		List<String> notices = new ArrayList<>();

		if (current.containsKey(Keys.SCHEMA_VERSION))
			return new Result(writes, notices, false);

		if (!AppSettings.isUnmigratedLegacy(current)) {
			// Fresh install: just stamp the schema so later reads skip the legacy path.
			writes.put(Keys.SCHEMA_VERSION, Keys.CURRENT_SCHEMA_VERSION);
			return new Result(writes, notices, false);
		}

		AppSettings legacy = AppSettings.readLegacy(current);
		StartupConfig s = legacy.startup;
		BluetoothLaunchConfig b = legacy.bluetooth;

		if (s.enabled || s.primary != null || s.secondary != null) {
			writes.put(Keys.STARTUP_ENABLED, s.enabled);
			writes.put(Keys.STARTUP_TRIGGER, s.trigger.name());
			writes.put(Keys.STARTUP_MODE, s.mode.name());
			putTarget(writes, notices, Keys.STARTUP_PRIMARY, s.primary, resolver);
			putTarget(writes, notices, Keys.STARTUP_SECONDARY, s.secondary, resolver);
			writes.put(Keys.STARTUP_DELAY_MS, s.delayMs);
			if (s.enabled)
				notices.add(NOTICE_STARTUP_MIGRATED);
		}

		if (b.enabled || b.launchOnDisconnect) {
			writes.put(Keys.BT_LAUNCH_ENABLED, b.enabled);
			putTarget(writes, notices, Keys.BT_LAUNCH_TARGET, b.target, resolver);
			writes.put(Keys.BT_LAUNCH_DELAY_MS, b.delayMs);
			writes.put(Keys.BT_CONFLICT_POLICY, b.conflictPolicy.name());
			writes.put(Keys.BT_LAUNCH_ON_DISCONNECT, b.launchOnDisconnect);
			notices.add(NOTICE_BLUETOOTH_MIGRATED);
		}

		if (s.enabled && s.mode == LaunchMode.SPLIT)
			notices.add(NOTICE_ROOT_ASSIST_OFF);

		writes.put(Keys.SCHEMA_VERSION, Keys.CURRENT_SCHEMA_VERSION);
		if (!notices.isEmpty())
			writes.put(Keys.MIGRATION_NOTICE, join(notices));

		return new Result(writes, notices, true);
	}

	private static void putTarget(Map<String, Object> writes, List<String> notices, String key,
			LaunchTarget target, LauncherResolver resolver) {
		if (target == null)
			return;

		LaunchTarget resolved = target;
		if (target.isPackageOnly()) {
			String cls = resolver == null ? null : resolver.launcherActivity(target.packageName);
			if (cls != null)
				resolved = new LaunchTarget(target.packageName, cls);
			else
				notices.add(NOTICE_UNRESOLVED_PREFIX + target.packageName);
		}
		// Unresolvable targets are kept as package-only values and shown as "cannot launch".
		writes.put(key, resolved.flatten());
	}

	public static String join(List<String> items) {
		StringBuilder sb = new StringBuilder();
		for (String item : items) {
			if (sb.length() > 0)
				sb.append(';');
			sb.append(item);
		}
		return sb.toString();
	}

	public static List<String> splitNotices(String stored) {
		List<String> out = new ArrayList<>();
		if (stored == null || stored.isEmpty())
			return out;
		for (String part : stored.split(";")) {
			if (!part.isEmpty())
				out.add(part);
		}
		return out;
	}
}
