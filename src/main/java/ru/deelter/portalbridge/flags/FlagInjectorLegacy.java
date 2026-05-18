package ru.deelter.portalbridge.flags;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.jspecify.annotations.NonNull;
import ru.deelter.portalbridge.PortalBridgePlugin;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public class FlagInjectorLegacy implements Listener {

    private static final String FLAG_CHAR = "\u200B";
    private final Set<ServerFlag> flags = EnumSet.noneOf(ServerFlag.class);

    public FlagInjectorLegacy() {
        flags.add(ServerFlag.PLUGIN_INSTALLED);
        if (Bukkit.getOnlineMode()) flags.add(ServerFlag.ONLINE_MODE);
        if (Bukkit.hasWhitelist()) flags.add(ServerFlag.WHITELIST);
        if (Bukkit.getServer().isLoggingIPs()) flags.add(ServerFlag.LOGS_IP);
        if (Bukkit.isAcceptingTransfers()) flags.add(ServerFlag.TRANSFERS);

        Set<String> pluginNames = new HashSet<>();
        for (org.bukkit.plugin.Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            pluginNames.add(plugin.getName().toLowerCase());
        }

        Set<String> authKeywords = Set.of("login", "auth", "authme", "nlogin", "jpremium", "simplelogin", "crackshotauth");
        Set<String> antiCheatKeywords = Set.of("vulcan", "matrix", "grim", "nocheatplus", "spartan", "hawk",
                "anticheatreloaded", "aac", "foxaddition", "etherealac", "verus", "themis", "grimac");

        if (pluginNames.stream().anyMatch(name -> authKeywords.stream().anyMatch(name::contains))) {
            flags.add(ServerFlag.AUTH);
        }
        if (pluginNames.stream().anyMatch(name -> antiCheatKeywords.stream().anyMatch(name::contains))) {
            flags.add(ServerFlag.ANTI_CHEAT);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerListPing(@NonNull ServerListPingEvent event) {
        String flagString = encodeFlags(flags);
        Component originalMotd = event.motd();
        event.motd(Component.text(flagString).append(originalMotd));
        if (PortalBridgePlugin.getInstance().getConfigManager().isDebug()) {
            PortalBridgePlugin.getInstance().getLogger().info("Legacy flag injection applied to MOTD");
        }
    }

    private @NonNull String encodeFlags(Set<ServerFlag> flagsSet) {
        StringBuilder stringBuilder = new StringBuilder();
        for (ServerFlag flag : ServerFlag.values()) {
            stringBuilder.append(flagsSet.contains(flag) ? FLAG_CHAR : "");
        }
        return stringBuilder.toString();
    }
}