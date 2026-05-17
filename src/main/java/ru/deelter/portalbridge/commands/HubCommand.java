package ru.deelter.portalbridge.commands;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.deelter.portalbridge.PortalBridgePlugin;

@RequiredArgsConstructor
public class HubCommand implements CommandExecutor {

    private final PortalBridgePlugin plugin;

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        var cfg = plugin.getConfigManager();
        player.sendMessage(plugin.getLang().getMessage("hub-returning", player));
        player.transfer(cfg.getHubHost(), cfg.getHubPort());
        plugin.getLogger().info("Player " + player.getName() + " rerouting to hub");
        return true;
    }
}
