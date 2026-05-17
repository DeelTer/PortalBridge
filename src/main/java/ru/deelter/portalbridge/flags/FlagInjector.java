package ru.deelter.portalbridge.flags;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

import java.util.HashSet;
import java.util.Set;

public class FlagInjector implements Listener {

	private static final Set<String> PLUGIN_KEYWORDS_AUTH = Set.of("login", "auth", "authme", "nlogin", "jpremium", "simplelogin", "crackshotauth");
	private static final Set<String> PLUGIN_KEYWORDS_ANTI_CHEAT = Set.of(
			"vulcan", "matrix", "grim", "nocheatplus", "spartan", "hawk",
			"anticheatreloaded", "aac", "foxaddition",
			"etherealac", "verus", "themis", "grimac"
	);
	private final Set<FlagEncoder.ServerFlag> cachedFlags = new HashSet<>();

	public FlagInjector() {
		boolean onlineMode = Bukkit.getOnlineMode();
		boolean whitelistEnabled = Bukkit.hasWhitelist();

		boolean loggingIPs = Bukkit.getServer().isLoggingIPs();
		boolean acceptingTransfers = Bukkit.getServer().isAcceptingTransfers();

		if (onlineMode) cachedFlags.add(FlagEncoder.ServerFlag.ONLINE_MODE);
		if (whitelistEnabled) cachedFlags.add(FlagEncoder.ServerFlag.WHITELIST);
		if (loggingIPs) cachedFlags.add(FlagEncoder.ServerFlag.LOGS_IP);
		if (acceptingTransfers) cachedFlags.add(FlagEncoder.ServerFlag.TRANSFERS);


		Set<String> pluginNames = new HashSet<>();
		for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
			String name = p.getName().toLowerCase();
			pluginNames.add(name);
		}

		boolean hasAuth = pluginNames.stream().anyMatch(name -> PLUGIN_KEYWORDS_AUTH.stream().anyMatch(name::contains));
		boolean hasAntiCheat = pluginNames.stream().anyMatch(name -> PLUGIN_KEYWORDS_ANTI_CHEAT.stream().anyMatch(name::contains));
		if (hasAuth) {
			cachedFlags.add(FlagEncoder.ServerFlag.AUTH);
		}
		if (hasAntiCheat) {
			cachedFlags.add(FlagEncoder.ServerFlag.ANTI_CHEAT);
		}
		cachedFlags.add(FlagEncoder.ServerFlag.PLUGIN_INSTALLED);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPing(@NonNull ServerListPingEvent event) {
		String flags = FlagEncoder.encode(cachedFlags);
		Component original = event.motd();
		event.motd(Component.text(flags).append(original));
	}
}