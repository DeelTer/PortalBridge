package ru.deelter.portalbridge.commands;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import ru.deelter.portalbridge.PortalBridgePlugin;
import ru.deelter.portalbridge.lang.Lang;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class PortalCommand implements CommandExecutor, TabCompleter {

    private final PortalBridgePlugin plugin;
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "open"  -> handleOpen(player, args);
            case "bind"  -> handleBind(player, args);
            case "hub"   -> handleHub(player);
            case "admin" -> handleAdmin(player, args);
            case "help"  -> sendHelp(player);
            default      -> handleLegacyOpen(player, args);
        }
        return true;
    }

    private void handleOpen(Player player, String @NonNull [] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.getLang().getMessage("usage-open", player));
            return;
        }
        String[] parsed = parseHostPort(args[1]);
        if (parsed == null) {
            player.sendMessage(plugin.getLang().getMessage("invalid-port", player));
            return;
        }
        initiatePortal(player, parsed[0], Integer.parseInt(parsed[1]));
    }

    private void handleBind(@NonNull Player player, String[] args) {
        if (!player.hasPermission("portalbridge.bind")) {
            player.sendMessage(plugin.getLang().getMessage("no-permission", player));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.getLang().getMessage("usage-bind", player));
            return;
        }
        var item = player.getInventory().getItemInMainHand();
        var bindManager = plugin.getDoorBindManager();
        if (!bindManager.isDoor(item)) {
            player.sendMessage(plugin.getLang().getMessage("bind-no-door", player));
            return;
        }
        String[] parsed = parseHostPort(args[1]);
        if (parsed == null) {
            player.sendMessage(plugin.getLang().getMessage("invalid-port", player));
            return;
        }
        int port = Integer.parseInt(parsed[1]);
        bindManager.bind(item, parsed[0], port);
        String address = port == 25565 ? parsed[0] : parsed[0] + ":" + port;
        player.sendMessage(plugin.getLang().getMessage("bind-success", player, "target", address));
    }

    private void handleHub(@NonNull Player player) {
        var cfg = plugin.getConfigManager();
        player.sendMessage(plugin.getLang().getMessage("hub-returning", player));
        player.transfer(cfg.getHubHost(), cfg.getHubPort());
    }

    private void handleAdmin(@NonNull Player player, String[] args) {
        if (!player.hasPermission("portalbridge.admin")) {
            player.sendMessage(plugin.getLang().getMessage("no-permission", player));
            return;
        }
        if (args.length < 2) return;
        switch (args[1].toLowerCase()) {
            case "reload" -> {
                plugin.getConfigManager().loadConfig();
                plugin.getLang().reload();
                player.sendMessage(plugin.getLang().getMessage("admin-reload", player));
            }
            case "removeall" -> {
                plugin.getPortalManager().removeAllPortals();
                player.sendMessage(plugin.getLang().getMessage("admin-removeall", player));
            }
        }
    }

    private void sendHelp(@NonNull Player player) {
        Lang lang = plugin.getLang();
        player.sendMessage(lang.getMessage("help-header", player));
        player.sendMessage(lang.getMessage("help-open", player));
        if (player.hasPermission("portalbridge.bind"))
            player.sendMessage(lang.getMessage("help-bind", player));
        player.sendMessage(lang.getMessage("help-hub", player));
        if (player.hasPermission("portalbridge.admin"))
            player.sendMessage(lang.getMessage("help-admin", player));
    }

    // Backwards compat: /portal <host>[:port]
    private void handleLegacyOpen(Player player, String @NonNull [] args) {
        String[] parsed = parseHostPort(args[0]);
        if (parsed == null) {
            player.sendMessage(plugin.getLang().getMessage("invalid-port", player));
            return;
        }
        initiatePortal(player, parsed[0], Integer.parseInt(parsed[1]));
    }

    private void initiatePortal(@NonNull Player player, String host, int port) {
        if (!processing.add(player.getUniqueId())) return;

        plugin.getServerPinger().ping(host, port).thenAccept(info -> {
            if (!plugin.getTrustListManager().isAllowed(info, host, port)) {
                player.sendMessage(plugin.getLang().getMessage("portal-create-untrusted", player));
                processing.remove(player.getUniqueId());
                return;
            }
            if (plugin.getTrustListManager().isBlacklisted(host, port)) {
                player.sendMessage(plugin.getLang().getMessage("server-blacklisted", player));
                processing.remove(player.getUniqueId());
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    int lifetime = plugin.getConfigManager().getPortalLifetimeSeconds();
                    String error = plugin.getPortalManager().createPortal(player, host, port, lifetime);
                    if (error != null) {
                        player.sendMessage(plugin.getLang().getMessage(error, player));
                        return;
                    }
                    String address = port == 25565 ? host : host + ":" + port;
                    player.sendMessage(plugin.getLang().getMessage("portal-created", player, "target", address));
                } finally {
                    processing.remove(player.getUniqueId());
                }
            });
        });
    }

    /** Returns [host, portStr] or null if port is invalid. */
    private String @Nullable [] parseHostPort(@NonNull String arg) {
        String host;
        int port;
        if (arg.contains(":")) {
            String[] split = arg.split(":", 2);
            host = split[0];
            try {
                port = Integer.parseInt(split[1]);
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            host = arg;
            port = 25565;
        }
        if (port < 1 || port > 65535) return null;
        return new String[]{host, String.valueOf(port)};
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return List.of();

        if (args.length == 1) {
            var subs = new java.util.ArrayList<>(List.of("open", "hub", "help"));
            subs.add("bind");

            if (player.hasPermission("portalbridge.admin")) {
                subs.add("admin");
            }
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && player.hasPermission("portalbridge.admin")) {
            return List.of("reload", "removeall").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        }

        return List.of();
    }
}
