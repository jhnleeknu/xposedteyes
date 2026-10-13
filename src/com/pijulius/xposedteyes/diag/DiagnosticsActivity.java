package com.pijulius.xposedteyes.diag;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.pijulius.xposedteyes.BuildConfig;
import com.pijulius.xposedteyes.ModulePrefs;
import com.pijulius.xposedteyes.ModuleStatus;
import com.pijulius.xposedteyes.R;
import com.pijulius.xposedteyes.RootAccessibility;
import com.pijulius.xposedteyes.SettingsActivity;
import com.pijulius.xposedteyes.core.compat.Feature;
import com.pijulius.xposedteyes.core.settings.AppSettings;

import dalvik.system.DexFile;

/**
 * Read-only diagnostics. Everything is computed on the device; a report leaves it
 * only when the user presses export after seeing the exact text on screen.
 * Nothing here records audio, changes system properties or touches CarLink.
 */
@SuppressWarnings("deprecation")
public class DiagnosticsActivity extends Activity {
	public static final String EXTRA_MODE = "mode";
	public static final String MODE_COMPAT = "compat";
	public static final String MODE_LOG = "log";
	public static final String MODE_CARLINK = "carlink";
	public static final String MODE_BTMIC = "btmic";

	static final String CARLINK = "com.syu.carlink";
	static final String SYU_BT = "com.syu.bt";
	/** Candidates named in the spec. Read only; their meaning is unverified. */
	static final String[] BT_MIC_PROPERTIES = {
		"persist.btmic.gain", "persist.btspk.gain", "persist.audio.mic.senstivity"};
	/** Class name fragments worth listing when looking for the CarPlay video/touch path. */
	static final String[] CARPLAY_HINTS = {"carplay", "airplay", "iap2", "mfi", "screenstream", "videodecoder", "touch"};
	private static final int MAX_CLASS_LINES = 400;
	private static final int REQUEST_EXPORT = 1;

	private String mode;
	private TextView text;
	private String report = "";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.diagnostics);
		text = findViewById(R.id.diagText);
		mode = getIntent().getStringExtra(EXTRA_MODE);
		if (mode == null)
			mode = MODE_COMPAT;

		Button clear = findViewById(R.id.diagClear);
		clear.setVisibility(MODE_LOG.equals(mode) ? View.VISIBLE : View.GONE);
		clear.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				LocalLog.get(DiagnosticsActivity.this).clear();
				load();
			}
		});

		findViewById(R.id.diagExport).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				export();
			}
		});

		load();
	}

	private void load() {
		text.setText(R.string.diag_loading);
		new Thread(new Runnable() {
			@Override
			public void run() {
				String r;
				try {
					r = build();
				} catch (Throwable t) {
					r = "error: " + t.getClass().getSimpleName();
				}
				// Reports are built from non-personal fields only; the log is sanitized on write.
				final String result = r;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						report = result;
						text.setText(result);
					}
				});
			}
		}, "XposedTeyesDiag").start();
	}

	private String build() throws Exception {
		if (MODE_LOG.equals(mode)) {
			setTitleSafe(R.string.pref_local_log);
			String log = LocalLog.get(this).readAll();
			return log.isEmpty() ? getString(R.string.diag_log_empty) : log;
		}
		if (MODE_CARLINK.equals(mode)) {
			setTitleSafe(R.string.pref_carlink_info);
			return carlinkReport();
		}
		if (MODE_BTMIC.equals(mode)) {
			setTitleSafe(R.string.pref_btmic_diag);
			return btMicReport();
		}
		setTitleSafe(R.string.pref_compatibility);
		return compatReport();
	}

	private void setTitleSafe(final int res) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				setTitle(res);
			}
		});
	}

	private String compatReport() {
		StringBuilder sb = new StringBuilder();
		sb.append("XposedTeyes ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.GIT_SHA).append(")\n");
		sb.append("Android ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT).append('\n');
		sb.append("ABI ").append(Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?").append("\n\n");

		sb.append(getString(R.string.diag_feature_header)).append('\n');
		for (Feature f : Feature.values()) {
			sb.append(String.format(Locale.ROOT, "%-22s code=%-12s device=%s%n", f.name(), f.code, f.device));
		}

		AppSettings s = ModulePrefs.settings(this);
		sb.append('\n').append(getString(R.string.diag_runtime_header)).append('\n');
		sb.append("module hooked in own process: ").append(ModuleStatus.isHookedInOwnProcess()
			? "yes" : "not confirmed (module app not in its own LSPosed scope)").append('\n');
		sb.append("accessibility service: ").append(RootAccessibility.isEnabled(this) ? "enabled" : "disabled").append('\n');
		sb.append("settings schema: ").append(s.schemaVersion).append(s.derivedFromLegacy ? " (legacy)" : "").append('\n');
		sb.append("new features disabled: ").append(s.killSwitch).append('\n');
		sb.append("startup: enabled=").append(s.startup.enabled).append(" trigger=").append(s.startup.trigger)
			.append(" mode=").append(s.startup.mode).append(" delayMs=").append(s.startup.delayMs)
			.append(" retries=").append(s.startup.retryCount).append('\n');
		sb.append("bluetooth launch: enabled=").append(s.bluetooth.enabled).append(" onDisconnect=")
			.append(s.bluetooth.launchOnDisconnect).append(" conflict=").append(s.bluetooth.conflictPolicy).append('\n');
		sb.append(CARLINK).append(": ").append(versionOf(CARLINK)).append('\n');
		sb.append(SYU_BT).append(": ").append(versionOf(SYU_BT)).append('\n');
		sb.append("\n").append(getString(R.string.diag_compat_note)).append('\n');
		return sb.toString();
	}

	private String carlinkReport() throws Exception {
		StringBuilder sb = new StringBuilder();
		sb.append(getString(R.string.diag_carlink_intro)).append("\n\n");
		PackageInfo pi;
		try {
			pi = getPackageManager().getPackageInfo(CARLINK, 0);
		} catch (PackageManager.NameNotFoundException e) {
			return sb.append(CARLINK).append(": not installed\n").toString();
		}
		ApplicationInfo ai = pi.applicationInfo;
		sb.append("package: ").append(CARLINK).append('\n');
		sb.append("version: ").append(pi.versionName).append(" (").append(pi.versionCode).append(")\n");
		sb.append("apk sha256: ").append(sha256(new File(ai.sourceDir))).append('\n');
		if (ai.splitSourceDirs != null)
			sb.append("split apks: ").append(ai.splitSourceDirs.length).append('\n');

		sb.append("\nnative libraries:\n");
		File libDir = ai.nativeLibraryDir == null ? null : new File(ai.nativeLibraryDir);
		String[] libs = libDir == null ? null : libDir.list();
		if (libs == null || libs.length == 0) {
			sb.append("  (none visible)\n");
		} else {
			List<String> sorted = new ArrayList<>();
			Collections.addAll(sorted, libs);
			Collections.sort(sorted);
			for (String l : sorted)
				sb.append("  ").append(l).append('\n');
		}

		sb.append("\nAndroid Auto hook points used today (CarLink > Android Auto settings):\n");
		Context carlink = createPackageContext(CARLINK, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
		ClassLoader cl = carlink.getClassLoader();
		for (String cls : new String[] {
				"com.syu.carlink.auto.AutoFragment",
				"com.google.android.projection.proto.Protos$VideoConfiguration",
				"com.google.android.projection.sink.ui.SystemUiHider"}) {
			sb.append("  ").append(cls).append(": ").append(classExists(cl, cls) ? "present" : "missing").append('\n');
		}

		sb.append("\n").append(getString(R.string.diag_carlink_classes)).append('\n');
		int count = 0;
		DexFile dex = null;
		try {
			dex = new DexFile(ai.sourceDir);
			List<String> hits = new ArrayList<>();
			for (Enumeration<String> e = dex.entries(); e.hasMoreElements();) {
				String name = e.nextElement();
				String lower = name.toLowerCase(Locale.ROOT);
				for (String hint : CARPLAY_HINTS) {
					if (lower.contains(hint)) {
						hits.add(name);
						break;
					}
				}
			}
			Collections.sort(hits);
			for (String h : hits) {
				if (count++ >= MAX_CLASS_LINES) {
					sb.append("  ... ").append(hits.size() - MAX_CLASS_LINES).append(" more\n");
					break;
				}
				sb.append("  ").append(h).append('\n');
			}
			if (hits.isEmpty())
				sb.append("  (no matching class names)\n");
		} catch (Exception e) {
			sb.append("  dex listing unavailable: ").append(e.getClass().getSimpleName()).append('\n');
		} finally {
			if (dex != null)
				dex.close();
		}
		return sb.toString();
	}

	private String btMicReport() {
		StringBuilder sb = new StringBuilder();
		sb.append(getString(R.string.diag_btmic_intro)).append("\n\n");
		sb.append(SYU_BT).append(": ").append(versionOf(SYU_BT)).append('\n');

		sb.append("\n").append(getString(R.string.diag_btmic_props)).append('\n');
		for (String p : BT_MIC_PROPERTIES)
			sb.append("  ").append(p).append(" = ").append(systemProperty(p)).append('\n');

		AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
		sb.append("\n").append(getString(R.string.diag_btmic_audio)).append('\n');
		sb.append("  mode: ").append(audioMode(am.getMode())).append('\n');
		sb.append("  bluetooth SCO on: ").append(am.isBluetoothScoOn()).append('\n');
		sb.append("  SCO available off call: ").append(am.isBluetoothScoAvailableOffCall()).append('\n');
		sb.append("  microphone muted: ").append(am.isMicrophoneMute()).append('\n');
		sb.append("  input devices:\n");
		for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_INPUTS))
			// Type only: product names can carry the paired phone's name.
			sb.append("    type=").append(d.getType()).append('\n');

		sb.append("\n").append(getString(R.string.diag_btmic_effects)).append('\n');
		sb.append("  AcousticEchoCanceler: ").append(AcousticEchoCanceler.isAvailable()).append('\n');
		sb.append("  NoiseSuppressor: ").append(NoiseSuppressor.isAvailable()).append('\n');
		sb.append("  AutomaticGainControl: ").append(AutomaticGainControl.isAvailable()).append('\n');

		sb.append("\n").append(getString(R.string.diag_btmic_hfp)).append('\n');
		return sb.toString();
	}

	private String versionOf(String pkg) {
		try {
			PackageInfo pi = getPackageManager().getPackageInfo(pkg, 0);
			return pi.versionName + " (" + pi.versionCode + ")";
		} catch (PackageManager.NameNotFoundException e) {
			return "not installed";
		}
	}

	private static boolean classExists(ClassLoader cl, String name) {
		try {
			Class.forName(name, false, cl);
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	private static String systemProperty(String key) {
		try {
			Class<?> sp = Class.forName("android.os.SystemProperties");
			Method get = sp.getMethod("get", String.class, String.class);
			String v = (String)get.invoke(null, key, "");
			return v == null || v.isEmpty() ? "(unset or not readable)" : v;
		} catch (Throwable t) {
			return "(not readable)";
		}
	}

	private static String audioMode(int mode) {
		switch (mode) {
			case AudioManager.MODE_NORMAL: return "NORMAL";
			case AudioManager.MODE_RINGTONE: return "RINGTONE";
			case AudioManager.MODE_IN_CALL: return "IN_CALL";
			case AudioManager.MODE_IN_COMMUNICATION: return "IN_COMMUNICATION";
			default: return String.valueOf(mode);
		}
	}

	private static String sha256(File f) {
		try (InputStream in = new FileInputStream(f)) {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] buf = new byte[65536];
			int n;
			while ((n = in.read(buf)) > 0)
				md.update(buf, 0, n);
			StringBuilder sb = new StringBuilder();
			for (byte b : md.digest())
				sb.append(String.format(Locale.ROOT, "%02x", b));
			return sb.toString();
		} catch (Exception e) {
			return "(not readable)";
		}
	}

	private void export() {
		if (report.isEmpty())
			return;
		Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
			.addCategory(Intent.CATEGORY_OPENABLE)
			.setType("text/plain")
			.putExtra(Intent.EXTRA_TITLE, "xposedteyes-" + mode + ".txt");
		try {
			startActivityForResult(intent, REQUEST_EXPORT);
		} catch (ActivityNotFoundException e) {
			Toast.makeText(this, R.string.toast_no_file_picker, Toast.LENGTH_LONG).show();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode != REQUEST_EXPORT || resultCode != RESULT_OK || data == null || data.getData() == null)
			return;
		boolean ok = SettingsActivity.write(this, data.getData(), report);
		Toast.makeText(this, ok ? R.string.toast_export_done : R.string.toast_export_failed, Toast.LENGTH_LONG).show();
	}
}
