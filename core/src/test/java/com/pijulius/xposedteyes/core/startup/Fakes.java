package com.pijulius.xposedteyes.core.startup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.pijulius.xposedteyes.core.settings.AppSettings;
import com.pijulius.xposedteyes.core.startup.Ports.Cancellable;
import com.pijulius.xposedteyes.core.startup.Ports.Foreground;
import com.pijulius.xposedteyes.core.startup.Ports.Launcher;
import com.pijulius.xposedteyes.core.startup.Ports.RequestResult;
import com.pijulius.xposedteyes.core.startup.Ports.RuntimeStore;
import com.pijulius.xposedteyes.core.startup.Ports.Scheduler;
import com.pijulius.xposedteyes.core.startup.Ports.SettingsSource;
import com.pijulius.xposedteyes.core.startup.Ports.StatusSink;

/** Deterministic test doubles: virtual clock, scripted launcher, in-memory stores. */
final class Fakes {
	private Fakes() {}

	static final class VirtualScheduler implements Scheduler {
		private static final class Task {
			final long at;
			final long seq;
			final Runnable runnable;
			boolean cancelled;

			Task(long at, long seq, Runnable runnable) {
				this.at = at;
				this.seq = seq;
				this.runnable = runnable;
			}
		}

		long now;
		private long seq;
		private final List<Task> tasks = new ArrayList<>();

		@Override
		public Cancellable schedule(Runnable task, long delayMs) {
			final Task t = new Task(now + delayMs, seq++, task);
			tasks.add(t);
			return new Cancellable() {
				@Override
				public void cancel() {
					t.cancelled = true;
				}
			};
		}

		/** Runs every task due up to now + ms, in time order. */
		void advance(long ms) {
			long target = now + ms;
			while (true) {
				Task next = null;
				for (Task t : tasks) {
					if (!t.cancelled && t.at <= target && (next == null || t.at < next.at ||
						(t.at == next.at && t.seq < next.seq)))
					{
						next = t;
					}
				}
				if (next == null)
					break;
				tasks.remove(next);
				now = next.at;
				next.runnable.run();
			}
			now = target;
			for (Iterator<Task> it = tasks.iterator(); it.hasNext();) {
				if (it.next().cancelled)
					it.remove();
			}
		}

		int pending() {
			int n = 0;
			for (Task t : tasks) {
				if (!t.cancelled)
					n++;
			}
			return n;
		}
	}

	static final class FakeLauncher implements Launcher {
		final List<Long> requestTimes = new ArrayList<>();
		final List<LaunchPlan> requests = new ArrayList<>();
		final List<RequestResult> scriptedResults = new ArrayList<>();
		final VirtualScheduler clock;
		Foreground foreground = Foreground.OTHER;
		boolean launchable = true;
		boolean startedAfterRequest = true;

		FakeLauncher(VirtualScheduler clock) {
			this.clock = clock;
		}

		@Override
		public boolean isLaunchable(LaunchPlan plan) {
			return launchable;
		}

		@Override
		public Foreground foreground(LaunchPlan plan) {
			return foreground;
		}

		@Override
		public RequestResult request(LaunchPlan plan) {
			requests.add(plan);
			requestTimes.add(clock.now);
			return scriptedResults.isEmpty() ? RequestResult.ACCEPTED : scriptedResults.remove(0);
		}

		@Override
		public boolean confirmStarted(LaunchPlan plan) {
			return startedAfterRequest;
		}
	}

	static final class MapStore implements RuntimeStore {
		final Map<String, String> map = new HashMap<>();

		@Override
		public String get(String key) {
			return map.get(key);
		}

		@Override
		public void put(String key, String value) {
			map.put(key, value);
		}
	}

	static final class Prefs implements SettingsSource {
		final Map<String, Object> map = new LinkedHashMap<>();

		Prefs set(String key, Object value) {
			map.put(key, value);
			return this;
		}

		@Override
		public AppSettings load() {
			return AppSettings.read(map);
		}
	}

	static final class Sink implements StatusSink {
		final List<LaunchRecord> records = new ArrayList<>();

		@Override
		public void report(LaunchRecord record) {
			records.add(record);
		}

		LaunchRecord lastFinal() {
			for (int i = records.size() - 1; i >= 0; i--) {
				if (records.get(i).isFinal)
					return records.get(i);
			}
			return null;
		}

		List<LaunchStatus> finals() {
			List<LaunchStatus> out = new ArrayList<>();
			for (LaunchRecord r : records) {
				if (r.isFinal)
					out.add(r.status);
			}
			return out;
		}
	}
}
