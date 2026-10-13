package com.pijulius.xposedteyes.core.apps;

import com.pijulius.xposedteyes.core.settings.LaunchTarget;

/** One launchable activity as shown in the app picker. */
public final class AppEntry {
	public final String label;
	public final LaunchTarget target;
	public final boolean system;
	/** Set by {@link AppCatalog} when another entry has the same label. */
	public final boolean ambiguousLabel;

	public AppEntry(String label, LaunchTarget target, boolean system) {
		this(label, target, system, false);
	}

	AppEntry(String label, LaunchTarget target, boolean system, boolean ambiguousLabel) {
		this.label = label == null || label.trim().isEmpty() ? target.packageName : label.trim();
		this.target = target;
		this.system = system;
		this.ambiguousLabel = ambiguousLabel;
	}

	AppEntry withAmbiguous(boolean ambiguous) {
		return new AppEntry(label, target, system, ambiguous);
	}
}
