package ru.deelter.portalbridge.flags;

import com.mojang.serialization.DynamicOps;
import org.jspecify.annotations.NonNull;

public interface IServerStatusPacketModifier {

	@NonNull
	<T> T modify(@NonNull DynamicOps<T> ops, @NonNull T value);
}
