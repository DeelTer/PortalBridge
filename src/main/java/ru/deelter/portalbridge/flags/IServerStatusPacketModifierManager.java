package ru.deelter.portalbridge.flags;

import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Collection;

public interface IServerStatusPacketModifierManager {

	@NonNull
	Collection<? extends IServerStatusPacketModifier> getModifiers();

	@NonNull
	Collection<? extends IServerStatusPacketModifier> getModifiersByPlugin(@NonNull Plugin plugin);

	void registerModifier(@NonNull Plugin plugin, @NonNull IServerStatusPacketModifier modifier);

	void unregisterModifier(@NonNull IServerStatusPacketModifier modifier);

	default void unregisterModifiers(@NonNull IServerStatusPacketModifier... modifiers) {
		Arrays.stream(modifiers).forEach(this::unregisterModifier);
	}

	default void unregisterModifiers(@NonNull Collection<? extends IServerStatusPacketModifier> modifiers) {
		unregisterModifiers(modifiers.toArray(IServerStatusPacketModifier[]::new));
	}

	default void unregisterModifiersByPlugin(@NonNull Plugin plugin) {
		unregisterModifiers(getModifiersByPlugin(plugin));
	}

	default void unregisterAllModifiers() {
		unregisterModifiers(getModifiers());
	}

	boolean isEnabled();

	void enable();

	void disable();

	@NonNull
	static IServerStatusPacketModifierManager create() {
		return ServerStatusPacketModifierManagerProvider.getFactory().create();
	}

	@NonNull
	static Factory getDefaultFactory() {
		return ServerStatusPacketModifierManagerProvider.getFactory();
	}

	static void setDefaultFactory(@NonNull Factory factory) {
		ServerStatusPacketModifierManagerProvider.setFactory(factory);
	}

	interface Factory {
		@NonNull
		IServerStatusPacketModifierManager create();
	}
}
