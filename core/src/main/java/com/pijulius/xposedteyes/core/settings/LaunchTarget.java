package com.pijulius.xposedteyes.core.settings;

/**
 * A user selected app to launch. Stored as "package/class" (an exact launcher
 * activity) or as a bare "package" for values carried over from the legacy
 * package-name text fields.
 */
public final class LaunchTarget {
	public final String packageName;
	/** Fully qualified activity class, or null for a package-only legacy target. */
	public final String className;

	public LaunchTarget(String packageName, String className) {
		if (packageName == null || packageName.isEmpty())
			throw new IllegalArgumentException("packageName");
		this.packageName = packageName;
		this.className = (className == null || className.isEmpty()) ? null : className;
	}

	/** Parses a stored value, returning null for empty or malformed input. */
	public static LaunchTarget parse(String stored) {
		if (stored == null)
			return null;
		stored = stored.trim();
		if (stored.isEmpty())
			return null;

		String pkg = stored;
		String cls = null;
		int slash = stored.indexOf('/');
		if (slash >= 0) {
			pkg = stored.substring(0, slash);
			cls = stored.substring(slash + 1);
			if (cls.startsWith("."))
				cls = pkg + cls;
		}

		if (!isValidName(pkg) || (cls != null && !isValidName(cls)))
			return null;
		return new LaunchTarget(pkg, cls);
	}

	/** Java/Android package or class name: dot separated identifiers. */
	static boolean isValidName(String name) {
		if (name == null || name.isEmpty() || name.length() > 255)
			return false;
		for (String part : name.split("\\.", -1)) {
			if (part.isEmpty() || !Character.isJavaIdentifierStart(part.charAt(0)))
				return false;
			for (int i = 1; i < part.length(); i++) {
				if (!Character.isJavaIdentifierPart(part.charAt(i)))
					return false;
			}
		}
		return true;
	}

	public boolean isPackageOnly() {
		return className == null;
	}

	public String flatten() {
		return className == null ? packageName : packageName + "/" + className;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof LaunchTarget))
			return false;
		LaunchTarget t = (LaunchTarget)o;
		return packageName.equals(t.packageName) &&
			(className == null ? t.className == null : className.equals(t.className));
	}

	@Override
	public int hashCode() {
		return flatten().hashCode();
	}

	@Override
	public String toString() {
		return flatten();
	}
}
