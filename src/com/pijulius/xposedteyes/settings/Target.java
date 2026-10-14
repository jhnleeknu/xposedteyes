package com.pijulius.xposedteyes.settings;

import java.util.regex.Pattern;

/**
 * An app the user selected to launch. Either an exact activity component
 * ("pkg/cls", the format of ComponentName.flattenToString()) or, for values
 * migrated from the legacy package name settings, just a package whose
 * launcher activity is resolved at launch time.
 */
public final class Target {
	private static final Pattern PACKAGE =
		Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");
	private static final Pattern CLASS =
		Pattern.compile("\\.?[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");

	public final String packageName;
	/** Fully qualified class name, or null for a package-only target. */
	public final String className;

	private Target(String packageName, String className) {
		this.packageName = packageName;
		this.className = className;
	}

	/**
	 * Parses a stored value. Returns null for empty or malformed input; never
	 * throws. A class starting with '.' is relative to the package, like
	 * ComponentName.unflattenFromString().
	 */
	public static Target parse(String value) {
		if (value == null)
			return null;

		value = value.trim();
		if (value.isEmpty() || value.length() > 512)
			return null;

		int slash = value.indexOf('/');
		if (slash < 0)
			return PACKAGE.matcher(value).matches() ? new Target(value, null) : null;

		String pkg = value.substring(0, slash);
		String cls = value.substring(slash + 1);

		if (!PACKAGE.matcher(pkg).matches() || !CLASS.matcher(cls).matches())
			return null;

		if (cls.startsWith("."))
			cls = pkg + cls;

		return new Target(pkg, cls);
	}

	public static boolean isValid(String value) {
		return value == null || value.trim().isEmpty() || parse(value) != null;
	}

	public boolean isPackageOnly() {
		return className == null;
	}

	public String flatten() {
		return className == null ? packageName : packageName + "/" + className;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Target))
			return false;
		Target t = (Target)o;
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
