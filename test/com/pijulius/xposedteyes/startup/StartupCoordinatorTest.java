package com.pijulius.xposedteyes.startup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.pijulius.xposedteyes.settings.Keys;
import com.pijulius.xposedteyes.startup.StartupCoordinator.ExitKind;

/** Test IDs refer to docs/TEST_PLAN.md (T-ST-xx). */
public class StartupCoordinatorTest {
	private static final String MUSIC = "com.music/com.music.Main";
	private static final String NAV = "com.nav/com.nav.Main";
	private static final String CARLINK = "com.carlink/com.carlink.Main";

	private FakeEnv env;
	private FakeScheduler scheduler;
	private StateStore store;
	private StartupCoordinator c;

	@Before
	public void setUp() {
		env = new FakeEnv();
		scheduler = new FakeScheduler();
		env.scheduler = scheduler;
		store = new StateStore.Memory();
		c = new StartupCoordinator(env, scheduler, store);
	}

	private void boot() {
		c.onHookInit("boot-1", false);
		c.onBootCompleted();
	}

	/** Simulates a SystemUI restart: new coordinator, same persisted store. */
	private void restartProcess() {
		c = new StartupCoordinator(env, scheduler, store);
	}

	@Test
	public void ST01_disabledByDefault_neverLaunches() {
		env.prefs.put(Keys.STARTUP_PRIMARY, MUSIC);
		boot();
		c.onAccOff();
		c.onAccOn();
		scheduler.advance(120_000);
		assertTrue(env.launches.isEmpty());
		assertTrue(env.hasStatus(StartupStatus.SKIPPED_DISABLED));
	}

	@Test
	public void ST02_coldBoot_launchesOnceAfterFiveSeconds() {
		env.enableSingle(MUSIC);
		boot();
		scheduler.advance(4_999);
		assertTrue("never earlier than the delay", env.launches.isEmpty());
		scheduler.advance(1);
		assertEquals(1, env.launches.size());
		assertEquals(Long.valueOf(5_000), env.launchTimes.get(0));
		scheduler.advance(StartupCoordinator.VERIFY_DELAY_MS);
		assertEquals(StartupStatus.STARTED, env.lastStatus());
		scheduler.advance(600_000);
		assertEquals(1, env.launches.size());
	}

	@Test
	public void ST03_defaultMode_ignoresAccOnAndResume() {
		env.enableSingle(MUSIC);
		c.onHookInit("boot-1", false);
		c.onAccOn();
		scheduler.advance(60_000);
		assertTrue(env.launches.isEmpty());
		c.onAccOff();
		c.onAccOn();
		scheduler.advance(60_000);
		assertTrue(env.launches.isEmpty());
	}

	@Test
	public void ST04_repeatedBootBroadcast_keepsSingleTimer() {
		env.enableSingle(MUSIC);
		boot();
		scheduler.advance(2_000);
		c.onBootCompleted();
		c.onBootCompleted();
		assertEquals(1, scheduler.pendingCount());
		scheduler.advance(3_000);
		assertEquals(1, env.launches.size());
		assertEquals(Long.valueOf(5_000), env.launchTimes.get(0));
	}

	@Test
	public void ST05_systemUiRestartAfterHandling_doesNotLaunchAgain() {
		env.enableSingle(MUSIC);
		boot();
		scheduler.advance(10_000);
		assertEquals(1, env.launches.size());

		restartProcess();
		c.onHookInit("boot-1", true);
		c.onBootCompleted(); // even a re-delivered broadcast
		scheduler.advance(60_000);
		assertEquals(1, env.launches.size());
	}

	@Test
	public void ST05b_restartBeforeBootCompletedHandled_catchesUpOnce() {
		env.enableSingle(MUSIC);
		c.onHookInit("boot-1", false);
		restartProcess(); // crashed before BOOT_COMPLETED arrived
		c.onHookInit("boot-1", true);
		scheduler.advance(5_000);
		assertEquals(1, env.launches.size());
	}

	@Test
	public void ST05c_moduleEnabledOnAlreadyBootedDevice_doesNotLaunch() {
		env.enableSingle(MUSIC);
		c.onHookInit("boot-1", true);
		c.onBootCompleted();
		c.onAccOn();
		scheduler.advance(60_000);
		assertTrue(env.launches.isEmpty());
		assertTrue(env.hasStatus(StartupStatus.SKIPPED_LATE_REGISTRATION));
	}

	@Test
	public void ST05d_unknownBootId_noCatchUp() {
		env.enableSingle(MUSIC);
		c.onHookInit(null, true);
		scheduler.advance(60_000);
		assertTrue(env.launches.isEmpty());
	}

	@Test
	public void ST05e_newBoot_resetsState() {
		env.enableSingle(MUSIC);
		boot();
		scheduler.advance(10_000);
		restartProcess();
		env.foreground = "com.home";
		c.onHookInit("boot-2", false);
		c.onBootCompleted();
		scheduler.advance(10_000);
		assertEquals(2, env.launches.size());
	}

	@Test
	public void ST06_retryableFailure_retriesOnceAfterFiveSeconds() {
		env.enableSingle(MUSIC);
		env.outcomes.add(LaunchOutcome.FAILED_RETRYABLE);
		boot();
		scheduler.advance(5_000);
		assertEquals(1, env.launches.size());
		assertEquals(StartupStatus.RETRY_SCHEDULED, env.lastStatus());
		scheduler.advance(4_999);
		assertEquals(1, env.launches.size());
		scheduler.advance(1);
		assertEquals(2, env.launches.size());
		assertEquals(Long.valueOf(10_000), env.launchTimes.get(1));
	}

	@Test
	public void ST07_retryAlsoFails_finalFailure() {
		env.enableSingle(MUSIC);
		env.defaultOutcome = LaunchOutcome.FAILED_RETRYABLE;
		boot();
		scheduler.advance(120_000);
		assertEquals("initial + 1 retry", 2, env.launches.size());
		assertEquals(StartupStatus.REQUEST_FAILED, env.lastStatus());
	}

	@Test
	public void ST07b_retryCountZeroAndThree() {
		env.enableSingle(MUSIC);
		env.defaultOutcome = LaunchOutcome.FAILED_RETRYABLE;
		env.prefs.put(Keys.STARTUP_RETRY_COUNT, "3");
		env.prefs.put(Keys.STARTUP_RETRY_DELAY_S, 2);
		boot();
		scheduler.advance(120_000);
		assertEquals(4, env.launches.size());

		setUp();
		env.enableSingle(MUSIC);
		env.defaultOutcome = LaunchOutcome.FAILED_RETRYABLE;
		env.prefs.put(Keys.STARTUP_RETRY_COUNT, "0");
		boot();
		scheduler.advance(120_000);
		assertEquals(1, env.launches.size());
	}

	@Test
	public void ST07c_permanentFailure_notRetried() {
		env.enableSingle(MUSIC);
		env.defaultOutcome = LaunchOutcome.FAILED_PERMANENT;
		boot();
		scheduler.advance(120_000);
		assertEquals(1, env.launches.size());
		assertEquals(StartupStatus.REQUEST_FAILED, env.lastStatus());
	}

	@Test
	public void ST08_exitAfterStart_defaultPolicyNeverRelaunches() {
		env.enableSingle(MUSIC);
		env.exitSignal = true;
		boot();
		scheduler.advance(10_000);
		c.onTargetExited("com.music", ExitKind.CRASH);
		c.onTargetExited("com.music", ExitKind.NORMAL);
		scheduler.advance(600_000);
		assertEquals(1, env.launches.size());
	}

	@Test
	public void ST09_otherAppInFront_defaultPolicyLaunchesWithoutKillingIt() {
		env.enableSingle(MUSIC);
		env.foreground = "com.other";
		boot();
		scheduler.advance(10_000);
		assertEquals(1, env.launches.size());
		assertEquals(MUSIC, env.launches.get(0));
	}

	@Test
	public void ST10_targetAlreadyInFront_noDuplicateLaunch() {
		env.enableSingle(MUSIC);
		env.foreground = "com.music";
		boot();
		scheduler.advance(10_000);
		assertTrue(env.launches.isEmpty());
		assertEquals(StartupStatus.STARTED, env.lastStatus());
	}

	@Test
	public void ST11_disableWhilePending_cancelsTimerAndRetry() {
		env.enableSingle(MUSIC);
		boot();
		scheduler.advance(2_000);
		env.prefs.put(Keys.STARTUP_ENABLED, false);
		c.onSettingsChanged();
		assertEquals(0, scheduler.pendingCount());
		scheduler.advance(60_000);
		assertTrue(env.launches.isEmpty());
		assertEquals(StartupStatus.CANCELLED, env.lastStatus());

		// Retry pending, then disabled.
		setUp();
		env.enableSingle(MUSIC);
		env.outcomes.add(LaunchOutcome.FAILED_RETRYABLE);
		boot();
		scheduler.advance(5_000);
		env.prefs.put(Keys.STARTUP_ENABLED, false);
		c.onSettingsChanged();
		scheduler.advance(60_000);
		assertEquals(1, env.launches.size());
	}

	@Test
	public void ST11b_killSwitchCancelsEverything() {
		env.enableSingle(MUSIC);
		env.prefs.put(Keys.BT_LAUNCH_ENABLED, true);
		env.prefs.put(Keys.BT_LAUNCH_TARGET, CARLINK);
		env.prefs.put(Keys.BT_CONFLICT_POLICY, "LAST_EVENT");
		boot();
		c.onBluetoothState(true);
		env.prefs.put(Keys.NEW_FEATURES_DISABLED, true);
		c.onSettingsChanged();
		scheduler.advance(60_000);
		assertTrue(env.launches.isEmpty());
	}

	@Test
	public void ST11c_missedSettingsBroadcast_stillHonouredAtFireTime() {
		env.enableSingle(MUSIC);
		boot();
		env.prefs.put(Keys.STARTUP_ENABLED, false); // no onSettingsChanged()
		scheduler.advance(60_000);
		assertTrue(env.launches.isEmpty());
	}

	@Test
	public void ST12_changeTargetWhilePending_cancelsAndDoesNotLaunch() {
		env.enableSingle(MUSIC);
		boot();
		env.prefs.put(Keys.STARTUP_PRIMARY, NAV);
		c.onSettingsChanged();
		scheduler.advance(60_000);
		assertTrue(env.launches.isEmpty());

		// Same without the broadcast: fingerprint check at fire time.
		setUp();
		env.enableSingle(MUSIC);
		boot();
		env.prefs.put(Keys.STARTUP_FOREGROUND_POLICY, "HOME_ONLY");
		scheduler.advance(60_000);
		assertTrue(env.launches.isEmpty());
	}

	@Test
	public void ST12b_settingsChangeAloneNeverLaunches() {
		env.enableSingle(MUSIC);
		c.onHookInit("boot-1", false);
		c.onSettingsChanged();
		env.prefs.put(Keys.STARTUP_PRIMARY, NAV);
		c.onSettingsChanged();
		scheduler.advance(60_000);
		assertTrue(env.launches.isEmpty());
	}

	@Test
	public void ST13_invalidTarget_noFallbackNoRetry() {
		env.prefs.put(Keys.STARTUP_ENABLED, true);
		boot();
		scheduler.advance(60_000);
		assertEquals(StartupStatus.INVALID_TARGET, env.lastStatus());

		setUp();
		env.enableSingle("com.removed/com.removed.Main");
		boot();
		scheduler.advance(60_000);
		assertTrue(env.launches.isEmpty());
		assertEquals(StartupStatus.INVALID_TARGET, env.lastStatus());
	}

	@Test
	public void ST14_accOffWhilePending_cancelledAndNotRevived() {
		env.enableSingle(MUSIC);
		boot();
		scheduler.advance(2_000);
		c.onAccOff();
		scheduler.advance(10_000);
		c.onAccOn();
		scheduler.advance(60_000);
		assertTrue(env.launches.isEmpty());
	}

	@Test
	public void ST18_bothMode_initialAccOnAndBootAreOneSession() {
		env.enableSingle(MUSIC);
		env.prefs.put(Keys.STARTUP_TRIGGER, "BOTH");
		c.onHookInit("boot-1", false);
		c.onAccOn();
		scheduler.advance(1_000);
		c.onBootCompleted();
		c.onAccOn();
		scheduler.advance(60_000);
		assertEquals(1, env.launches.size());
		assertEquals("the ACC ON session was not replaced", Long.valueOf(5_000), env.launchTimes.get(0));
		assertFalse(env.hasStatus(StartupStatus.CANCELLED));

		// A real ACC OFF -> ON is a new resume event.
		c.onAccOff();
		env.foreground = "com.home";
		c.onAccOn();
		c.onAccOn();
		scheduler.advance(60_000);
		assertEquals(2, env.launches.size());
	}

	@Test
	public void ST18b_bothMode_bootFirstThenAccOn() {
		env.enableSingle(MUSIC);
		env.prefs.put(Keys.STARTUP_TRIGGER, "BOTH");
		boot();
		c.onAccOn();
		scheduler.advance(60_000);
		assertEquals(1, env.launches.size());
	}

	@Test
	public void ST18c_accMode_launchesOnPowerOnAndEveryResume() {
		env.enableSingle(MUSIC);
		env.prefs.put(Keys.STARTUP_TRIGGER, "ACC_RESUME");
		c.onHookInit("boot-1", false);
		c.onBootCompleted();
		scheduler.advance(60_000);
		assertTrue("boot completed alone is not an ACC event", env.launches.isEmpty());
		c.onAccOn();
		scheduler.advance(60_000);
		assertEquals(1, env.launches.size());
		c.onAccOff();
		env.foreground = "com.home";
		c.onAccOn();
		scheduler.advance(60_000);
		assertEquals(2, env.launches.size());

		// Sticky re-delivery after a SystemUI restart within the same ACC epoch.
		restartProcess();
		c.onHookInit("boot-1", true);
		c.onAccOn();
		scheduler.advance(60_000);
		assertEquals(2, env.launches.size());
	}

	private void enableBluetooth(String policy) {
		env.prefs.put(Keys.BT_LAUNCH_ENABLED, true);
		env.prefs.put(Keys.BT_LAUNCH_TARGET, CARLINK);
		if (policy != null)
			env.prefs.put(Keys.BT_CONFLICT_POLICY, policy);
	}

	@Test
	public void ST19_bluetoothTransitionsOnly() {
		enableBluetooth(null);
		c.onHookInit("boot-1", false);
		c.onBluetoothState(false); // initial disconnected report is not a transition
		c.onBluetoothState(true);
		c.onBluetoothState(true);
		scheduler.advance(2_999);
		assertTrue(env.launches.isEmpty());
		scheduler.advance(1);
		assertEquals(1, env.launches.size());
		assertEquals(CARLINK, env.launches.get(0));

		// Disconnect cancels a pending launch; reconnect is a new event.
		env.foreground = "com.home";
		c.onBluetoothState(false);
		c.onBluetoothState(true);
		scheduler.advance(1_000);
		c.onBluetoothState(false);
		scheduler.advance(60_000);
		assertEquals(1, env.launches.size());
		c.onBluetoothState(true);
		scheduler.advance(60_000);
		assertEquals(2, env.launches.size());
	}

	@Test
	public void ST19b_disconnectDoesNotLaunchByDefault() {
		env.enableSingle(MUSIC);
		enableBluetooth(null);
		c.onHookInit("boot-1", false);
		c.onBluetoothState(true);
		scheduler.advance(60_000);
		env.foreground = "com.home";
		c.onBluetoothState(false);
		scheduler.advance(60_000);
		assertEquals(1, env.launches.size());

		env.prefs.put(Keys.BT_LAUNCH_ON_DISCONNECT, true);
		c.onBluetoothState(true);
		scheduler.advance(60_000);
		env.foreground = "com.home";
		c.onBluetoothState(false);
		scheduler.advance(60_000);
		assertEquals(MUSIC, env.launches.get(env.launches.size() - 1));
	}

	@Test
	public void ST20_conflict_startupFirst() {
		env.enableSingle(MUSIC);
		enableBluetooth("STARTUP_FIRST");
		boot();
		c.onBluetoothState(true);
		scheduler.advance(60_000);
		assertEquals(1, env.launches.size());
		assertEquals(MUSIC, env.launches.get(0));
		assertTrue(env.hasStatus(StartupStatus.SKIPPED_CONFLICT));
	}

	@Test
	public void ST20b_conflict_bluetoothFirst() {
		env.enableSingle(MUSIC);
		enableBluetooth("BLUETOOTH_FIRST");
		boot();
		c.onBluetoothState(true);
		scheduler.advance(60_000);
		assertEquals(1, env.launches.size());
		assertEquals(CARLINK, env.launches.get(0));

		// Startup event while the Bluetooth launch is pending is skipped.
		setUp();
		env.enableSingle(MUSIC);
		enableBluetooth("BLUETOOTH_FIRST");
		c.onHookInit("boot-1", false);
		c.onBluetoothState(true);
		c.onBootCompleted();
		scheduler.advance(60_000);
		assertEquals(1, env.launches.size());
		assertEquals(CARLINK, env.launches.get(0));
	}

	@Test
	public void ST20c_conflict_lastEvent() {
		env.enableSingle(MUSIC);
		enableBluetooth("LAST_EVENT");
		boot();
		c.onBluetoothState(true);
		scheduler.advance(60_000);
		assertEquals(1, env.launches.size());
		assertEquals(CARLINK, env.launches.get(0));
	}

	@Test
	public void ST21_acceptedButNotConfirmed_notRepeated() {
		env.enableSingle(MUSIC);
		env.launchBringsToFront = false;
		env.foreground = "com.other";
		boot();
		scheduler.advance(600_000);
		assertEquals(1, env.launches.size());
		assertEquals(StartupStatus.UNCONFIRMED, env.lastStatus());

		setUp();
		env.enableSingle(MUSIC);
		env.launchBringsToFront = false;
		env.foreground = null;
		boot();
		scheduler.advance(600_000);
		assertEquals(1, env.launches.size());
		assertEquals(StartupStatus.UNCONFIRMED, env.lastStatus());
	}

	@Test
	public void ST23_postLaunchRecovery_disabledWithoutExitSignal() {
		env.enableSingle(MUSIC);
		env.prefs.put(Keys.STARTUP_POST_LAUNCH_POLICY, "KEEP_ALIVE");
		env.exitSignal = false;
		boot();
		scheduler.advance(10_000);
		c.onTargetExited("com.music", ExitKind.CRASH);
		scheduler.advance(600_000);
		assertEquals(1, env.launches.size());
	}

	@Test
	public void postLaunch_recoverCrash_limitedAndIgnoresNormalExitAndForceStop() {
		env.enableSingle(MUSIC);
		env.exitSignal = true;
		env.prefs.put(Keys.STARTUP_POST_LAUNCH_POLICY, "RECOVER_CRASH");
		env.prefs.put(Keys.STARTUP_RECOVERY_LIMIT, 2);
		boot();
		scheduler.advance(10_000);

		c.onTargetExited("com.music", ExitKind.BACKGROUND); // home / app switch is not an exit
		scheduler.advance(60_000);
		assertEquals(1, env.launches.size());

		env.foreground = "com.home";
		c.onTargetExited("com.music", ExitKind.CRASH);
		scheduler.advance(4_999);
		assertEquals(1, env.launches.size());
		scheduler.advance(10_000);
		assertEquals(2, env.launches.size());

		env.foreground = "com.home";
		c.onTargetExited("com.music", ExitKind.CRASH);
		scheduler.advance(60_000);
		assertEquals(3, env.launches.size());

		env.foreground = "com.home";
		c.onTargetExited("com.music", ExitKind.CRASH);
		scheduler.advance(60_000);
		assertEquals("limit 2", 3, env.launches.size());
		assertTrue(env.hasStatus(StartupStatus.RECOVERY_LIMIT_REACHED));
	}

	@Test
	public void postLaunch_keepAlive_neverRevivesForceStop() {
		env.enableSingle(MUSIC);
		env.exitSignal = true;
		env.prefs.put(Keys.STARTUP_POST_LAUNCH_POLICY, "KEEP_ALIVE");
		env.prefs.put(Keys.STARTUP_RECOVERY_LIMIT, 3);
		boot();
		scheduler.advance(10_000);
		env.foreground = "com.home";
		c.onTargetExited("com.music", ExitKind.FORCE_STOP);
		c.onTargetExited("com.music", ExitKind.CRASH); // no longer tracked
		scheduler.advance(600_000);
		assertEquals(1, env.launches.size());
	}

	@Test
	public void homeOnlyPolicy() {
		env.enableSingle(MUSIC);
		env.prefs.put(Keys.STARTUP_FOREGROUND_POLICY, "HOME_ONLY");
		env.foreground = "com.other";
		boot();
		scheduler.advance(60_000);
		assertTrue(env.launches.isEmpty());
		assertEquals(StartupStatus.SKIPPED_NOT_HOME, env.lastStatus());

		setUp();
		env.enableSingle(MUSIC);
		env.prefs.put(Keys.STARTUP_FOREGROUND_POLICY, "HOME_ONLY");
		env.foreground = null;
		boot();
		scheduler.advance(60_000);
		assertTrue("unknown foreground: skip conservatively", env.launches.isEmpty());
		assertEquals(StartupStatus.SKIPPED_FOREGROUND_UNKNOWN, env.lastStatus());

		setUp();
		env.enableSingle(MUSIC);
		env.prefs.put(Keys.STARTUP_FOREGROUND_POLICY, "HOME_ONLY");
		boot();
		scheduler.advance(60_000);
		assertEquals(1, env.launches.size());
	}

	@Test
	public void splitMode_launchesBothAndValidatesSecondary() {
		env.enableSingle(MUSIC);
		env.prefs.put(Keys.STARTUP_MODE, "SPLIT");
		boot();
		scheduler.advance(60_000);
		assertTrue(env.launches.isEmpty());
		assertEquals(StartupStatus.INVALID_TARGET, env.lastStatus());

		setUp();
		env.enableSingle(MUSIC);
		env.prefs.put(Keys.STARTUP_MODE, "SPLIT");
		env.prefs.put(Keys.STARTUP_SECONDARY, NAV);
		boot();
		scheduler.advance(60_000);
		assertEquals(1, env.launches.size());
		assertEquals("split:" + MUSIC + "+" + NAV, env.launches.get(0));
	}

	@Test
	public void singleMode_ignoresSecondaryApp() {
		env.enableSingle(MUSIC);
		env.prefs.put(Keys.STARTUP_SECONDARY, "com.removed/com.removed.Main");
		boot();
		scheduler.advance(60_000);
		assertEquals(MUSIC, env.launches.get(0));
	}

	@Test
	public void packageOnlyTarget_resolvesWithinSamePackage() {
		env.enableSingle("com.music");
		boot();
		scheduler.advance(60_000);
		assertEquals(MUSIC, env.launches.get(0));
	}

	@Test
	public void repeatedErrors_disableEngine() {
		StartupEnv broken = new FakeEnv() {
			@Override
			public com.pijulius.xposedteyes.settings.StartupSettings loadSettings() {
				throw new IllegalStateException("boom");
			}
		};
		FakeEnv reporter = (FakeEnv)broken;
		StartupCoordinator bc = new StartupCoordinator(broken, scheduler, store);
		bc.onHookInit("boot-1", false);
		bc.onBootCompleted();
		bc.onAccOn();
		bc.onAccOff(); // does not load settings
		bc.onAccOn();
		bc.onSettingsChanged();
		assertTrue(bc.isDisabled());
		assertEquals(StartupStatus.ENGINE_DISABLED, reporter.lastStatus());
		assertFalse(reporter.launches.size() > 0);
	}
}
