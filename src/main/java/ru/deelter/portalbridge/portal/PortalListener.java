package ru.deelter.portalbridge.portal;

import lombok.RequiredArgsConstructor;
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

@RequiredArgsConstructor
public class PortalListener implements Listener {

    private final PortalBridgePlugin plugin;

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;

        Portal portal = plugin.getPortalManager().getPortalByEntity(interaction);
        if (portal == null || portal.isExpired()) return;

        event.setCancelled(true);
        Player p = event.getPlayer();
        if (portal.isOpen()) return;

        ServerInfo info = portal.getCachedInfo();
        if (info == null) {
            plugin.getServerPinger().ping(portal.getTargetHost(), portal.getTargetPort())
                .thenAccept(i -> {
                    portal.setCachedInfo(i);
                    if (!isServerAccepting(i)) {
                        p.sendMessage(plugin.getLang().getMessage("server-not-accepting-transfers", p));
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> openPortal(p, portal, i));
                });
        } else {
            if (!isServerAccepting(info)) {
                p.sendMessage(plugin.getLang().getMessage("server-not-accepting-transfers", p));
                return;
            }
            openPortal(p, portal, info);
        }
    }

    private boolean isServerAccepting(@NonNull ServerInfo info) {
        return info.hasFlag(FlagEncoder.ServerFlag.PROXY) ||
               info.hasFlag(FlagEncoder.ServerFlag.TRANSFERS);
    }

    private void openPortal(@NonNull Player p, Portal portal, ServerInfo info) {
        applyDoorAnimation(portal, true);
        plugin.getConfigManager().getOpenSound().play(portal.getLowerDoorLoc());
        portal.setOpen(true);

        int checkId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (portal.isExpired() || !portal.isOpen()) {
                cancelCheckTask(portal);
                return;
            }
            Interaction interaction = portal.getInteraction();
            if (interaction == null) return;

            Location center = interaction.getLocation();
            double halfW = interaction.getInteractionWidth() / 2.0;
            double halfH = interaction.getInteractionHeight() / 2.0;

            for (Player player : Bukkit.getOnlinePlayers()) {
                Location loc = player.getLocation();
                if (loc.getWorld().equals(center.getWorld()) &&
                    Math.abs(loc.getX() - center.getX()) <= halfW &&
                    Math.abs(loc.getY() - center.getY()) <= halfH &&
                    Math.abs(loc.getZ() - center.getZ()) <= halfW) {

                    player.transfer(portal.getTargetHost(), portal.getTargetPort());
                    plugin.getConfigManager().getCloseSound().play(portal.getLowerDoorLoc());
                    plugin.getPortalManager().removePortalAnimated(portal.getLowerDoorLoc());
                    cancelCheckTask(portal);
                    break;
                }
            }
        }, 0L, 10L).getTaskId();

        portal.setCheckTaskId(checkId);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (portal.isOpen() && !portal.isExpired()) {
                applyDoorAnimation(portal, false);
                plugin.getConfigManager().getCloseSound().play(portal.getLowerDoorLoc());
                portal.setOpen(false);
                cancelCheckTask(portal);
            }
        }, 100L);
    }

    private void cancelCheckTask(Portal portal) {
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
        final float endAngle   = toOpen ? openAngle : 0f;

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
