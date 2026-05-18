package ru.deelter.portalbridge.flags;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FlagDetector {

	private static final Set<String> AUTH_KEYWORDS = Set.of(
			"login", "auth", "authme", "nlogin", "jpremium", "simplelogin", "crackshotauth");
	private static final Set<String> ANTI_CHEAT_KEYWORDS = Set.of(
			"vulcan", "matrix", "grim", "nocheatplus", "spartan", "hawk",
			"anticheatreloaded", "aac", "foxaddition", "etherealac", "verus", "themis", "grimac");

	public static @NonNull Set<ServerFlag> detectFlags() {
		Set<ServerFlag> flags = EnumSet.noneOf(ServerFlag.class);
		flags.add(ServerFlag.PLUGIN_INSTALLED);

		if (Bukkit.getOnlineMode())            flags.add(ServerFlag.ONLINE_MODE);
		if (Bukkit.hasWhitelist())             flags.add(ServerFlag.WHITELIST);
		if (Bukkit.getServer().isLoggingIPs()) flags.add(ServerFlag.LOGS_IP);
		if (Bukkit.isAcceptingTransfers())     flags.add(ServerFlag.TRANSFERS);

		Set<String> pluginNames = Stream.of(Bukkit.getPluginManager().getPlugins())
				.map(Plugin::getName)
				.map(String::toLowerCase)
				.collect(Collectors.toSet());

		if (pluginNames.stream().anyMatch(name -> AUTH_KEYWORDS.stream().anyMatch(name::contains)))
			flags.add(ServerFlag.AUTH);
		if (pluginNames.stream().anyMatch(name -> ANTI_CHEAT_KEYWORDS.stream().anyMatch(name::contains)))
			flags.add(ServerFlag.ANTI_CHEAT);

		return flags;
	}
}
