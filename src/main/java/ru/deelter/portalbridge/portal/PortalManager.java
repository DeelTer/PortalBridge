package ru.deelter.portalbridge.portal;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;
import ru.deelter.portalbridge.PortalBridgePlugin;
import ru.deelter.portalbridge.pinger.ServerInfo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class PortalManager {

    private final PortalBridgePlugin plugin;
    private final Map<Location, Portal> portalsByLowerLoc = new ConcurrentHashMap<>();
    private final Map<UUID, Portal> portalsByEntityId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerPortalCount = new ConcurrentHashMap<>();

    public boolean canPlace(Player player, Location targetBlock) {
        if (targetBlock == null) return false;

        for (Location loc : portalsByLowerLoc.keySet()) {
            if (loc.distance(targetBlock) < plugin.getConfigManager().getCollisionRadius()) return false;
        }

        double radius = plugin.getConfigManager().getPlayerProximityRadius();
        boolean nearbyPlayer = targetBlock.getNearbyPlayers(radius).stream()
                .anyMatch(p -> !p.equals(player));

        if (nearbyPlayer) return false;

        int max = plugin.getConfigManager().getMaxPortalsPerPlayer();
        int current = playerPortalCount.getOrDefault(player.getUniqueId(), 0);
        return current < max;
    }

    public void createPortal(Player player, String targetHost, int targetPort, int lifetimeSec) {
        Location targetBlock = player.getTargetBlock(null, plugin.getConfigManager().getMaxPlacementDistance()).getLocation();

        if (targetBlock.getBlock().isEmpty() || !canPlace(player, targetBlock)) {
            player.sendMessage(plugin.getLang().getMessage("cannot-place-near-player", player));
            return;
        }

        long expiry = System.currentTimeMillis() + lifetimeSec * 1000L;
        Portal portal = new Portal(targetBlock, targetHost, targetPort, expiry, player.getUniqueId());
        portalsByLowerLoc.put(targetBlock, portal);

        // Spawn at exact block corner — BlockDisplay's origin is the corner of its location.
        Location lowerLoc = targetBlock.clone().add(0, 1, 0);
        Location upperLoc = targetBlock.clone().add(0, 2, 0);

        BlockDisplay lower = lowerLoc.getWorld().spawn(lowerLoc, BlockDisplay.class);
        BlockDisplay upper = upperLoc.getWorld().spawn(upperLoc, BlockDisplay.class);

        // Door panel sits on the face nearest the player so they walk INTO it.
        BlockFace facing = player.getFacing();
        Door.Hinge hinge = pickHinge(player, targetBlock, facing);

        Door lowerData = (Door) Bukkit.createBlockData(Material.SPRUCE_DOOR);
        lowerData.setHalf(Bisected.Half.BOTTOM);
        lowerData.setFacing(facing);
        lowerData.setHinge(hinge);
        lowerData.setOpen(false);

        Door upperData = (Door) Bukkit.createBlockData(Material.SPRUCE_DOOR);
        upperData.setHalf(Bisected.Half.TOP);
        upperData.setFacing(facing);
        upperData.setHinge(hinge);
        upperData.setOpen(false);

        lower.setBlock(lowerData);
        upper.setBlock(upperData);

        // Apply correct closed-state transform immediately so first render lines up with target block.
        Transformation closedTrans = PortalListener.buildDoorTransform(facing, hinge == Door.Hinge.LEFT, false);

        for (BlockDisplay d : new BlockDisplay[]{lower, upper}) {
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(closedTrans);
            d.setPersistent(false);
            portalsByEntityId.put(d.getUniqueId(), portal);
        }

        portal.setLowerDisplay(lower);
        portal.setUpperDisplay(upper);

        // Interaction
        Location interactionLoc = targetBlock.clone().add(0.5, 1.0, 0.5);
        Interaction interaction = interactionLoc.getWorld().spawn(interactionLoc, Interaction.class);
        interaction.setInteractionWidth(1.4f);
        interaction.setInteractionHeight(2.4f);
        interaction.setResponsive(true);
        interaction.setPersistent(false);
        portalsByEntityId.put(interaction.getUniqueId(), portal);
        portal.setInteraction(interaction);

        // Hologram — above the top of the upper door block, visible through walls.
        TextDisplay hologram = targetBlock.getWorld().spawn(
                targetBlock.clone().add(0.5, 3.4, 0.5), TextDisplay.class);
        hologram.setDefaultBackground(false);
        hologram.setBillboard(Display.Billboard.CENTER);
        hologram.setSeeThrough(true);
        hologram.setShadowed(true);
        hologram.setPersistent(false);
        portal.setHologram(hologram);

        playerPortalCount.merge(player.getUniqueId(), 1, Integer::sum);

        plugin.getServerPinger().ping(targetHost, targetPort).thenAccept(info -> {
            portal.setCachedInfo(info);
            updateHologram(portal, info);
        });

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> removePortalAnimated(targetBlock), lifetimeSec * 20L).getTaskId();
        portal.setTaskId(taskId);
    }

    /**
     * Removes the portal with a shrink-to-point animation. Door and hologram scale
     * down toward their centers over a few ticks, then entities are removed.
     */
    public void removePortalAnimated(Location lowerLoc) {
        Portal p = portalsByLowerLoc.get(lowerLoc);
        if (p == null) return;

        if (p.getAnimTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(p.getAnimTaskId());
            p.setAnimTaskId(-1);
        }
        if (p.getCheckTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(p.getCheckTaskId());
            p.setCheckTaskId(-1);
        }
        if (p.getInteraction() != null) {
            p.getInteraction().remove();
            p.setInteraction(null);
        }

        final BlockDisplay lower = p.getLowerDisplay();
        final BlockDisplay upper = p.getUpperDisplay();
        final TextDisplay hologram = p.getHologram();

        final Transformation lowerStart = lower != null ? lower.getTransformation() : null;
        final Transformation upperStart = upper != null ? upper.getTransformation() : null;
        final Transformation hgStart    = hologram != null ? hologram.getTransformation() : null;

        final int duration = 6;
        final int[] tick = {0};
        int id = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick[0]++;
            float frac = Math.min(1f, (float) tick[0] / duration);
            float scale = Math.max(0.001f, 1f - frac);

            if (lower != null && lower.isValid() && lowerStart != null)
                applyShrink(lower, lowerStart, scale, true);
            if (upper != null && upper.isValid() && upperStart != null)
                applyShrink(upper, upperStart, scale, true);
            if (hologram != null && hologram.isValid() && hgStart != null)
                applyShrink(hologram, hgStart, scale, false);

            if (tick[0] >= duration) {
                int taskId = p.getAnimTaskId();
                p.setAnimTaskId(-1);
                if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
                removePortal(lowerLoc);
            }
        }, 1L, 1L).getTaskId();
        p.setAnimTaskId(id);
    }

    private void applyShrink(org.bukkit.entity.Display d, Transformation base, float scale, boolean centerBlock) {
        Vector3f t = new Vector3f(base.getTranslation());
        if (centerBlock) {
            float off = (1f - scale) * 0.5f;
            t.add(off, off, off);
        }
        Transformation nt = new Transformation(
                t,
                base.getLeftRotation(),
                new Vector3f(scale),
                base.getRightRotation()
        );
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(1);
        d.setTransformation(nt);
    }

    private void updateHologram(Portal portal, ServerInfo info) {
        if (portal.isExpired() || portal.getHologram() == null || !portal.getHologram().isValid()) return;

        String format = plugin.getConfig().getString("display.format",
                "<gold><motd>\n<green><online>/<max> online\n<gray>Version: <version>");

        String filled = format.replace("<motd>", info.getMotd() != null ? info.getMotd() : "Unknown")
                .replace("<online>", String.valueOf(info.getOnline()))
                .replace("<max>", String.valueOf(info.getMax()))
                .replace("<version>", info.getVersion() != null ? info.getVersion() : "?");

        Component text = plugin.getLang().getMiniMessage().deserialize(filled);
        portal.getHologram().text(text);
    }

    public void removePortal(Location lowerLoc) {
        Portal p = portalsByLowerLoc.remove(lowerLoc);
        if (p != null) {
            if (p.getLowerDisplay() != null) p.getLowerDisplay().remove();
            if (p.getUpperDisplay() != null) p.getUpperDisplay().remove();
            if (p.getInteraction() != null) p.getInteraction().remove();
            if (p.getHologram() != null) p.getHologram().remove();

            portalsByEntityId.values().removeIf(portal -> portal == p);
            playerPortalCount.computeIfPresent(p.getOwner(), (uuid, count) -> count <= 1 ? null : count - 1);

            if (p.getTaskId() != -1) Bukkit.getScheduler().cancelTask(p.getTaskId());
            if (p.getAnimTaskId() != -1) Bukkit.getScheduler().cancelTask(p.getAnimTaskId());
            if (p.getCheckTaskId() != -1) Bukkit.getScheduler().cancelTask(p.getCheckTaskId());
        }
    }

    public void removeAllPortals() {
        portalsByLowerLoc.keySet().forEach(this::removePortal);
        portalsByLowerLoc.clear();
        portalsByEntityId.clear();
        playerPortalCount.clear();
    }

    public Portal getPortalByEntity(org.bukkit.entity.Entity entity) {
        return portalsByEntityId.get(entity.getUniqueId());
    }

    /**
     * Determine hinge side from player's hit point on the target block, matching
     * vanilla "click left half → right hinge" intuition. Uses ray-trace hit point
     * along the axis perpendicular to door facing; falls back to LEFT.
     */
    private Door.Hinge pickHinge(Player player, Location targetBlock, BlockFace facing) {
        double range = plugin.getConfigManager().getMaxPlacementDistance();
        RayTraceResult hit = player.rayTraceBlocks(range);
        if (hit == null) {
			return Door.Hinge.LEFT;
        } else {
	        hit.getHitPosition();
        }

		Vector pos = hit.getHitPosition();
        // Local offset within target block, range [0, 1].
        double lx = pos.getX() - targetBlock.getBlockX();
        double lz = pos.getZ() - targetBlock.getBlockZ();

        // Project hit onto the perpendicular axis of facing.
        // "side" > 0 → hit is on the right side from player's POV looking toward facing.
        double side = switch (facing) {
            case NORTH -> 0.5 - lx;   // facing N, player N, right = west (-x)
            case SOUTH -> lx - 0.5;   // facing S, player S, right = east (+x)
            case EAST  -> 0.5 - lz;   // facing E, player E, right = north (-z)
            case WEST  -> lz - 0.5;   // facing W, player W, right = south (+z)
            default    -> 0.0;
        };
        return side > 0 ? Door.Hinge.RIGHT : Door.Hinge.LEFT;
    }
}