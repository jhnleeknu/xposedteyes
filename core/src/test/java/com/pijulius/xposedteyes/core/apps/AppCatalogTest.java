package com.pijulius.xposedteyes.core.apps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import com.pijulius.xposedteyes.core.settings.LaunchTarget;

public class AppCatalogTest {
	static AppEntry e(String label, String component, boolean system) {
		return new AppEntry(label, LaunchTarget.parse(component), system);
	}

	AppCatalog catalog = new AppCatalog(Arrays.asList(
		e("Waze", "com.waze/com.waze.FreeMapAppActivity", false),
		e("Music", "com.syu.music/com.syu.music.Main", true),
		e("Music", "com.audials/com.audials.Main", false),
		e("Settings", "com.android.settings/.Settings", true),
		e("Settings Alias", "com.android.settings/.Settings$WifiAlias", true),
		e("Waze", "com.waze/com.waze.FreeMapAppActivity", false)));

	@Test
	public void tSt15_sortedDeduplicatedWithAmbiguousLabelsMarked() {
		assertEquals(5, catalog.all().size());
		assertEquals("Music", catalog.all().get(0).label);
		assertTrue(catalog.all().get(0).ambiguousLabel);
		assertFalse(catalog.all().get(4).ambiguousLabel);
		// Same label sorted by component so the order is stable.
		assertEquals("com.audials", catalog.all().get(0).target.packageName);
	}

	@Test
	public void tSt15_searchByLabelOrPackage() {
		assertEquals(2, catalog.search("mus").size());
		assertEquals(1, catalog.search("AUDIALS").size());
		assertEquals(5, catalog.search("  ").size());
		assertEquals(0, catalog.search("zzz").size());
	}

	@Test
	public void tSt15_aliasesAreKeptAsDistinctEntryPoints() {
		LaunchTarget alias = LaunchTarget.parse("com.android.settings/.Settings$WifiAlias");
		assertEquals("Settings Alias", catalog.find(alias).label);
		// A package with several entry points is never guessed from the package name.
		assertNull(catalog.find(LaunchTarget.parse("com.android.settings")));
	}

	@Test
	public void legacyPackageOrUpdatedComponentMapsOnlyWhenUnambiguous() {
		assertEquals("Waze", catalog.find(LaunchTarget.parse("com.waze")).label);
		assertEquals("Waze", catalog.find(LaunchTarget.parse("com.waze/com.waze.OldActivity")).label);
		assertNull(catalog.find(LaunchTarget.parse("com.removed.app/.Main")));
	}
}
