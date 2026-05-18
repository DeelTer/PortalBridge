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
        ServerInfo cachedInfo = portal.getCachedInfo();

        if (cachedInfo == ServerInfo.UNREACHABLE || cachedInfo == ServerInfo.EMPTY) {
            openPortal(player, portal);
            return;
        }

        if (cachedInfo == null) {
            plugin.getServerPinger().ping(portal.getTargetHost(), portal.getTargetPort())
                    .thenAccept(receivedInfo -> {
                        portal.setCachedInfo(receivedInfo);
                        if (receivedInfo == ServerInfo.UNREACHABLE || receivedInfo == ServerInfo.EMPTY) {
                            Bukkit.getScheduler().runTask(plugin, () -> openPortal(player, portal));
                            return;
                        }

                        boolean hasDataFromApi = receivedInfo.getMotd() != null && !receivedInfo.getMotd().isEmpty();
                        boolean isAccepting = isServerAccepting(receivedInfo);

                        if (!hasDataFromApi && !isAccepting) {
                            player.sendMessage(plugin.getLang().getMessage("server-not-accepting-transfers", player));
                            return;
                        }

                        if (hasDataFromApi && !receivedInfo.hasPortalBridge()) {
                            player.sendMessage(plugin.getLang().getMessage("server-no-portalbridge", player));
                        }

                        Bukkit.getScheduler().runTask(plugin, () -> openPortal(player, portal));
                    });
        } else {
            boolean hasDataFromApi = cachedInfo.getMotd() != null && !cachedInfo.getMotd().isEmpty();
            boolean isAccepting = isServerAccepting(cachedInfo);

            if (!hasDataFromApi && !isAccepting) {
                player.sendMessage(plugin.getLang().getMessage("server-not-accepting-transfers", player));
                return;
            }

            if (hasDataFromApi && !cachedInfo.hasPortalBridge()) {
                player.sendMessage(plugin.getLang().getMessage("server-no-portalbridge", player));
            }

            openPortal(player, portal);
        }
    }

    private boolean isServerAccepting(@NonNull ServerInfo serverInfo) {
        return serverInfo.hasFlag(ServerFlag.PROXY) || serverInfo.hasFlag(ServerFlag.TRANSFERS);
    }

    private void openPortal(Player player, Portal portal) {
        if (!portal.isOpen()) {
            applyDoorAnimation(portal, true);
            plugin.getConfigManager().getOpenSound().play(portal.getLowerDoorLoc());
            portal.setOpen(true);
        }
        if (portal.getCheckTaskId() == -1) startPlayerCheckTask(portal);
        scheduleAutoClose(portal);
    }

    private void startPlayerCheckTask(Portal portal) {
        int checkTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (portal.isExpired()) {
                cancelCheckTask(portal);
                return;
            }
            Interaction interaction = portal.getInteraction();
            if (interaction == null) return;

            Location center = interaction.getLocation();
            double halfWidth = interaction.getInteractionWidth() / 2.0;
            double halfHeight = interaction.getInteractionHeight() / 2.0;

            double checkRadius = plugin.getConfigManager().getPortalCheckRadius();
            for (Player nearbyPlayer : center.getNearbyPlayers(checkRadius)) {
                Location playerLocation = nearbyPlayer.getLocation();
                if (Math.abs(playerLocation.getX() - center.getX()) > halfWidth) continue;
                if (Math.abs(playerLocation.getY() - center.getY()) > halfHeight) continue;
                if (Math.abs(playerLocation.getZ() - center.getZ()) > halfWidth) continue;

                if (!canTransfer(nearbyPlayer, portal)) continue;

                nearbyPlayer.transfer(portal.getTargetHost(), portal.getTargetPort());
                notifyNearbyPlayers(nearbyPlayer, portal);
                scheduleAutoClose(portal);
            }
        }, 0L, 10L).getTaskId();
        portal.setCheckTaskId(checkTaskId);
    }

    private boolean canTransfer(Player player, Portal portal) {
        ServerInfo serverInfo = portal.getCachedInfo();
        if (serverInfo == null) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("canTransfer: server info is null for " + portal.getTargetHost() + " - allowing transfer");
            }
            return true;
        }

        if (serverInfo == ServerInfo.UNREACHABLE || serverInfo == ServerInfo.EMPTY) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("canTransfer: server " + portal.getTargetHost() + " is unreachable - allowing transfer");
            }
            return true;
        }

        boolean hasAuthPlugin = serverInfo.hasFlag(ServerFlag.AUTH);
        boolean isWhitelisted = plugin.getTrustListManager().isWhitelisted(portal.getTargetHost(), portal.getTargetPort());

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("canTransfer: " + player.getName() + " -> " + portal.getTargetHost() +
                    " hasAuthPlugin=" + hasAuthPlugin +
                    " isWhitelisted=" + isWhitelisted);
        }

        if (!hasAuthPlugin) return true;
        if (isWhitelisted) return true;

        ConsentCache consentCache = plugin.getConsentCache();
        UUID playerUniqueId = player.getUniqueId();
        String targetHost = portal.getTargetHost();
        int targetPort = portal.getTargetPort();

        if (consentCache.hasConsent(playerUniqueId, targetHost, targetPort)) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("canTransfer: player already gave consent for " + targetHost);
            }
            return true;
        }

        if (!consentCache.isOnCooldown(playerUniqueId, targetHost, targetPort)) {
            String forceCommand = "/portal force " + targetHost + " " + targetPort;
            Component warningMessage = plugin.getLang().getMessage(
                    "untrusted-warning", player,
                    Placeholder.unparsed("command", forceCommand)
            );
            player.sendMessage(warningMessage);
            consentCache.setCooldown(playerUniqueId, targetHost, targetPort);
        }
        return false;
    }

    private void notifyNearbyPlayers(Player enteringPlayer, @NonNull Portal portal) {
        String targetHost = portal.getTargetHost();
        int targetPort = portal.getTargetPort();
        String targetAddress = targetPort == 25565 ? targetHost : targetHost + ":" + targetPort;
        Component notificationMessage = plugin.getLang().getMessage(
                "player-entered-portal", enteringPlayer,
                Placeholder.unparsed("player", enteringPlayer.getName()),
                Placeholder.unparsed("target", targetAddress));
        if (notificationMessage == null) return;
        for (Player nearbyPlayer : portal.getLowerDoorLoc().getNearbyPlayers(12)) {
            nearbyPlayer.sendActionBar(notificationMessage);
        }
    }

    private void scheduleAutoClose(Portal portal) {
        if (portal.getAutoCloseTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(portal.getAutoCloseTaskId());
            portal.setAutoCloseTaskId(-1);
        }
        int autoCloseTaskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (portal.isOpen() && !portal.isExpired()) {
                applyDoorAnimation(portal, false);
                plugin.getConfigManager().getCloseSound().play(portal.getLowerDoorLoc());
                portal.setOpen(false);
                cancelCheckTask(portal);
            }
        }, 100L).getTaskId();
        portal.setAutoCloseTaskId(autoCloseTaskId);
    }

    private void cancelCheckTask(@NonNull Portal portal) {
        if (portal.getCheckTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(portal.getCheckTaskId());
            portal.setCheckTaskId(-1);
        }
    }

    void applyDoorAnimation(Portal portal, boolean toOpen) {
        BlockDisplay lowerDoorDisplay = portal.getLowerDisplay();
        BlockDisplay upperDoorDisplay = portal.getUpperDisplay();
        if (lowerDoorDisplay == null || upperDoorDisplay == null) return;

        Door doorBlockData = (Door) lowerDoorDisplay.getBlock();
        BlockFace doorFacing = doorBlockData.getFacing();
        boolean isLeftHinge = doorBlockData.getHinge() == Door.Hinge.LEFT;

        portal.setOpen(toOpen);

        if (portal.getAnimTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(portal.getAnimTaskId());
            portal.setAnimTaskId(-1);
        }

        int totalAnimationTicks = plugin.getConfigManager().getOpenTicks();
        float openAngleDegrees = isLeftHinge ? 90f : -90f;
        float startAngle = toOpen ? 0f : openAngleDegrees;
        float endAngle = toOpen ? openAngleDegrees : 0f;

        final int[] currentTick = {0};
        int animationTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            currentTick[0]++;
            float progress = Math.min(1f, (float) currentTick[0] / totalAnimationTicks);
            float angle = startAngle + (endAngle - startAngle) * progress;
            Transformation doorTransformation = DoorAnimator.buildTransform(doorFacing, isLeftHinge, angle);

            lowerDoorDisplay.setInterpolationDelay(0);
            lowerDoorDisplay.setInterpolationDuration(1);
            lowerDoorDisplay.setTransformation(doorTransformation);
            upperDoorDisplay.setInterpolationDelay(0);
            upperDoorDisplay.setInterpolationDuration(1);
            upperDoorDisplay.setTransformation(doorTransformation);

            if (currentTick[0] >= totalAnimationTicks) {
                int previousTaskId = portal.getAnimTaskId();
                portal.setAnimTaskId(-1);
                if (previousTaskId != -1) Bukkit.getScheduler().cancelTask(previousTaskId);
            }
        }, 1L, 1L).getTaskId();
        portal.setAnimTaskId(animationTaskId);
    }
}