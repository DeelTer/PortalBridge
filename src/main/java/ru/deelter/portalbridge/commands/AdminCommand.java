package ru.deelter.portalbridge.commands;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;
import ru.deelter.portalbridge.PortalBridgePlugin;

@RequiredArgsConstructor
public class AdminCommand implements CommandExecutor {

    private final PortalBridgePlugin plugin;

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
        if (args.length == 0) return false;
        if (args[0].equalsIgnoreCase("reload")) {
            plugin.getConfigManager().loadConfig();
            plugin.getLang().reload();

            sender.sendMessage("PortalBridge reloaded.");
        }
        return true;
    }
}