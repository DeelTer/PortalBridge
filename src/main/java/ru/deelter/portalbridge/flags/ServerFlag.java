package ru.deelter.portalbridge.flags;

import org.jspecify.annotations.Nullable;

public enum ServerFlag {
	PLUGIN_INSTALLED,
	PROXY,
	ONLINE_MODE,
	AUTH,
	ANTI_CHEAT,
	WHITELIST,
	TRANSFERS,
	LOGS_IP;

	public static @Nullable ServerFlag fromOrdinal(int ordinal) {
		ServerFlag[] values = values();
		if (ordinal < 0 || ordinal >= values.length) return null;
		return values[ordinal];
	}
}
