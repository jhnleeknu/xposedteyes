package com.pijulius.xposedteyes.startup;

/** Delayed execution on a monotonic clock; injected so timing is testable. */
public interface Scheduler {
	interface Task {
		void cancel();
	}

	/** Runs {@code r} no earlier than {@code delayMs} from now. */
	Task schedule(Runnable r, long delayMs);

	/** Monotonic milliseconds (never jumps with wall clock changes). */
	long nowMs();
}
