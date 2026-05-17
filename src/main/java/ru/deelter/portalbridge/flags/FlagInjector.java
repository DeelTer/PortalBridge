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

	private static final String[] AUTH_PLUGINS = {"Login", "Auth", "AuthMe", "nLogin", "JPremium", "SimpleLogin", "CrackShotAuthentication"};
	private final Set<FlagEncoder.ServerFlag> cachedFlags = new HashSet<>();

	public FlagInjector() {
		boolean onlineMode = Bukkit.getOnlineMode();
		boolean hasAuth = hasAuthPlugin();
		boolean whitelistEnabled = Bukkit.hasWhitelist();

		boolean loggingIPs = Bukkit.getServer().isLoggingIPs();
		boolean acceptingTransfers = Bukkit.getServer().isAcceptingTransfers();

		if (hasAuth) cachedFlags.add(FlagEncoder.ServerFlag.HAS_AUTH_PLUGIN);
		if (onlineMode) cachedFlags.add(FlagEncoder.ServerFlag.ONLINE_MODE);
		if (whitelistEnabled) cachedFlags.add(FlagEncoder.ServerFlag.WHITELIST_ENABLED);
		if (loggingIPs) cachedFlags.add(FlagEncoder.ServerFlag.LOGS_IP_ENABLED);
		if (acceptingTransfers) cachedFlags.add(FlagEncoder.ServerFlag.TRANSFERS_ENABLED);

		cachedFlags.add(FlagEncoder.ServerFlag.PLUGIN_INSTALLED);
	}

	private boolean hasAuthPlugin() {
		for (String pluginName : AUTH_PLUGINS) {
			for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
				if (plugin.getName().equalsIgnoreCase(pluginName)) return true;
			}
		}
		return false;
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPing(@NonNull ServerListPingEvent event) {
		String flags = FlagEncoder.encode(cachedFlags);
		Component original = event.motd();
		event.motd(Component.text(flags).append(original));
	}
}