package com.pijulius.xposedteyes.core.startup;

public enum LaunchCause {
	BOOT,
	ACC_ON,
	BT_CONNECT,
	BT_DISCONNECT;

	/** Bluetooth caused launches are governed by the bluetooth launch policy. */
	public boolean isBluetooth() {
		return this == BT_CONNECT || this == BT_DISCONNECT;
	}
}
