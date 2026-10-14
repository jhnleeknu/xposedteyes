package com.pijulius.xposedteyes;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

/**
 * Read-only Bluetooth call microphone path diagnostics (BT-03).
 *
 * Reads candidate properties and the audio routing state visible to a normal
 * app. Nothing is written, no recording is made and no audio input is opened,
 * so an ongoing call or another capture is never interrupted. Whether any of
 * these values affects the HFP uplink is NOT known (docs/research/bt-mic.md).
 */
public final class BtMicDiagnostics {
	private BtMicDiagnostics() {}

	/** Candidates from user reports. Read only; their effect is unverified. */
	static final String[] PROPERTIES = {
		"persist.btmic.gain",
		"persist.btspk.gain",
		"persist.audio.mic.senstivity",
	};

	public static String run(Context context) {
		StringBuilder sb = new StringBuilder();
		String unreadable = context.getString(R.string.btmic_unreadable);

		sb.append(context.getString(R.string.btmic_diag_header)).append("\n\n");

		sb.append("[properties]\n");
		for (String p : PROPERTIES) {
			String v = readProperty(p);
			sb.append(p).append(" = ").append(v == null || v.isEmpty() ? unreadable : v).append('\n');
		}

		sb.append("\n[audio]\n");
		try {
			AudioManager am = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
			sb.append("mode = ").append(modeName(am.getMode())).append('\n');
			sb.append("bluetoothScoOn = ").append(am.isBluetoothScoOn()).append('\n');
			sb.append("bluetoothScoAvailableOffCall = ").append(am.isBluetoothScoAvailableOffCall()).append('\n');
			sb.append("microphoneMute = ").append(am.isMicrophoneMute()).append('\n');

			sb.append("\n[input devices]\n");
			for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_INPUTS))
				sb.append(deviceType(d.getType())).append(" id=").append(d.getId()).append('\n');
		} catch (RuntimeException e) {
			sb.append(unreadable).append(" (").append(e.getClass().getSimpleName()).append(")\n");
		}

		sb.append('\n').append(context.getString(R.string.btmic_diag_footer));
		return sb.toString();
	}

	// Read-only access to system properties; no public API exists for vendor properties.
	@SuppressLint("PrivateApi")
	static String readProperty(String key) {
		try {
			Class<?> sp = Class.forName("android.os.SystemProperties");
			return (String)sp.getMethod("get", String.class).invoke(null, key);
		} catch (Exception e) {
			return null;
		}
	}

	static String modeName(int mode) {
		switch (mode) {
		case AudioManager.MODE_NORMAL: return "NORMAL";
		case AudioManager.MODE_RINGTONE: return "RINGTONE";
		case AudioManager.MODE_IN_CALL: return "IN_CALL";
		case AudioManager.MODE_IN_COMMUNICATION: return "IN_COMMUNICATION";
		default: return String.valueOf(mode);
		}
	}

	static String deviceType(int type) {
		switch (type) {
		case AudioDeviceInfo.TYPE_BUILTIN_MIC: return "BUILTIN_MIC";
		case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return "BLUETOOTH_SCO";
		case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "WIRED_HEADSET";
		case AudioDeviceInfo.TYPE_USB_DEVICE: return "USB_DEVICE";
		case AudioDeviceInfo.TYPE_USB_HEADSET: return "USB_HEADSET";
		case AudioDeviceInfo.TYPE_TELEPHONY: return "TELEPHONY";
		case AudioDeviceInfo.TYPE_FM_TUNER: return "FM_TUNER";
		case AudioDeviceInfo.TYPE_TV_TUNER: return "TV_TUNER";
		case AudioDeviceInfo.TYPE_LINE_ANALOG: return "LINE_ANALOG";
		case AudioDeviceInfo.TYPE_REMOTE_SUBMIX: return "REMOTE_SUBMIX";
		case AudioDeviceInfo.TYPE_BUS: return "BUS";
		default: return "TYPE_" + type;
		}
	}
}
