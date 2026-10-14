package com.pijulius.xposedteyes.startup;

import java.util.ArrayList;
import java.util.List;

/** Manual clock scheduler: tasks run only when time is advanced past their due time. */
final class FakeScheduler implements Scheduler {
	final class Entry implements Task {
		final Runnable runnable;
		final long due;
		boolean cancelled;

		Entry(Runnable runnable, long due) {
			this.runnable = runnable;
			this.due = due;
		}

		@Override
		public void cancel() {
			cancelled = true;
		}
	}

	private long now = 0;
	final List<Entry> entries = new ArrayList<>();

	@Override
	public Task schedule(Runnable r, long delayMs) {
		Entry e = new Entry(r, now + delayMs);
		entries.add(e);
		return e;
	}

	@Override
	public long nowMs() {
		return now;
	}

	int pendingCount() {
		int n = 0;
		for (Entry e : entries)
			if (!e.cancelled)
				n++;
		return n;
	}

	/** Advances time, running due tasks in due-time order (including ones they schedule). */
	void advance(long ms) {
		long target = now + ms;
		while (true) {
			Entry next = null;
			for (Entry e : entries)
				if (!e.cancelled && e.due <= target && (next == null || e.due < next.due))
					next = e;
			if (next == null)
				break;
			entries.remove(next);
			now = Math.max(now, next.due);
			next.runnable.run();
		}
		now = target;
	}
}
