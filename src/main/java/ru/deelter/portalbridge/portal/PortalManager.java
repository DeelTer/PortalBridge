package ru.deelter.portalbridge.portal;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
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
import org.joml.Vector3f;
import ru.deelter.portalbridge.PortalBridgePlugin;
import ru.deelter.portalbridge.config.ConfigManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class PortalManager {

    public enum PlaceResult { OK, NO_BLOCK, TOO_CLOSE_PORTAL, TOO_CLOSE_PLAYER, MAX_PORTALS }

    private final PortalBridgePlugin plugin;
    private final Map<Location, Portal> portalsByLowerLoc = new ConcurrentHashMap<>();
    private final Map<UUID, Portal> portalsByEntityId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerPortalCount = new ConcurrentHashMap<>();

    public PlaceResult canPlace(Player player, Location targetBlock) {
        ConfigManager cfg = plugin.getConfigManager();
        int max = cfg.getMaxPortalsPerPlayer();
        if (playerPortalCount.getOrDefault(player.getUniqueId(), 0) >= max)
            return PlaceResult.MAX_PORTALS;

        for (Location loc : portalsByLowerLoc.keySet())
            if (loc.distance(targetBlock) < cfg.getCollisionRadius())
                return PlaceResult.TOO_CLOSE_PORTAL;

        boolean nearPlayer = targetBlock.getNearbyPlayers(cfg.getPlayerProximityRadius()).stream()
                .anyMatch(p -> !p.equals(player));
        if (nearPlayer) return PlaceResult.TOO_CLOSE_PLAYER;

        return PlaceResult.OK;
    }

    /** Returns null on success, or a lang key describing the failure. */
    public String createPortal(Player player, String targetHost, int targetPort, int lifetimeSec) {
        ConfigManager cfg = plugin.getConfigManager();

        RayTraceResult hit = player.rayTraceBlocks(cfg.getMaxPlacementDistance());
        if (hit == null || hit.getHitBlock() == null || hit.getHitBlock().isEmpty())
            return "must-look-at-block";

        Block targetBlock = hit.getHitBlock();
        Location targetLoc = targetBlock.getLocation();

        PlaceResult result = canPlace(player, targetLoc);
        if (result == PlaceResult.TOO_CLOSE_PORTAL) return "cannot-place-near-portal";
        if (result == PlaceResult.TOO_CLOSE_PLAYER) return "cannot-place-near-player";
        if (result == PlaceResult.MAX_PORTALS)      return "max-portals-reached";

        long expiry = System.currentTimeMillis() + lifetimeSec * 1000L;
        Portal portal = new Portal(targetLoc, targetHost, targetPort, expiry, player.getUniqueId(), player.getName());
        portalsByLowerLoc.put(targetLoc, portal);

        spawnPortalEntities(portal, player, targetLoc, targetHost, targetPort, lifetimeSec);

        cfg.getPlaceSound().play(targetLoc.clone().add(0.5, 1.5, 0.5));
        cfg.getPlaceParticles().spawn(targetLoc.clone().add(0.5, 1.5, 0.5));

        plugin.getServerPinger().ping(targetHost, targetPort).thenAccept(info -> {
            portal.setCachedInfo(info);
            Bukkit.getScheduler().runTask(plugin, () ->
                PortalDisplayUpdater.update(portal, info, cfg.getHologramFormat(), player.getName()));
        });

        return null;
    }

    private void spawnPortalEntities(Portal portal, Player player, Location targetLoc,
                                     String targetHost, int targetPort, int lifetimeSec) {
        ConfigManager cfg = plugin.getConfigManager();

        Location lowerLoc = targetLoc.clone().add(0, 1, 0);
        Location upperLoc = targetLoc.clone().add(0, 2, 0);

        BlockDisplay lower = lowerLoc.getWorld().spawn(lowerLoc, BlockDisplay.class);
        BlockDisplay upper = upperLoc.getWorld().spawn(upperLoc, BlockDisplay.class);

        BlockFace facing = player.getFacing();
        Door.Hinge hinge = pickHinge(player, targetLoc, facing);
        boolean leftHinge = hinge == Door.Hinge.LEFT;

        Door lowerData = (Door) Bukkit.createBlockData(cfg.getDoorMaterial());
        lowerData.setHalf(Bisected.Half.BOTTOM);
        lowerData.setFacing(facing);
        lowerData.setHinge(hinge);
        lowerData.setOpen(false);

        Door upperData = (Door) Bukkit.createBlockData(cfg.getDoorMaterial());
        upperData.setHalf(Bisected.Half.TOP);
        upperData.setFacing(facing);
        upperData.setHinge(hinge);
        upperData.setOpen(false);

        lower.setBlock(lowerData);
        upper.setBlock(upperData);

        Transformation closedTrans = DoorAnimator.buildTransform(facing, leftHinge, false);
        for (BlockDisplay d : new BlockDisplay[]{lower, upper}) {
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(closedTrans);
            d.setPersistent(false);
            portalsByEntityId.put(d.getUniqueId(), portal);
        }

        portal.setLowerDisplay(lower);
        portal.setUpperDisplay(upper);

        Location interactionLoc = targetLoc.clone().add(0.5, 1.0, 0.5);
        Interaction interaction = interactionLoc.getWorld().spawn(interactionLoc, Interaction.class);
        interaction.setInteractionWidth(cfg.getInteractionWidth());
        interaction.setInteractionHeight(cfg.getInteractionHeight());
        interaction.setResponsive(true);
        interaction.setPersistent(false);
        portalsByEntityId.put(interaction.getUniqueId(), portal);
        portal.setInteraction(interaction);

        TextDisplay hologram = targetLoc.getWorld().spawn(
                targetLoc.clone().add(0.5, cfg.getHologramHeight(), 0.5), TextDisplay.class);
        hologram.setDefaultBackground(false);
        hologram.setBillboard(Display.Billboard.CENTER);
        hologram.setSeeThrough(cfg.isHologramSeeThrough());
        hologram.setShadowed(cfg.isHologramShadowed());
        hologram.setPersistent(false);
        portal.setHologram(hologram);

        PortalDisplayUpdater.update(portal, null, cfg.getHologramFormat(), player.getName());

        playerPortalCount.merge(player.getUniqueId(), 1, Integer::sum);

        int taskId = Bukkit.getScheduler().runTaskLater(plugin,
                () -> removePortalAnimated(targetLoc), lifetimeSec * 20L).getTaskId();
        portal.setTaskId(taskId);
    }

    public void removePortalAnimated(Location lowerLoc) {
        Portal p = portalsByLowerLoc.get(lowerLoc);
        if (p == null) return;

        if (p.getAnimTaskId() != -1) { Bukkit.getScheduler().cancelTask(p.getAnimTaskId()); p.setAnimTaskId(-1); }
        if (p.getCheckTaskId() != -1) { Bukkit.getScheduler().cancelTask(p.getCheckTaskId()); p.setCheckTaskId(-1); }
        if (p.getInteraction() != null) { p.getInteraction().remove(); p.setInteraction(null); }

        final BlockDisplay lower = p.getLowerDisplay();
        final BlockDisplay upper = p.getUpperDisplay();
        final TextDisplay hologram = p.getHologram();

        final Transformation lowerStart = lower != null ? lower.getTransformation() : null;
        final Transformation upperStart = upper != null ? upper.getTransformation() : null;
        final Transformation hgStart    = hologram != null ? hologram.getTransformation() : null;

        final int duration = plugin.getConfigManager().getShrinkTicks();
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

    private void applyShrink(Display d, Transformation base, float scale, boolean centerBlock) {
        Vector3f t = new Vector3f(base.getTranslation());
        if (centerBlock) {
            float off = (1f - scale) * 0.5f;
            t.add(off, off, off);
        }
        Transformation nt = new Transformation(t, base.getLeftRotation(), new Vector3f(scale), base.getRightRotation());
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(1);
        d.setTransformation(nt);
    }

    public void removePortal(Location lowerLoc) {
        Portal p = portalsByLowerLoc.remove(lowerLoc);
        if (p == null) return;
        if (p.getLowerDisplay()  != null) p.getLowerDisplay().remove();
        if (p.getUpperDisplay()  != null) p.getUpperDisplay().remove();
        if (p.getInteraction()   != null) p.getInteraction().remove();
        if (p.getHologram()      != null) p.getHologram().remove();
        portalsByEntityId.values().removeIf(portal -> portal == p);
        playerPortalCount.computeIfPresent(p.getOwner(), (uuid, count) -> count <= 1 ? null : count - 1);
        if (p.getTaskId()      != -1) Bukkit.getScheduler().cancelTask(p.getTaskId());
        if (p.getAnimTaskId()  != -1) Bukkit.getScheduler().cancelTask(p.getAnimTaskId());
        if (p.getCheckTaskId() != -1) Bukkit.getScheduler().cancelTask(p.getCheckTaskId());
    }

    public void removeAllPortals() {
        for (Location loc : portalsByLowerLoc.keySet()) removePortal(loc);
        portalsByLowerLoc.clear();
        portalsByEntityId.clear();
        playerPortalCount.clear();
    }

    public Portal getPortalByEntity(org.bukkit.entity.Entity entity) {
        return portalsByEntityId.get(entity.getUniqueId());
    }

    private Door.Hinge pickHinge(Player player, Location targetBlock, BlockFace facing) {
        double range = plugin.getConfigManager().getMaxPlacementDistance();
        RayTraceResult hit = player.rayTraceBlocks(range);
        if (hit == null) return Door.Hinge.LEFT;

        org.bukkit.util.Vector pos = hit.getHitPosition();
        double lx = pos.getX() - targetBlock.getBlockX();
        double lz = pos.getZ() - targetBlock.getBlockZ();

        double side = switch (facing) {
            case NORTH -> 0.5 - lx;
            case SOUTH -> lx - 0.5;
            case EAST  -> 0.5 - lz;
            case WEST  -> lz - 0.5;
            default    -> 0.0;
        };
        return side > 0 ? Door.Hinge.RIGHT : Door.Hinge.LEFT;
    }
}
