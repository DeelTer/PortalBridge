package ru.deelter.portalbridge.flags;

import com.mojang.serialization.DynamicOps;
import org.jspecify.annotations.NonNull;

import java.util.EnumSet;
import java.util.Set;

public final class PortalBridgeFlagModifier implements IServerStatusPacketModifier {

	private final Set<ServerFlag> flags;

	public PortalBridgeFlagModifier() {
		this.flags = FlagDetector.detectFlags();
	}

	@Override
	public <T> @NonNull T modify(@NonNull DynamicOps<T> ops, @NonNull T value) {
		return ops.set(value, FlagCodec.JSON_FIELD, ops.createString(FlagCodec.encodeHex(flags)));
	}

	public @NonNull Set<ServerFlag> getFlags() {
		return EnumSet.copyOf(flags);
	}
}
