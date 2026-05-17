package ru.deelter.portalbridge.commands;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.deelter.portalbridge.PortalBridgePlugin;

@RequiredArgsConstructor
public class HubCommand implements CommandExecutor {

	private static final String HOST = "cominers.net";
	private static final int PORT = 25565;

	private final PortalBridgePlugin plugin;

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (!(sender instanceof Player player)) return true;

		player.sendMessage(plugin.getLang().getMessage("hub-returning", player));
		player.transfer(HOST, PORT);

		PortalBridgePlugin.getInstance().getLogger().info(String.format("Player %s rerouting to hub", player.getName()));
		return true;
	}
}