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
import ru.deelter.portalbridge.flags.FlagEncoder;
import ru.deelter.portalbridge.pinger.ServerInfo;

import java.util.Collection;

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
			openPortal(player, portal, info);
			return;
		}

		if (info == null) {
			plugin.getServerPinger().ping(portal.getTargetHost(), portal.getTargetPort())
					.thenAccept(receivedInfo -> {
						portal.setCachedInfo(receivedInfo);
						if (receivedInfo == ServerInfo.UNREACHABLE || receivedInfo == ServerInfo.EMPTY) {
							Bukkit.getScheduler().runTask(plugin, () -> openPortal(player, portal, receivedInfo));
							return;
						}
						if (!isServerAccepting(receivedInfo)) {
							player.sendMessage(plugin.getLang().getMessage("server-not-accepting-transfers", player));
							return;
						}
						Bukkit.getScheduler().runTask(plugin, () -> openPortal(player, portal, receivedInfo));
					});
		} else {
			if (!isServerAccepting(info)) {
				player.sendMessage(plugin.getLang().getMessage("server-not-accepting-transfers", player));
				return;
			}
			openPortal(player, portal, info);
		}
	}

	private boolean isServerAccepting(@NonNull ServerInfo info) {
		return info.hasFlag(FlagEncoder.ServerFlag.PROXY) ||
				info.hasFlag(FlagEncoder.ServerFlag.TRANSFERS);
	}

	private void openPortal(@NonNull Player player, Portal portal, ServerInfo info) {
		// Открыть дверь, если она закрыта
		if (!portal.isOpen()) {
			applyDoorAnimation(portal, true);
			plugin.getConfigManager().getOpenSound().play(portal.getLowerDoorLoc());
			portal.setOpen(true);
		}

		if (portal.getCheckTaskId() == -1) {
			startCheckTask(portal);
		}

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

			Collection<Player> nearbyPlayers = center.getNearbyPlayers(2.5);
			for (Player onlinePlayer : nearbyPlayers) {
				Location playerLoc = onlinePlayer.getLocation();
				if (Math.abs(playerLoc.getX() - center.getX()) <= halfW &&
						Math.abs(playerLoc.getY() - center.getY()) <= halfH &&
						Math.abs(playerLoc.getZ() - center.getZ()) <= halfW) {

					ServerInfo info = portal.getCachedInfo();
					boolean hasAuth = info != null && info.hasFlag(FlagEncoder.ServerFlag.AUTH);
					boolean isWhitelisted = plugin.getTrustListManager().isWhitelisted(portal.getTargetHost(), portal.getTargetPort());
					boolean hasConsent = plugin.getConsentCache().hasConsent(onlinePlayer.getUniqueId(), portal.getTargetHost(), portal.getTargetPort());

					if (hasAuth && !isWhitelisted && !hasConsent) {
						boolean onCooldown = plugin.getConsentCache().isOnCooldown(onlinePlayer.getUniqueId(), portal.getTargetHost(), portal.getTargetPort());
						if (onCooldown) {
							String commandText = "/portal force " + portal.getTargetHost() + " " + portal.getTargetPort();
							Component warning = plugin.getLang().getMessage(
									"untrusted-warning",
									onlinePlayer,
									Placeholder.unparsed("command", commandText)
							);
							onlinePlayer.sendMessage(warning);
							plugin.getConsentCache().setCooldown(onlinePlayer.getUniqueId(), portal.getTargetHost(), portal.getTargetPort());
							continue;
						}
					}
					onlinePlayer.transfer(portal.getTargetHost(), portal.getTargetPort());
					sendActionBarMessage(onlinePlayer, portal);
					scheduleAutoClose(portal);
				}
			}
		}, 0L, 10L).getTaskId();
		portal.setCheckTaskId(checkId);
	}

	private void scheduleAutoClose(Portal portal) {
		// Отменить предыдущий таймер авто-закрытия
		if (portal.getAutoCloseTaskId() != -1) {
			Bukkit.getScheduler().cancelTask(portal.getAutoCloseTaskId());
			portal.setAutoCloseTaskId(-1);
		}

		// Запланировать закрытие через 5 секунд (100 тиков)
		int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
			if (portal.isOpen() && !portal.isExpired()) {
				applyDoorAnimation(portal, false);
				plugin.getConfigManager().getCloseSound().play(portal.getLowerDoorLoc());
				portal.setOpen(false);
				// Остановить таймер проверки, пока дверь закрыта
				cancelCheckTask(portal);
			}
		}, 100L).getTaskId();
		portal.setAutoCloseTaskId(taskId);
	}

	private void sendActionBarMessage(Player whoEntered, @NonNull Portal portal) {
		String host = portal.getTargetHost();
		int port = portal.getTargetPort();
		String address = port == 25565 ? host : host + ":" + port;

		Component message = plugin.getLang().getMessage(
				"player-entered-portal",
				whoEntered,
				Placeholder.unparsed("player", whoEntered.getName()),
				Placeholder.unparsed("target", address)
		);
		if (message == null) return;
		for (Player nearbyPlayer : portal.getLowerDoorLoc().getNearbyPlayers(12)) {
			nearbyPlayer.sendActionBar(message);
		}
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
			float frac = Math.min(1f, (float) tick[0] / totalTicks);
			float angle = startAngle + (endAngle - startAngle) * frac;

			Transformation t = DoorAnimator.buildTransform(facing, leftHinge, angle);
			lower.setInterpolationDelay(0);
			upper.setInterpolationDelay(0);
			lower.setInterpolationDuration(1);
			upper.setInterpolationDuration(1);
			lower.setTransformation(t);
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