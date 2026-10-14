package com.pijulius.xposedteyes.startup;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.pijulius.xposedteyes.settings.Keys;
import com.pijulius.xposedteyes.settings.StartupSettings;
import com.pijulius.xposedteyes.settings.Target;

class FakeEnv implements StartupEnv {
	final Map<String, Object> prefs = new HashMap<>();
	final Set<String> installed = new HashSet<>();
	final List<String> launches = new ArrayList<>();
	final List<Long> launchTimes = new ArrayList<>();
	final List<StatusReport> reports = new ArrayList<>();
	final Deque<LaunchOutcome> outcomes = new ArrayDeque<>();
	LaunchOutcome defaultOutcome = LaunchOutcome.ACCEPTED;
	String foreground = "com.home";
	String home = "com.home";
	/** When true a successful launch brings the target to the front. */
	boolean launchBringsToFront = true;
	FakeScheduler scheduler;
	int loads = 0;

	FakeEnv() {
		installed.add("com.music/com.music.Main");
		installed.add("com.nav/com.nav.Main");
		installed.add("com.carlink/com.carlink.Main");
	}

	FakeEnv enableSingle(String component) {
		prefs.put(Keys.STARTUP_ENABLED, true);
		prefs.put(Keys.STARTUP_APP, component);
		return this;
	}

	@Override
	public StartupSettings loadSettings() {
		loads++;
		return StartupSettings.from(new HashMap<>(prefs));
	}

	@Override
	public Target resolve(Target target) {
		for (String c : installed) {
			Target t = Target.parse(c);
			if (!t.packageName.equals(target.packageName))
				continue;
			// Same package: exact component, or its current launcher activity.
			return t;
		}
		return null;
	}

	private LaunchOutcome next(String what, Target front) {
		launches.add(what);
		launchTimes.add(scheduler == null ? -1 : scheduler.nowMs());
		LaunchOutcome o = outcomes.isEmpty() ? defaultOutcome : outcomes.poll();
		if (o == LaunchOutcome.ACCEPTED && launchBringsToFront)
			foreground = front.packageName;
		return o;
	}

	@Override
	public LaunchOutcome launchSingle(Target target) {
		return next(target.flatten(), target);
	}

	@Override
	public String foregroundPackage() {
		return foreground;
	}

	@Override
	public String homePackage() {
		return home;
	}

	@Override
	public void report(StatusReport report) {
		reports.add(report);
	}

	StartupStatus lastStatus() {
		return reports.isEmpty() ? null : reports.get(reports.size() - 1).status;
	}

	boolean hasStatus(StartupStatus s) {
		for (StatusReport r : reports)
			if (r.status == s)
				return true;
		return false;
	}
}
