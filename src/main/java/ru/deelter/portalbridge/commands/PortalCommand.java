package ru.deelter.portalbridge.commands;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import ru.deelter.portalbridge.pinger.ServerInfo;
import ru.deelter.portalbridge.portal.Portal;
import ru.deelter.portalbridge.portal.PortalDisplayUpdater;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class PortalCommand implements CommandExecutor, TabCompleter {

	private record HostPort(String host, int port) {
		String address() {
			return port == 25565 ? host : host + ":" + port;
		}
	}

	private final PortalBridgePlugin plugin;
	private final Set<UUID> processingSet = ConcurrentHashMap.newKeySet();
	private final Cache<String, Boolean> commandCooldown = Caffeine.newBuilder()
			.expireAfterWrite(2, TimeUnit.SECONDS)
			.build();

	@Override
	public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] arguments) {
		if (!(sender instanceof Player player)) return true;

		if (arguments.length == 0) {
			sendHelp(player);
			return true;
		}

		switch (arguments[0].toLowerCase()) {
			case "open" -> handleOpen(player, arguments);
			case "bind" -> handleBind(player, arguments);
			case "hub", "lobby" -> handleHub(player);
			case "admin" -> handleAdmin(player, arguments);
			case "help" -> sendHelp(player);
			case "force" -> handleForce(player, arguments);
			default -> handleLegacyOpen(player, arguments);
		}
		return true;
	}

	private void handleOpen(Player player, String[] arguments) {
		if (arguments.length < 2) {
			player.sendMessage(plugin.getLang().getMessage("usage-open", player));
			return;
		}
		HostPort hostPort = parseHostPort(arguments[1]);
		if (hostPort == null) {
			player.sendMessage(plugin.getLang().getMessage("invalid-port", player));
			return;
		}
		initiatePortal(player, hostPort);
	}

	private void handleBind(Player player, String[] arguments) {
		if (!player.hasPermission("portalbridge.bind")) {
			player.sendMessage(plugin.getLang().getMessage("no-permission", player));
			return;
		}
		if (arguments.length < 2) {
			player.sendMessage(plugin.getLang().getMessage("usage-bind", player));
			return;
		}
		var item = player.getInventory().getItemInMainHand();
		var bindManager = plugin.getDoorBindManager();
		if (!bindManager.isDoor(item)) {
			player.sendMessage(plugin.getLang().getMessage("bind-no-door", player));
			return;
		}
		HostPort hostPort = parseHostPort(arguments[1]);
		if (hostPort == null) {
			player.sendMessage(plugin.getLang().getMessage("invalid-port", player));
			return;
		}
		bindManager.bind(item, hostPort.host(), hostPort.port());
		player.sendMessage(plugin.getLang().getMessage("bind-success", player, "target", hostPort.address()));
	}

	private void handleHub(Player player) {
		var configuration = plugin.getConfigManager();
		player.sendMessage(plugin.getLang().getMessage("hub-returning", player));
		player.transfer(configuration.getHubHost(), configuration.getHubPort());
	}

	private void handleAdmin(Player player, String[] arguments) {
		if (!player.hasPermission("portalbridge.admin")) {
			player.sendMessage(plugin.getLang().getMessage("no-permission", player));
			return;
		}
		if (arguments.length < 2) return;
		switch (arguments[1].toLowerCase()) {
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

	private void handleForce(Player player, String[] arguments) {
		if (arguments.length < 2) {
			player.sendMessage(plugin.getLang().getMessage("usage-force", player));
			return;
		}
		HostPort hostPort = parseHostPort(arguments[1]);
		if (hostPort == null) {
			player.sendMessage(plugin.getLang().getMessage("invalid-port", player));
			return;
		}
		plugin.getConsentCache().grantConsent(player.getUniqueId(), hostPort.host(), hostPort.port());
		player.transfer(hostPort.host(), hostPort.port());
	}

	private void sendHelp(Player player) {
		Lang language = plugin.getLang();
		player.sendMessage(language.getMessage("help-header", player));
		player.sendMessage(language.getMessage("help-open", player));
		if (player.hasPermission("portalbridge.bind")) {
			player.sendMessage(language.getMessage("help-bind", player));
		}
		player.sendMessage(language.getMessage("help-hub", player));
		if (player.hasPermission("portalbridge.admin")) {
			player.sendMessage(language.getMessage("help-admin", player));
		}
	}

	private void handleLegacyOpen(Player player, String[] arguments) {
		HostPort hostPort = parseHostPort(arguments[0]);
		if (hostPort == null) {
			player.sendMessage(plugin.getLang().getMessage("invalid-port", player));
			return;
		}
		initiatePortal(player, hostPort);
	}

	private void initiatePortal(Player player, HostPort hostPort) {
		String cooldownKey = player.getUniqueId() + ":" + hostPort.host() + ":" + hostPort.port();
		if (commandCooldown.getIfPresent(cooldownKey) != null) {
			player.sendMessage(plugin.getLang().getMessage("please-wait", player));
			return;
		}
		if (!processingSet.add(player.getUniqueId())) return;

		int lifetimeSeconds = plugin.getConfigManager().getPortalLifetimeSeconds();
		Portal portal = plugin.getPortalManager().createPortal(player, hostPort.host(), hostPort.port(), lifetimeSeconds, null, null);
		if (portal == null) {
			processingSet.remove(player.getUniqueId());
			return;
		}
		commandCooldown.put(cooldownKey, true);
		player.sendMessage(plugin.getLang().getMessage("portal-created", player, "target", hostPort.address()));

		// Асинхронное обновление информации о сервере и голограммы
		plugin.getServerPinger().ping(hostPort.host(), hostPort.port()).thenAccept(serverInfo -> {
			Bukkit.getScheduler().runTask(plugin, () -> updatePortalWithInfo(portal, serverInfo, player, lifetimeSeconds));
			processingSet.remove(player.getUniqueId());
		});
	}

	private void updatePortalWithInfo(@NonNull Portal portal, ServerInfo serverInfo, @NonNull Player player, int lifetimeSeconds) {
		portal.setCachedInfo(serverInfo);
		long remaining = (portal.getExpiryTime() - System.currentTimeMillis()) / 1000;
		if (remaining < 0) remaining = 0;
		PortalDisplayUpdater.update(portal, serverInfo,
				plugin.getConfigManager().getHologramFormat(),
				plugin.getConfigManager().getHologramFormatUnreached(),
				player.getName(),
				(int) remaining,
				lifetimeSeconds);
	}

	@Nullable
	private HostPort parseHostPort(@NonNull String argument) {
		String host;
		int port;
		if (argument.contains(":")) {
			String[] split = argument.split(":", 2);
			host = split[0];
			try {
				port = Integer.parseInt(split[1]);
			} catch (NumberFormatException exception) {
				return null;
			}
		} else {
			host = argument;
			port = 25565;
		}
		if (port < 1 || port > 65535) return null;
		return new HostPort(host, port);
	}

	@Override
	public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] arguments) {
		if (!(sender instanceof Player player)) return List.of();

		if (arguments.length == 1) {
			List<String> suggestions = new ArrayList<>(List.of("open", "hub", "lobby", "help", "bind"));
			if (player.hasPermission("portalbridge.admin")) suggestions.add("admin");
			return suggestions.stream()
					.filter(suggestion -> suggestion.startsWith(arguments[0].toLowerCase()))
					.toList();
		}

		if (arguments.length == 2 && arguments[0].equalsIgnoreCase("admin") && player.hasPermission("portalbridge.admin")) {
			return List.of("reload", "removeall").stream()
					.filter(subCommand -> subCommand.startsWith(arguments[1].toLowerCase()))
					.toList();
		}

		return List.of();
	}
}