package com.pijulius.xposedteyes.core.settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Manipulates the colon separated Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
 * value without dropping services other apps or the user enabled.
 */
public final class AccessibilityServices {
	private AccessibilityServices() {}

	public static List<String> split(String value) {
		List<String> out = new ArrayList<>();
		if (value == null)
			return out;
		value = value.trim();
		if (value.isEmpty() || "null".equals(value))
			return out;
		for (String part : value.split(":")) {
			part = part.trim();
			if (!part.isEmpty() && !out.contains(part))
				out.add(part);
		}
		return out;
	}

	public static boolean contains(String value, String service) {
		for (String s : split(value)) {
			if (sameService(s, service))
				return true;
		}
		return false;
	}

	/** Returns the existing list with {@code service} appended if it was missing. */
	public static String withService(String value, String service) {
		List<String> list = split(value);
		for (String s : list) {
			if (sameService(s, service))
				return join(list);
		}
		list.add(service);
		return join(list);
	}

	/** Compares "pkg/.Cls" and "pkg/pkg.Cls" forms as equal. */
	static boolean sameService(String a, String b) {
		return expand(a).equalsIgnoreCase(expand(b));
	}

	private static String expand(String component) {
		int slash = component.indexOf('/');
		if (slash < 0)
			return component;
		String pkg = component.substring(0, slash);
		String cls = component.substring(slash + 1);
		if (cls.startsWith("."))
			cls = pkg + cls;
		return pkg + "/" + cls;
	}

	private static String join(List<String> list) {
		StringBuilder sb = new StringBuilder();
		for (String s : list) {
			if (sb.length() > 0)
				sb.append(':');
			sb.append(s);
		}
		return sb.toString();
	}
}
