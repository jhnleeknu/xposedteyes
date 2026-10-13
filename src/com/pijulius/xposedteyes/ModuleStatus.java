package com.pijulius.xposedteyes;

/**
 * Hooked by {@link XposedTeyes} when LSPosed loads the module into its own
 * process (the module app must be in its own scope for that). A false result
 * therefore means "not confirmed", not "inactive".
 */
public final class ModuleStatus {
	private ModuleStatus() {}

	public static boolean isHookedInOwnProcess() {
		return false;
	}
}
