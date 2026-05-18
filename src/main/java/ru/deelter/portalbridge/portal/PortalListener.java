package ru.deelter.portalbridge.portal;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.util.Transformation;
import org.jspecify.annotations.NonNull;
import ru.deelter.portalbridge.PortalBridgePlugin;
import ru.deelter.portalbridge.flags.ServerFlag;
import ru.deelter.portalbridge.pinger.ServerInfo;
import ru.deelter.portalbridge.utils.ConsentCache;

import java.util.UUID;

@RequiredArgsConstructor
public class PortalListener implements Listener {

	private final PortalBridgePlugin plugin;

	@EventHandler
	public void onEntityInteract(@NonNull PlayerInteractEntityEvent event) {
		if (!(event.getRightClicked() instanceof Interaction interaction)) return;

		Portal portal = plugin.getPortalManager().getPortalByEntity(interaction);
		if (portal == null || portal.isExpired()) return;

		event.setCancelled(true);
		Player player = event.getPlayer();
		ServerInfo info = portal.getCachedInfo();

		if (info == ServerInfo.UNREACHABLE || info == ServerInfo.EMPTY) {
			openPortal(player, portal);
			return;
		}

		if (info == null) {
			plugin.getServerPinger().ping(portal.getTargetHost(), portal.getTargetPort())
					.thenAccept(receivedInfo -> {
						portal.setCachedInfo(receivedInfo);
						if (receivedInfo == ServerInfo.UNREACHABLE || receivedInfo == ServerInfo.EMPTY) {
							Bukkit.getScheduler().runTask(plugin, () -> openPortal(player, portal));
							return;
						}

						boolean hasDataFromApi = receivedInfo.getMotd() != null && !receivedInfo.getMotd().isEmpty();
						boolean isAccepting = isServerAccepting(receivedInfo);

						// Нет данных от API и сервер не принимает transfer -> блокируем
						if (!hasDataFromApi && !isAccepting) {
							player.sendMessage(plugin.getLang().getMessage("server-not-accepting-transfers", player));
							return;
						}

						// Есть данные от API, но нет флага PortalBridge -> предупреждение, но переход разрешён
						if (hasDataFromApi && !receivedInfo.hasPortalBridge()) {
							player.sendMessage(plugin.getLang().getMessage("server-no-portalbridge", player));
						}

						Bukkit.getScheduler().runTask(plugin, () -> openPortal(player, portal));
					});
		} else {
			boolean hasDataFromApi = info.getMotd() != null && !info.getMotd().isEmpty();
			boolean isAccepting = isServerAccepting(info);

			if (!hasDataFromApi && !isAccepting) {
				player.sendMessage(plugin.getLang().getMessage("server-not-accepting-transfers", player));
				return;
			}

			if (hasDataFromApi && !info.hasPortalBridge()) {
				player.sendMessage(plugin.getLang().getMessage("server-no-portalbridge", player));
			}

			openPortal(player, portal);
		}
	}

	private boolean isServerAccepting(@NonNull ServerInfo info) {
		return info.hasFlag(ServerFlag.PROXY) || info.hasFlag(ServerFlag.TRANSFERS);
	}

	private void openPortal(Player player, Portal portal) {
		if (!portal.isOpen()) {
			applyDoorAnimation(portal, true);
			plugin.getConfigManager().getOpenSound().play(portal.getLowerDoorLoc());
			portal.setOpen(true);
		}
		if (portal.getCheckTaskId() == -1) startCheckTask(portal);
		scheduleAutoClose(portal);
	}

	private void startCheckTask(Portal portal) {
		int checkId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			if (portal.isExpired()) {
				cancelCheckTask(portal);
				return;
			}
			Interaction interaction = portal.getInteraction();
			if (interaction == null) return;

			Location center = interaction.getLocation();
			double halfW = interaction.getInteractionWidth() / 2.0;
			double halfH = interaction.getInteractionHeight() / 2.0;

			for (Player player : center.getNearbyPlayers(2.5)) {
				Location loc = player.getLocation();
				if (Math.abs(loc.getX() - center.getX()) > halfW) continue;
				if (Math.abs(loc.getY() - center.getY()) > halfH) continue;
				if (Math.abs(loc.getZ() - center.getZ()) > halfW) continue;

				if (!canTransfer(player, portal)) continue;

				player.transfer(portal.getTargetHost(), portal.getTargetPort());
				notifyNearby(player, portal);
				scheduleAutoClose(portal);
			}
		}, 0L, 10L).getTaskId();
		portal.setCheckTaskId(checkId);
	}

	private boolean canTransfer(Player player, Portal portal) {
		ServerInfo info = portal.getCachedInfo();
		if (info == null) {
			if (plugin.getConfigManager().isDebug()) {
				plugin.getLogger().info("canTransfer: info is null for " + portal.getTargetHost() + " - allowing transfer");
			}
			return true;
		}

		if (info == ServerInfo.UNREACHABLE || info == ServerInfo.EMPTY) {
			if (plugin.getConfigManager().isDebug()) {
				plugin.getLogger().info("canTransfer: server " + portal.getTargetHost() + " is unreachable/empty - allowing transfer");
			}
			return true;
		}

		boolean hasAuth = info.hasFlag(ServerFlag.AUTH);
		boolean isWhitelisted = plugin.getTrustListManager().isWhitelisted(portal.getTargetHost(), portal.getTargetPort());

		if (plugin.getConfigManager().isDebug()) {
			plugin.getLogger().info("canTransfer: " + player.getName() + " -> " + portal.getTargetHost() +
					" hasAuth=" + hasAuth +
					" isWhitelisted=" + isWhitelisted);
		}

		if (!hasAuth) return true;
		if (isWhitelisted) return true;

		ConsentCache consent = plugin.getConsentCache();
		UUID playerId = player.getUniqueId();
		String host = portal.getTargetHost();
		int port = portal.getTargetPort();

		if (consent.hasConsent(playerId, host, port)) {
			if (plugin.getConfigManager().isDebug()) {
				plugin.getLogger().info("canTransfer: player has consent for " + host);
			}
			return true;
		}

		if (!consent.isOnCooldown(playerId, host, port)) {
			String commandText = "/portal force " + host + " " + port;
			Component warning = plugin.getLang().getMessage(
					"untrusted-warning", player,
					Placeholder.unparsed("command", commandText)
			);
			player.sendMessage(warning);
			consent.setCooldown(playerId, host, port);
		}
		return false;
	}

	private void notifyNearby(Player whoEntered, @NonNull Portal portal) {
		String host = portal.getTargetHost();
		int port = portal.getTargetPort();
		String address = port == 25565 ? host : host + ":" + port;
		Component message = plugin.getLang().getMessage(
				"player-entered-portal", whoEntered,
				Placeholder.unparsed("player", whoEntered.getName()),
				Placeholder.unparsed("target", address));
		if (message == null) return;
		for (Player nearby : portal.getLowerDoorLoc().getNearbyPlayers(12))
			nearby.sendActionBar(message);
	}

	private void scheduleAutoClose(Portal portal) {
		if (portal.getAutoCloseTaskId() != -1) {
			Bukkit.getScheduler().cancelTask(portal.getAutoCloseTaskId());
			portal.setAutoCloseTaskId(-1);
		}
		int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
			if (portal.isOpen() && !portal.isExpired()) {
				applyDoorAnimation(portal, false);
				plugin.getConfigManager().getCloseSound().play(portal.getLowerDoorLoc());
				portal.setOpen(false);
				cancelCheckTask(portal);
			}
		}, 100L).getTaskId();
		portal.setAutoCloseTaskId(taskId);
	}

	private void cancelCheckTask(@NonNull Portal portal) {
		if (portal.getCheckTaskId() != -1) {
			Bukkit.getScheduler().cancelTask(portal.getCheckTaskId());
			portal.setCheckTaskId(-1);
		}
	}

	void applyDoorAnimation(Portal portal, boolean toOpen) {
		BlockDisplay lower = portal.getLowerDisplay();
		BlockDisplay upper = portal.getUpperDisplay();
		if (lower == null || upper == null) return;

		Door doorData = (Door) lower.getBlock();
		BlockFace facing = doorData.getFacing();
		boolean leftHinge = doorData.getHinge() == Door.Hinge.LEFT;

		portal.setOpen(toOpen);

		if (portal.getAnimTaskId() != -1) {
			Bukkit.getScheduler().cancelTask(portal.getAnimTaskId());
			portal.setAnimTaskId(-1);
		}

		final int totalTicks = plugin.getConfigManager().getOpenTicks();
		final float openAngle = leftHinge ? 90f : -90f;
		final float startAngle = toOpen ? 0f : openAngle;
		final float endAngle = toOpen ? openAngle : 0f;

		final int[] tick = {0};
		int id = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			tick[0]++;
			float angle = startAngle + (endAngle - startAngle) * Math.min(1f, (float) tick[0] / totalTicks);
			Transformation t = DoorAnimator.buildTransform(facing, leftHinge, angle);

			lower.setInterpolationDelay(0);
			lower.setInterpolationDuration(1);
			lower.setTransformation(t);
			upper.setInterpolationDelay(0);
			upper.setInterpolationDuration(1);
			upper.setTransformation(t);

			if (tick[0] >= totalTicks) {
				int taskId = portal.getAnimTaskId();
				portal.setAnimTaskId(-1);
				if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
			}
		}, 1L, 1L).getTaskId();
		portal.setAnimTaskId(id);
	}
}