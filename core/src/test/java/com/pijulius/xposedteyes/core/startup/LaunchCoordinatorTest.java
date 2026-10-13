package com.pijulius.xposedteyes.core.startup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

import com.pijulius.xposedteyes.core.settings.Keys;
import com.pijulius.xposedteyes.core.startup.Fakes.FakeLauncher;
import com.pijulius.xposedteyes.core.startup.Fakes.MapStore;
import com.pijulius.xposedteyes.core.startup.Fakes.Prefs;
import com.pijulius.xposedteyes.core.startup.Fakes.Sink;
import com.pijulius.xposedteyes.core.startup.Fakes.VirtualScheduler;
import com.pijulius.xposedteyes.core.startup.Ports.Foreground;
import com.pijulius.xposedteyes.core.startup.Ports.RequestResult;

/** Test IDs refer to docs/TEST_PLAN.md (spec section 11.1). */
public class LaunchCoordinatorTest {
	static final String APP = "com.example.music/com.example.music.Main";
	static final String APP2 = "com.example.nav/com.example.nav.Main";
	static final String BT_APP = "com.syu.carlink/com.syu.carlink.Main";

	VirtualScheduler scheduler;
	FakeLauncher launcher;
	MapStore store;
	Prefs prefs;
	Sink sink;
	LaunchCoordinator c;

	@Before
	public void setUp() {
		scheduler = new VirtualScheduler();
		launcher = new FakeLauncher(scheduler);
		store = new MapStore();
		prefs = new Prefs().set(Keys.SCHEMA_VERSION, 1);
		sink = new Sink();
		c = newCoordinator();
	}

	LaunchCoordinator newCoordinator() {
		return new LaunchCoordinator(scheduler, prefs, store, launcher, sink);
	}

	void enableSingle() {
		prefs.set(Keys.STARTUP_ENABLED, true).set(Keys.STARTUP_PRIMARY, APP);
	}

	void enableBluetooth() {
		prefs.set(Keys.BT_LAUNCH_ENABLED, true).set(Keys.BT_LAUNCH_TARGET, BT_APP);
	}

	@Test
	public void tSt01_freshInstallLaunchesNothing() {
		prefs.map.clear(); // nothing written at all
		c.init("boot1", false);
		c.onAccOn();
		c.onBootCompleted();
		c.onBluetooth(true);
		scheduler.advance(120_000);
		assertTrue(launcher.requests.isEmpty());
		assertEquals(LaunchStatus.SKIPPED_DISABLED, sink.lastFinal().status);
	}

	@Test
	public void tSt02_singleAppFiveSecondsAfterBoot() {
		enableSingle();
		c.init("boot1", false);
		scheduler.advance(10_000);
		c.onBootCompleted();
		scheduler.advance(4_999);
		assertTrue("must not fire early", launcher.requests.isEmpty());
		scheduler.advance(1);
		assertEquals(Arrays.asList(15_000L), launcher.requestTimes);
		scheduler.advance(60_000);
		assertEquals(1, launcher.requests.size());
		assertEquals(LaunchStatus.STARTED, sink.lastFinal().status);
	}

	@Test
	public void tSt03_defaultModeIgnoresAccOnAndResume() {
		enableSingle();
		c.init("boot1", false);
		c.onAccOn();
		c.onAccOff();
		c.onAccOn();
		scheduler.advance(60_000);
		assertTrue(launcher.requests.isEmpty());
	}

	@Test
	public void tSt04_repeatedBootBroadcastKeepsOneTimer() {
		enableSingle();
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(2_000);
		c.onBootCompleted();
		c.onBootCompleted();
		assertEquals(1, scheduler.pending());
		scheduler.advance(60_000);
		assertEquals(1, launcher.requests.size());
		assertEquals(Arrays.asList(5_000L), launcher.requestTimes);
	}

	@Test
	public void tSt05_systemUiRestartAfterLaunchDoesNotRelaunch() {
		enableSingle();
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(60_000);
		assertEquals(1, launcher.requests.size());

		// SystemUI process restarts: a new coordinator with the persisted store.
		LaunchCoordinator restarted = newCoordinator();
		restarted.init("boot1", true);
		restarted.onBootCompleted();
		restarted.onAccOn();
		scheduler.advance(60_000);
		assertEquals(1, launcher.requests.size());
	}

	@Test
	public void tSt05b_restartWhileWaitingResumesOnce() {
		enableSingle();
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(2_000);
		// Process dies before the timer fires; its timers die with it.
		VirtualScheduler fresh = new VirtualScheduler();
		fresh.now = scheduler.now;
		launcher = new FakeLauncher(fresh);
		LaunchCoordinator restarted = new LaunchCoordinator(fresh, prefs, store, launcher, sink);
		restarted.init("boot1", true);
		fresh.advance(4_999);
		assertTrue(launcher.requests.isEmpty());
		fresh.advance(60_000);
		assertEquals(1, launcher.requests.size());
	}

	@Test
	public void moduleActivatedOnRunningSystemDoesNotLaunch() {
		enableSingle();
		c.init("boot1", true); // boot already completed, never observed
		c.onBootCompleted();
		scheduler.advance(60_000);
		assertTrue(launcher.requests.isEmpty());
		assertEquals(LaunchStatus.SKIPPED_MISSED_EVENT, sink.records.get(0).status);
	}

	@Test
	public void newBootLaunchesAgain() {
		enableSingle();
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(60_000);
		LaunchCoordinator next = newCoordinator();
		next.init("boot2", false);
		next.onBootCompleted();
		scheduler.advance(60_000);
		assertEquals(2, launcher.requests.size());
	}

	@Test
	public void tSt06_retryableFailureRetriesOnceAfterFiveSeconds() {
		enableSingle();
		launcher.scriptedResults.add(RequestResult.FAILED_RETRYABLE);
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(60_000);
		assertEquals(Arrays.asList(5_000L, 10_000L), launcher.requestTimes);
		assertEquals(LaunchStatus.STARTED, sink.lastFinal().status);
	}

	@Test
	public void tSt07_retryAlsoFailsThenStop() {
		enableSingle();
		launcher.scriptedResults.add(RequestResult.FAILED_RETRYABLE);
		launcher.scriptedResults.add(RequestResult.FAILED_RETRYABLE);
		launcher.scriptedResults.add(RequestResult.FAILED_RETRYABLE);
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(120_000);
		assertEquals(2, launcher.requests.size());
		LaunchRecord last = sink.lastFinal();
		assertEquals(LaunchStatus.REQUEST_FAILED, last.status);
		assertEquals("retries_exhausted", last.reason);
	}

	@Test
	public void retryCountZeroAndThreeAreHonoured() {
		enableSingle();
		prefs.set(Keys.STARTUP_RETRY_COUNT, 3).set(Keys.STARTUP_RETRY_DELAY_MS, 2_000);
		for (int i = 0; i < 5; i++)
			launcher.scriptedResults.add(RequestResult.FAILED_RETRYABLE);
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(120_000);
		assertEquals(Arrays.asList(5_000L, 7_000L, 9_000L, 11_000L), launcher.requestTimes);

		setUp();
		enableSingle();
		prefs.set(Keys.STARTUP_RETRY_COUNT, 0);
		launcher.scriptedResults.add(RequestResult.FAILED_RETRYABLE);
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(120_000);
		assertEquals(1, launcher.requests.size());
	}

	@Test
	public void permanentFailureIsNotRetried() {
		enableSingle();
		launcher.scriptedResults.add(RequestResult.FAILED_PERMANENT);
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(120_000);
		assertEquals(1, launcher.requests.size());
		assertEquals("not_retryable", sink.lastFinal().reason);
	}

	@Test
	public void tSt08_appExitAfterStartIsNotRelaunched() {
		enableSingle();
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(10_000);
		// The app crashes / exits: the coordinator has no relaunch path in NONE mode.
		launcher.foreground = Foreground.HOME;
		scheduler.advance(600_000);
		assertEquals(1, launcher.requests.size());
	}

	@Test
	public void postLaunchPolicyOtherThanNoneStillNeverRelaunches() {
		enableSingle();
		prefs.set(Keys.STARTUP_POST_LAUNCH_POLICY, "LIMITED_KEEP_ALIVE");
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(600_000);
		assertEquals(1, launcher.requests.size());
	}

	@Test
	public void tSt09_otherAppInFrontStillRequestsOnce() {
		enableSingle();
		launcher.foreground = Foreground.OTHER;
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(60_000);
		assertEquals(1, launcher.requests.size());
	}

	@Test
	public void tSt10_targetAlreadyInFrontIsNotLaunchedAgain() {
		enableSingle();
		launcher.foreground = Foreground.TARGET;
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(60_000);
		assertTrue(launcher.requests.isEmpty());
		assertEquals(LaunchStatus.STARTED, sink.lastFinal().status);
		assertEquals("already_foreground", sink.lastFinal().reason);
	}

	@Test
	public void homeOnlyPolicySkipsWhenOtherOrUnknown() {
		enableSingle();
		prefs.set(Keys.STARTUP_FOREGROUND_POLICY, "HOME_ONLY");
		launcher.foreground = Foreground.UNKNOWN;
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(60_000);
		assertTrue(launcher.requests.isEmpty());
		assertEquals(LaunchStatus.SKIPPED_NOT_HOME, sink.lastFinal().status);
		assertEquals("foreground_unknown", sink.lastFinal().reason);

		setUp();
		enableSingle();
		prefs.set(Keys.STARTUP_FOREGROUND_POLICY, "HOME_ONLY");
		launcher.foreground = Foreground.HOME;
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(60_000);
		assertEquals(1, launcher.requests.size());
	}

	@Test
	public void tSt11_disablingWhileWaitingCancelsTimerAndRetries() {
		enableSingle();
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(3_000);
		prefs.set(Keys.STARTUP_ENABLED, false);
		c.onSettingsChanged();
		assertEquals(0, scheduler.pending());
		scheduler.advance(60_000);
		assertTrue(launcher.requests.isEmpty());
		assertEquals(LaunchStatus.CANCELLED, sink.lastFinal().status);

		// Also during the retry wait.
		setUp();
		enableSingle();
		launcher.scriptedResults.add(RequestResult.FAILED_RETRYABLE);
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(6_000);
		prefs.set(Keys.KILL_SWITCH, true);
		c.onSettingsChanged();
		scheduler.advance(60_000);
		assertEquals(1, launcher.requests.size());
	}

	@Test
	public void disableIsHonouredAtFireTimeEvenWithoutNotification() {
		enableSingle();
		c.init("boot1", false);
		c.onBootCompleted();
		prefs.set(Keys.STARTUP_ENABLED, false);
		scheduler.advance(60_000);
		assertTrue(launcher.requests.isEmpty());
	}

	@Test
	public void tSt12_changingTargetOrPolicyCancelsAndDoesNotStartNewTimer() {
		enableSingle();
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(1_000);
		prefs.set(Keys.STARTUP_PRIMARY, APP2);
		c.onSettingsChanged();
		scheduler.advance(60_000);
		assertTrue(launcher.requests.isEmpty());

		// A settings change on an idle coordinator never starts anything.
		c.onSettingsChanged();
		scheduler.advance(60_000);
		assertTrue(launcher.requests.isEmpty());
		assertEquals(0, scheduler.pending());
	}

	@Test
	public void unrelatedSettingChangeKeepsTimer() {
		enableSingle();
		c.init("boot1", false);
		c.onBootCompleted();
		prefs.set("cameraWidth", "1000");
		c.onSettingsChanged();
		scheduler.advance(60_000);
		assertEquals(1, launcher.requests.size());
	}

	@Test
	public void tSt13_invalidTargetIsNotReplacedOrRetried() {
		enableSingle();
		launcher.launchable = false;
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(120_000);
		assertTrue(launcher.requests.isEmpty());
		assertEquals(LaunchStatus.INVALID_TARGET, sink.lastFinal().status);

		setUp();
		prefs.set(Keys.STARTUP_ENABLED, true); // nothing selected
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(120_000);
		assertTrue(launcher.requests.isEmpty());
		assertEquals(LaunchStatus.INVALID_TARGET, sink.lastFinal().status);
		assertEquals("not_selected", sink.lastFinal().reason);
	}

	@Test
	public void tSt14_accOffWhileWaitingIsNotRevivedOnResume() {
		enableSingle();
		c.init("boot1", false);
		c.onAccOn();
		c.onBootCompleted();
		scheduler.advance(2_000);
		c.onAccOff();
		assertEquals(0, scheduler.pending());
		c.onAccOn();
		scheduler.advance(120_000);
		assertTrue(launcher.requests.isEmpty());

		// Nor after a SystemUI restart in the same boot.
		LaunchCoordinator restarted = newCoordinator();
		restarted.init("boot1", true);
		scheduler.advance(120_000);
		assertTrue(launcher.requests.isEmpty());
	}

	@Test
	public void tSt18_bothModeMergesInitialAccOnAndOnlyRealResumesLaunch() {
		enableSingle();
		prefs.set(Keys.STARTUP_TRIGGER, "BOTH");
		c.init("boot1", false);
		c.onAccOn();             // initial ACC ON of this boot
		c.onAccOn();             // same transition under a second action name
		c.onBootCompleted();
		scheduler.advance(60_000);
		assertEquals(1, launcher.requests.size());

		c.onAccOn();             // no OFF in between: duplicate
		scheduler.advance(60_000);
		assertEquals(1, launcher.requests.size());

		c.onAccOff();
		c.onAccOn();             // real resume
		scheduler.advance(60_000);
		assertEquals(2, launcher.requests.size());
	}

	@Test
	public void bothModeBootFirstThenInitialAccOn() {
		enableSingle();
		prefs.set(Keys.STARTUP_TRIGGER, "BOTH");
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(1_000);
		c.onAccOn();
		scheduler.advance(60_000);
		assertEquals(1, launcher.requests.size());
	}

	@Test
	public void accOnlyModeWaitsForBootThenLaunchesOnEveryResume() {
		enableSingle();
		prefs.set(Keys.STARTUP_TRIGGER, "ACC_ON");
		c.init("boot1", false);
		c.onAccOn();
		scheduler.advance(30_000);
		assertTrue("must wait for boot completion", launcher.requests.isEmpty());
		c.onBootCompleted();
		scheduler.advance(60_000);
		assertEquals(1, launcher.requests.size());

		c.onAccOff();
		c.onAccOn();
		scheduler.advance(60_000);
		assertEquals(2, launcher.requests.size());
	}

	@Test
	public void tSt19_bluetoothTransitionsOnly() {
		enableBluetooth();
		c.init("boot1", false);
		c.onBootCompleted();
		c.onBluetooth(true);
		c.onBluetooth(true);  // repeated report
		scheduler.advance(2_000);
		c.onBluetooth(false); // dropped before the 3s delay: cancel
		scheduler.advance(60_000);
		assertTrue(launcher.requests.isEmpty());

		c.onBluetooth(true);  // reconnect is a new event
		scheduler.advance(2_999);
		assertTrue(launcher.requests.isEmpty());
		scheduler.advance(1);
		assertEquals(1, launcher.requests.size());
		assertEquals("com.syu.carlink", launcher.requests.get(0).primary.packageName);
	}

	@Test
	public void bluetoothDisconnectDoesNothingByDefault() {
		enableSingle();
		enableBluetooth();
		c.init("boot1", false);
		c.onBluetooth(true);
		scheduler.advance(60_000);
		c.onBluetooth(false);
		scheduler.advance(60_000);
		assertEquals(1, launcher.requests.size());
	}

	@Test
	public void bluetoothDisconnectRelaunchesStartupWhenEnabled() {
		enableSingle();
		enableBluetooth();
		prefs.set(Keys.BT_LAUNCH_ON_DISCONNECT, true);
		c.init("boot1", false);
		c.onBluetooth(false); // first report after boot: not a disconnect transition
		scheduler.advance(60_000);
		assertTrue(launcher.requests.isEmpty());
		c.onBluetooth(true);
		scheduler.advance(60_000);
		c.onBluetooth(false);
		scheduler.advance(60_000);
		assertEquals(2, launcher.requests.size());
		assertEquals(LaunchCause.BT_DISCONNECT, launcher.requests.get(1).cause);
	}

	@Test
	public void bluetoothSwitchOffCancelsPendingBluetoothLaunch() {
		enableBluetooth();
		c.init("boot1", false);
		c.onBluetooth(true);
		prefs.set(Keys.BT_LAUNCH_ENABLED, false);
		c.onSettingsChanged();
		scheduler.advance(60_000);
		assertTrue(launcher.requests.isEmpty());
	}

	@Test
	public void tSt20_conflictStartupFirst() {
		enableSingle();
		enableBluetooth();
		c.init("boot1", false);
		c.onBootCompleted();     // startup due at 5s
		scheduler.advance(1_000);
		c.onBluetooth(true);     // bt due at 4s, loses to startup
		scheduler.advance(60_000);
		assertEquals(1, launcher.requests.size());
		assertEquals(LaunchCause.BOOT, launcher.requests.get(0).cause);
		assertTrue(sink.finals().contains(LaunchStatus.SKIPPED_CONFLICT));
	}

	@Test
	public void tSt20_conflictBluetoothFirst() {
		enableSingle();
		enableBluetooth();
		prefs.set(Keys.BT_CONFLICT_POLICY, "BLUETOOTH_FIRST");
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(1_000);
		c.onBluetooth(true);
		scheduler.advance(60_000);
		assertEquals(1, launcher.requests.size());
		assertEquals(LaunchCause.BT_CONNECT, launcher.requests.get(0).cause);

		// The cancelled boot session is not resumed after a SystemUI restart.
		LaunchCoordinator restarted = newCoordinator();
		restarted.init("boot1", true);
		scheduler.advance(60_000);
		assertEquals(1, launcher.requests.size());
	}

	@Test
	public void tSt20_conflictLastEvent() {
		enableSingle();
		enableBluetooth();
		prefs.set(Keys.BT_CONFLICT_POLICY, "LAST_EVENT");
		c.init("boot1", false);
		c.onBluetooth(true);
		scheduler.advance(1_000);
		c.onBootCompleted();
		scheduler.advance(60_000);
		assertEquals(1, launcher.requests.size());
		assertEquals(LaunchCause.BOOT, launcher.requests.get(0).cause);
	}

	@Test
	public void tSt21_acceptedButNotVerifiedIsUnconfirmedAndNotRepeated() {
		enableSingle();
		launcher.startedAfterRequest = false;
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(120_000);
		assertEquals(1, launcher.requests.size());
		assertEquals(LaunchStatus.UNCONFIRMED, sink.lastFinal().status);
		LaunchRecord accepted = sink.records.get(sink.records.size() - 2);
		assertEquals(LaunchStatus.REQUEST_ACCEPTED, accepted.status);
		assertFalse(accepted.isFinal);
	}

	@Test
	public void splitModeNeedsBothTargets() {
		prefs.set(Keys.STARTUP_ENABLED, true).set(Keys.STARTUP_MODE, "SPLIT").set(Keys.STARTUP_PRIMARY, APP);
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(60_000);
		assertTrue(launcher.requests.isEmpty());

		setUp();
		prefs.set(Keys.STARTUP_ENABLED, true).set(Keys.STARTUP_MODE, "SPLIT")
			.set(Keys.STARTUP_PRIMARY, APP).set(Keys.STARTUP_SECONDARY, APP2);
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(60_000);
		assertEquals(1, launcher.requests.size());
		assertNotNull(launcher.requests.get(0).secondary);
	}

	@Test
	public void singleModeIgnoresStaleSecondApp() {
		enableSingle();
		prefs.set(Keys.STARTUP_SECONDARY, APP2);
		c.init("boot1", false);
		c.onBootCompleted();
		scheduler.advance(60_000);
		assertEquals(null, launcher.requests.get(0).secondary);
	}

	@Test
	public void legacyUnmigratedSettingsKeepPreviousBehaviour() {
		prefs.map.clear();
		prefs.set(Keys.LEGACY_LAUNCH_ON_STARTUP, true)
			.set(Keys.LEGACY_MUSIC_APP, "com.audials")
			.set(Keys.LEGACY_NAVIGATION_APP, "com.waze");
		c.init("boot1", false);
		c.onAccOn();
		c.onBootCompleted();
		scheduler.advance(60_000);
		assertEquals(1, launcher.requests.size());
		assertEquals(Arrays.asList(1_000L), launcher.requestTimes);
		c.onAccOff();
		c.onAccOn();
		scheduler.advance(60_000);
		assertEquals(2, launcher.requests.size());
	}
}
