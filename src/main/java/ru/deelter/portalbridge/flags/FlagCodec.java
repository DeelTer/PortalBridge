package ru.deelter.portalbridge.flags;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

public final class FlagCodec {

	public static final String JSON_FIELD = "portalbridge_flags";

	private FlagCodec() {}

	public static int encode(@NonNull Set<ServerFlag> flags) {
		int bits = 0;
		for (ServerFlag flag : flags) bits |= (1 << flag.ordinal());
		return bits;
	}

	public static @NonNull Set<ServerFlag> decode(int bits) {
		Set<ServerFlag> result = EnumSet.noneOf(ServerFlag.class);
		for (ServerFlag flag : ServerFlag.values()) {
			if ((bits & (1 << flag.ordinal())) != 0) result.add(flag);
		}
		return result;
	}

	public static @NonNull Set<ServerFlag> decode(@Nullable String hex) {
		if (hex == null || hex.isEmpty()) return EnumSet.noneOf(ServerFlag.class);
		try {
			return decode(Integer.parseInt(hex, 16));
		} catch (NumberFormatException e) {
			return EnumSet.noneOf(ServerFlag.class);
		}
	}

	public static @NonNull String encodeHex(@NonNull Set<ServerFlag> flags) {
		return Integer.toHexString(encode(flags));
	}
}
