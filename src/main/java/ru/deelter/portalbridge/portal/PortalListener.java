package ru.deelter.portalbridge.portal;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;
import ru.deelter.portalbridge.PortalBridgePlugin;
import ru.deelter.portalbridge.flags.FlagEncoder;
import ru.deelter.portalbridge.pinger.ServerInfo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class PortalListener implements Listener {

    private final PortalBridgePlugin plugin;
    private final Map<UUID, Portal> openPortals = new ConcurrentHashMap<>();

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
                    openPortal(p, portal, i);
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
               info.hasFlag(FlagEncoder.ServerFlag.TRANSFERS_ENABLED);
    }

    private void openPortal(@NonNull Player p, Portal portal, ServerInfo info) {
        applyDoorAnimation(portal, true);
        Location doorLoc = portal.getLowerDoorLoc();
        if (doorLoc != null && doorLoc.getWorld() != null) {
            doorLoc.getWorld().playSound(doorLoc, Sound.BLOCK_WOODEN_DOOR_OPEN, 1.0f, 1.0f);
        }
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
                    playCloseSound(portal);
                    plugin.getPortalManager().removePortalAnimated(portal.getLowerDoorLoc());
                    cancelCheckTask(portal);
                    break;
                }
            }
        }, 0L, 10L).getTaskId();

        portal.setCheckTaskId(checkId);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (portal.isOpen() && !portal.isExpired()) {
                closePortal(portal);
                cancelCheckTask(portal);
            }
        }, 100L);
    }

    private void closePortal(Portal portal) {
        applyDoorAnimation(portal, false);
        playCloseSound(portal);
    }

    private void playCloseSound(Portal portal) {
        Location loc = portal.getLowerDoorLoc();
        if (loc != null && loc.getWorld() != null) {
            loc.getWorld().playSound(loc, Sound.BLOCK_WOODEN_DOOR_CLOSE, 1.0f, 1.0f);
        }
    }

    private void cancelCheckTask(Portal portal) {
        if (portal.getCheckTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(portal.getCheckTaskId());
            portal.setCheckTaskId(-1);
        }
    }

    private void applyDoorAnimation(Portal portal, boolean toOpen) {
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

        final int totalTicks = 5;
        final float openAngle = leftHinge ? 90f : -90f;
        final float startAngle = toOpen ? 0f : openAngle;
        final float endAngle   = toOpen ? openAngle : 0f;

        final int[] tick = {0};
        int id = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick[0]++;
            float frac = Math.min(1f, (float) tick[0] / totalTicks);
            float angle = startAngle + (endAngle - startAngle) * frac;

            Transformation t = buildDoorTransform(facing, leftHinge, angle);
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

    /**
     * Build closed/open transformation. Convenience wrapper around the angle-based version.
     */
    public static Transformation buildDoorTransform(BlockFace facing, boolean leftHinge, boolean open) {
        float angleDeg = open ? (leftHinge ? 90f : -90f) : 0f;
        return buildDoorTransform(facing, leftHinge, angleDeg);
    }

    /**
     * Build transformation for door BlockDisplay anchored at block corner, rotated
     * by angleDeg around the hinge axis (Y). Display must be spawned at exact block position.
     * T = hinge - rot(angleDeg) * hinge  → keeps hinge point fixed at any angle.
     */
    public static Transformation buildDoorTransform(BlockFace facing, boolean leftHinge, float angleDeg) {
        Vector3f hinge = getHingePoint(facing, leftHinge);
        Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(angleDeg));

        Vector3f rotatedHinge = rot.transform(new Vector3f(hinge));
        Vector3f translation = new Vector3f(hinge).sub(rotatedHinge);
        translation.y += 0.001f;

        return new Transformation(translation, rot, new Vector3f(1f), new Quaternionf());
    }

    /**
     * Hinge axis position in [0,1]³ block-local space, matching vanilla door model
     * (panel-thickness center on the hinge edge). Values mirror the AnimatedDoors datapack.
     */
    private static Vector3f getHingePoint(BlockFace facing, boolean leftHinge) {
        final float near = 0.09375f;          // panel thickness / 2 = 3/32
        final float far  = 1f - near;         // 0.90625
        return switch (facing) {
            case NORTH -> leftHinge ? new Vector3f(near, 0f, far)  : new Vector3f(far,  0f, far);
            case SOUTH -> leftHinge ? new Vector3f(far,  0f, near) : new Vector3f(near, 0f, near);
            case EAST  -> leftHinge ? new Vector3f(near, 0f, near) : new Vector3f(near, 0f, far);
            case WEST  -> leftHinge ? new Vector3f(far,  0f, far)  : new Vector3f(far,  0f, near);
            default    -> new Vector3f(0f, 0f, 0f);
        };
    }
}