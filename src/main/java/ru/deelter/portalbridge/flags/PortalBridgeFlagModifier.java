package ru.deelter.portalbridge.flags;

import com.mojang.serialization.DynamicOps;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;
import ru.deelter.portalbridge.PortalBridgePlugin;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public final class PortalBridgeFlagModifier implements IServerStatusPacketModifier {

	private static final Set<String> AUTH_PLUGIN_KEYWORDS = Set.of(
			"login", "auth", "authme", "nlogin", "jpremium", "simplelogin", "crackshotauth");
	private static final Set<String> ANTI_CHEAT_PLUGIN_KEYWORDS = Set.of(
			"vulcan", "matrix", "grim", "nocheatplus", "spartan", "hawk",
			"anticheatreloaded", "aac", "foxaddition", "etherealac", "verus", "themis", "grimac");

	private final Set<ServerFlag> flags = EnumSet.noneOf(ServerFlag.class);

	public PortalBridgeFlagModifier() {
		flags.add(ServerFlag.PLUGIN_INSTALLED);
		if (Bukkit.getOnlineMode())            flags.add(ServerFlag.ONLINE_MODE);
		if (Bukkit.hasWhitelist())             flags.add(ServerFlag.WHITELIST);
		if (Bukkit.getServer().isLoggingIPs()) flags.add(ServerFlag.LOGS_IP);
		if (Bukkit.isAcceptingTransfers())     flags.add(ServerFlag.TRANSFERS);

		Set<String> pluginNames = new HashSet<>();
		for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) pluginNames.add(plugin.getName().toLowerCase());

		if (pluginNames.stream().anyMatch(name -> AUTH_PLUGIN_KEYWORDS.stream().anyMatch(name::contains)))
			flags.add(ServerFlag.AUTH);
		if (pluginNames.stream().anyMatch(name -> ANTI_CHEAT_PLUGIN_KEYWORDS.stream().anyMatch(name::contains)))
			flags.add(ServerFlag.ANTI_CHEAT);
	}

	@Override
	public <T> @NonNull T modify(@NonNull DynamicOps<T> ops, @NonNull T value) {
		PortalBridgePlugin.getInstance().getLogger().info("Injecting flags into status packet");
		return ops.set(value, FlagCodec.JSON_FIELD, ops.createString(FlagCodec.encodeHex(flags)));
	}

	public @NonNull Set<ServerFlag> getFlags() {
		return EnumSet.copyOf(flags);
	}
}
