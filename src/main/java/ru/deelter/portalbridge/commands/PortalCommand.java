package ru.deelter.portalbridge.commands;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import ru.deelter.portalbridge.PortalBridgePlugin;
import ru.deelter.portalbridge.lang.Lang;
import ru.deelter.portalbridge.portal.Portal;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PortalCommand implements CommandExecutor, TabCompleter {

	private record HostPort(String host, int port) {
		String address() { return port == 25565 ? host : host + ":" + port; }
	}

	private final PortalBridgePlugin plugin;
	private final Set<UUID> processingSet = ConcurrentHashMap.newKeySet();
	private final Cache<String, Boolean> commandCooldown;

	public PortalCommand(PortalBridgePlugin plugin) {
		this.plugin = plugin;
		this.commandCooldown = Caffeine.newBuilder()
				.expireAfterWrite(plugin.getConfigManager().getCommandCooldownSeconds(), TimeUnit.SECONDS)
				.build();
	}

	@Override
	public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
		if (!(sender instanceof Player player)) return true;
		if (args.length == 0) { sendHelp(player); return true; }

		switch (args[0].toLowerCase()) {
			case "open"          -> handleOpen(player, args);
			case "bind"          -> handleBind(player, args);
			case "hub", "lobby"  -> handleHub(player);
			case "admin"         -> handleAdmin(player, args);
			case "help"          -> sendHelp(player);
			case "force"         -> handleForce(player, args);
			default              -> handleLegacyOpen(player, args);
		}
		return true;
	}

	private void handleOpen(Player player, String[] args) {
		if (args.length < 2) { player.sendMessage(plugin.getLang().getMessage("usage-open", player)); return; }
		HostPort hp = parseHostPort(args[1]);
		if (hp == null) { player.sendMessage(plugin.getLang().getMessage("invalid-port", player)); return; }
		initiatePortal(player, hp);
	}

	private void handleBind(Player player, String[] args) {
		if (!player.hasPermission("portalbridge.bind")) {
			player.sendMessage(plugin.getLang().getMessage("no-permission", player));
			return;
		}
		if (args.length < 2) { player.sendMessage(plugin.getLang().getMessage("usage-bind", player)); return; }
		var item = player.getInventory().getItemInMainHand();
		var bindManager = plugin.getDoorBindManager();
		if (!bindManager.isDoor(item)) { player.sendMessage(plugin.getLang().getMessage("bind-no-door", player)); return; }
		HostPort hp = parseHostPort(args[1]);
		if (hp == null) { player.sendMessage(plugin.getLang().getMessage("invalid-port", player)); return; }
		bindManager.bind(item, hp.host(), hp.port());
		player.sendMessage(plugin.getLang().getMessage("bind-success", player, "target", hp.address()));
	}

	private void handleHub(Player player) {
		player.sendMessage(plugin.getLang().getMessage("hub-returning", player));
		player.transfer(plugin.getConfigManager().getHubHost(), plugin.getConfigManager().getHubPort());
	}

	private void handleAdmin(Player player, String[] args) {
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

	private void handleForce(Player player, String[] args) {
		if (args.length < 2) { player.sendMessage(plugin.getLang().getMessage("usage-force", player)); return; }
		HostPort hp = parseHostPort(args[1]);
		if (hp == null) { player.sendMessage(plugin.getLang().getMessage("invalid-port", player)); return; }
		plugin.getConsentCache().grantConsent(player.getUniqueId(), hp.host(), hp.port());
		player.transfer(hp.host(), hp.port());
	}

	private void sendHelp(Player player) {
		Lang lang = plugin.getLang();
		player.sendMessage(lang.getMessage("help-header", player));
		player.sendMessage(lang.getMessage("help-open", player));
		if (player.hasPermission("portalbridge.bind"))  player.sendMessage(lang.getMessage("help-bind", player));
		player.sendMessage(lang.getMessage("help-hub", player));
		if (player.hasPermission("portalbridge.admin")) player.sendMessage(lang.getMessage("help-admin", player));
	}

	private void handleLegacyOpen(Player player, String[] args) {
		HostPort hp = parseHostPort(args[0]);
		if (hp == null) { player.sendMessage(plugin.getLang().getMessage("invalid-port", player)); return; }
		initiatePortal(player, hp);
	}

	private void initiatePortal(Player player, HostPort hp) {
		if (plugin.getTrustListManager().isBlacklisted(hp.host(), hp.port())) {
			player.sendMessage(plugin.getLang().getMessage("server-blacklisted", player));
			return;
		}

		String cooldownKey = player.getUniqueId() + ":" + hp.host() + ":" + hp.port();
		if (commandCooldown.getIfPresent(cooldownKey) != null) {
			player.sendMessage(plugin.getLang().getMessage("please-wait", player));
			return;
		}
		if (!processingSet.add(player.getUniqueId())) return;

		int lifetimeSec = plugin.getConfigManager().getPortalLifetimeSeconds();
		Portal portal = plugin.getPortalManager().createPortal(player, hp.host(), hp.port(), lifetimeSec, null, null);
		processingSet.remove(player.getUniqueId());
		if (portal == null) return;

		commandCooldown.put(cooldownKey, true);
		player.sendMessage(plugin.getLang().getMessage("portal-created", player, "target", hp.address()));
		plugin.getPortalManager().startPingAndUpdateHologram(portal, lifetimeSec);
	}

	@Nullable
	private HostPort parseHostPort(@NonNull String arg) {
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
		return new HostPort(host, port);
	}

	@Override
	public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
		if (!(sender instanceof Player player)) return List.of();

		if (args.length == 1) {
			List<String> subs = new ArrayList<>(List.of("open", "hub", "lobby", "help", "bind"));
			if (player.hasPermission("portalbridge.admin")) subs.add("admin");
			return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
		}

		if (args.length == 2 && args[0].equalsIgnoreCase("admin") && player.hasPermission("portalbridge.admin")) {
			return List.of("reload", "removeall").stream()
					.filter(s -> s.startsWith(args[1].toLowerCase())).toList();
		}

		return List.of();
	}
}
