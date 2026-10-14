package com.pijulius.xposedteyes.diag;

import java.util.regex.Pattern;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.DisplayMetrics;

import com.pijulius.xposedteyes.settings.Keys;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Opt-in, read-only diagnostics for the CarPlay display (CP-02) and the
 * Bluetooth call microphone path (BT-02) analysis.
 *
 * These hooks never change arguments or results; they only write a bounded
 * number of lines to the Xposed (LSPosed) log. They are installed only when
 * enabled in "Advanced / Diagnostics" and only in the listed packages, and
 * take effect after the target app restarts. No audio data, phone numbers or
 * device identifiers are logged.
 */
public final class DiagHooks {
	private DiagHooks() {}

	static final String TAG = "XposedTeyes diag: ";
	static final int MAX_LINES = 200;

	static final String[] VIDEO_PACKAGES = { "com.syu.carlink" };
	static final String[] AUDIO_PACKAGES = { "com.syu.bt", "com.syu.carlink", "com.syu.ms", "com.syu.us",
		"com.android.systemui" };

	/** System property keys worth logging for the microphone path analysis. */
	static final Pattern AUDIO_PROPERTY = Pattern.compile("(?i).*(mic|hfp|sco|btspk|btmic|aec|agc).*");

	private static int lines = 0;

	public static void install(LoadPackageParam lpparam, XSharedPreferences settings) {
		try {
			if (settings.getBoolean(Keys.NEW_FEATURES_DISABLED, false))
				return;
			if (settings.getBoolean(Keys.DIAG_CARLINK_VIDEO, false) && contains(VIDEO_PACKAGES, lpparam.packageName))
				installVideo(lpparam.packageName);
			if (settings.getBoolean(Keys.DIAG_AUDIO_PATH, false) && contains(AUDIO_PACKAGES, lpparam.packageName))
				installAudio(lpparam.packageName);
		} catch (Throwable t) {
			XposedBridge.log(TAG + "install failed in " + lpparam.packageName + ": " + t);
		}
	}

	static boolean contains(String[] list, String value) {
		for (String s : list)
			if (s.equals(value))
				return true;
		return false;
	}

	static synchronized void log(String pkg, String message) {
		if (lines >= MAX_LINES)
			return;
		lines++;
		if (message.length() > 300)
			message = message.substring(0, 300) + "...";
		XposedBridge.log(TAG + pkg + " " + message + (lines == MAX_LINES ? " [limit reached]" : ""));
	}

	private static void installVideo(final String pkg) {
		XposedBridge.hookAllMethods(MediaCodec.class, "configure", new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) {
				try {
					if (param.args.length == 0 || !(param.args[0] instanceof MediaFormat))
						return;
					MediaFormat f = (MediaFormat)param.args[0];
					String codec = ((MediaCodec)param.thisObject).getName();
					log(pkg, "MediaCodec.configure codec=" + codec +
						" mime=" + str(f, MediaFormat.KEY_MIME) +
						" width=" + num(f, MediaFormat.KEY_WIDTH) +
						" height=" + num(f, MediaFormat.KEY_HEIGHT) +
						" frame-rate=" + num(f, MediaFormat.KEY_FRAME_RATE) +
						" encoder=" + (param.args.length > 3 && Integer.valueOf(1).equals(param.args[3])) +
						" stack=" + caller());
				} catch (Throwable t) {}
			}
		});

		XposedBridge.hookAllMethods(Activity.class, "onResume", new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) {
				try {
					Activity a = (Activity)param.thisObject;
					DisplayMetrics m = a.getResources().getDisplayMetrics();
					log(pkg, "Activity " + a.getClass().getName() + " display=" + m.widthPixels + "x" +
						m.heightPixels + " densityDpi=" + m.densityDpi);
				} catch (Throwable t) {}
			}
		});
	}

	private static void installAudio(final String pkg) {
		XposedBridge.hookAllConstructors(android.media.AudioRecord.class, new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) {
				try {
					android.media.AudioRecord r = (android.media.AudioRecord)param.thisObject;
					log(pkg, "AudioRecord source=" + r.getAudioSource() + " rate=" + r.getSampleRate() +
						" channels=" + r.getChannelCount() + " format=" + r.getAudioFormat() +
						" session=" + r.getAudioSessionId() + " stack=" + caller());
				} catch (Throwable t) {}
			}
		});

		XposedBridge.hookAllMethods(android.media.AudioManager.class, "setParameters", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) {
				if (param.args.length > 0)
					log(pkg, "AudioManager.setParameters " + param.args[0] + " stack=" + caller());
			}
		});

		XposedBridge.hookAllMethods(android.media.AudioManager.class, "setMode", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) {
				if (param.args.length > 0)
					log(pkg, "AudioManager.setMode " + param.args[0]);
			}
		});

		for (String effect : new String[] { "AcousticEchoCanceler", "NoiseSuppressor", "AutomaticGainControl" }) {
			final String name = effect;
			try {
				Class<?> c = Class.forName("android.media.audiofx." + effect);
				XposedBridge.hookAllMethods(c, "create", new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) {
						log(pkg, name + ".create session=" + (param.args.length > 0 ? param.args[0] : "?") +
							" ok=" + (param.getResult() != null));
					}
				});
			} catch (Throwable t) {}
		}

		try {
			Class<?> audioSystem = XposedHelpers.findClass("android.media.AudioSystem", null);
			XposedBridge.hookAllMethods(audioSystem, "setParameters", new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) {
					if (param.args.length > 0)
						log(pkg, "AudioSystem.setParameters " + param.args[0] + " stack=" + caller());
				}
			});
		} catch (Throwable t) {}

		try {
			Class<?> props = XposedHelpers.findClass("android.os.SystemProperties", null);
			XposedBridge.hookAllMethods(props, "set", new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) {
					if (param.args.length < 2 || !(param.args[0] instanceof String))
						return;
					String key = (String)param.args[0];
					if (AUDIO_PROPERTY.matcher(key).matches())
						log(pkg, "SystemProperties.set " + key + "=" + param.args[1] + " stack=" + caller());
				}
			});
		} catch (Throwable t) {}
	}

	private static String str(MediaFormat f, String key) {
		return f.containsKey(key) ? f.getString(key) : "-";
	}

	private static String num(MediaFormat f, String key) {
		try {
			return f.containsKey(key) ? String.valueOf(f.getInteger(key)) : "-";
		} catch (ClassCastException e) {
			return String.valueOf(f.getFloat(key));
		}
	}

	/** First stack frame outside the framework/Xposed, to locate the calling app class. */
	static String caller() {
		for (StackTraceElement e : new Throwable().getStackTrace()) {
			String c = e.getClassName();
			if (c.startsWith("android.") || c.startsWith("java.") || c.startsWith("de.robv.") ||
				c.startsWith("com.pijulius.") || c.startsWith("dalvik.") || c.startsWith("com.android.internal.") ||
				c.startsWith("LSP") || c.startsWith("org.lsposed."))
			{
				continue;
			}
			return c + "." + e.getMethodName();
		}
		return "-";
	}
}
