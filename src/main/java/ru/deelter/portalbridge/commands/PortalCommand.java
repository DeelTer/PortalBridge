package ru.deelter.portalbridge.commands;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.deelter.portalbridge.PortalBridgePlugin;

@RequiredArgsConstructor
public class PortalCommand implements CommandExecutor {

    private final PortalBridgePlugin plugin;

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length < 1) return false;

        String host;
        int port;

        if (args.length == 1) {
            String arg = args[0];
            if (arg.contains(":")) {
                String[] split = arg.split(":", 2);
                host = split[0];
                try {
                    port = Integer.parseInt(split[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cInvalid port format.");
                    return true;
                }
            } else {
                host = arg;
                port = 25565;
            }
        } else {
            host = args[0];
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid port format.");
                return true;
            }
        }

        int lifetime = plugin.getConfigManager().getPortalLifetimeSeconds();

        plugin.getServerPinger().ping(host, port).thenAccept(info -> {
            if (plugin.getTrustListManager().isBlacklisted(host, port)) {
                player.sendMessage(plugin.getLang().getMessage("server-blacklisted", player));
                return;
            }
            if (!plugin.getTrustListManager().isAllowed(info, host, port)) {
                player.sendMessage(plugin.getLang().getMessage("portal-create-untrusted", player));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!plugin.getPortalManager().canPlace(player, player.getTargetBlock(null, plugin.getConfigManager().getMaxPlacementDistance()).getLocation())) {
                    player.sendMessage(plugin.getLang().getMessage("cannot-place-near-player", player));
                    return;
                }
                plugin.getPortalManager().createPortal(player, host, port, lifetime);
                player.sendMessage(plugin.getLang().getMessage("portal-created", player, "target", host + ":" + port));
            });
        });
        return true;
    }
}