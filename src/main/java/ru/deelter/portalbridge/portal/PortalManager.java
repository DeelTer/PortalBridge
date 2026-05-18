package ru.deelter.portalbridge.portal;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.*;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import ru.deelter.portalbridge.PortalBridgePlugin;
import ru.deelter.portalbridge.config.ConfigManager;
import ru.deelter.portalbridge.pinger.ServerInfo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of all active portals in the world.
 * <p>
 * Responsible for:
 * - Creating portals when players execute commands (validation, entity spawn, scheduler setup)
 * - Removing portals when they expire or are manually deleted
 * - Animating door opening/closing and shrink-out effects
 * - Updating holographic displays with server information
 * - Tracking portal references by location and entity ID
 * - Enforcing placement rules (collision distance, player proximity, per-player limits)
 * <p>
 * Portal lifecycle:
 * 1. {@link #canPlace(Player, Location)} validates placement rules
 * 2. {@link #createPortal(Player, String, int, int, Material, ServerInfo)} spawns entities + scheduler
 * 3. {@link #startPingAndUpdateHologram(Portal, int)} fetches server info and updates hologram async
 * 4. {@link #removePortalAnimated(Location)} triggers shrink animation
 * 5. {@link #removePortal(Location)} removes all entities + cancels all tasks
 * <p>
 * Handles:
 * - BlockDisplay door rendering (lower + upper halves with transformation)
 * - TextDisplay hologram with MiniMessage formatting
 * - Interaction entity for right-click detection
 * - Scheduler tasks: expiry, animation, player checking, hologram update
 *
 * @see Portal for entity/task storage
 * @see PortalListener for player interaction handling
 * @see PortalDisplayUpdater for hologram formatting
 */
@RequiredArgsConstructor
public class PortalManager {

	public enum PlaceResult {OK, NO_BLOCK, TOO_CLOSE_PORTAL, TOO_CLOSE_PLAYER, MAX_PORTALS}

	private final PortalBridgePlugin plugin;
	private final Map<Location, Portal> portalsByLowerLocation = new ConcurrentHashMap<>();
	private final Map<UUID, Portal> portalsByEntityId = new ConcurrentHashMap<>();
	private final Map<UUID, Integer> playerPortalCount = new ConcurrentHashMap<>();

	public PlaceResult canPlace(Player player, Location targetLoc) {
		ConfigManager cfg = plugin.getConfigManager();

		if (playerPortalCount.getOrDefault(player.getUniqueId(), 0) >= cfg.getMaxPortalsPerPlayer())
			return PlaceResult.MAX_PORTALS;

		double collisionRadiusSq = cfg.getCollisionRadius() * cfg.getCollisionRadius();
		for (Location loc : portalsByLowerLocation.keySet())
			if (loc.distanceSquared(targetLoc) < collisionRadiusSq)
				return PlaceResult.TOO_CLOSE_PORTAL;

		boolean nearPlayer = targetLoc.getNearbyPlayers(cfg.getPlayerProximityRadius()).stream()
				.anyMatch(nearbyPlayer -> !nearbyPlayer.equals(player));
		if (nearPlayer) return PlaceResult.TOO_CLOSE_PLAYER;

		return PlaceResult.OK;
	}

	public Portal createPortal(Player player, String targetHost, int targetPort, int lifetimeSec, Material doorMaterial, ServerInfo preCachedInfo) {
		ConfigManager cfg = plugin.getConfigManager();
		RayTraceResult hit = player.rayTraceBlocks(cfg.getMaxPlacementDistance(), FluidCollisionMode.NEVER);
		if (hit == null || hit.getHitBlock() == null || hit.getHitBlock().isEmpty()) {
			player.sendMessage(plugin.getLang().getMessage("must-look-at-block", player));
			return null;
		}

		Block targetBlock = hit.getHitBlock();
		Location targetLoc = targetBlock.getLocation();

		PlaceResult result = canPlace(player, targetLoc);
		if (result == PlaceResult.TOO_CLOSE_PORTAL) {
			player.sendMessage(plugin.getLang().getMessage("cannot-place-near-portal", player));
			return null;
		}
		if (result == PlaceResult.TOO_CLOSE_PLAYER) {
			player.sendMessage(plugin.getLang().getMessage("cannot-place-near-player", player));
			return null;
		}
		if (result == PlaceResult.MAX_PORTALS) {
			player.sendMessage(plugin.getLang().getMessage("max-portals-reached", player));
			return null;
		}

		long expiry = System.currentTimeMillis() + lifetimeSec * 1000L;
		Portal portal = new Portal(targetLoc, targetHost, targetPort, expiry, player.getUniqueId(), player.getName());
		portalsByLowerLocation.put(targetLoc, portal);

		spawnPortalEntities(portal, player, targetLoc, lifetimeSec, doorMaterial);

		Location effectLoc = targetLoc.clone().add(0.5, 1.5, 0.5);
		cfg.getPlaceSound().play(effectLoc);
		cfg.getPlaceParticles().spawn(effectLoc);

		if (preCachedInfo != null) {
			portal.setCachedInfo(preCachedInfo);
			Bukkit.getScheduler().runTask(plugin, () -> updateHologram(portal, preCachedInfo, player.getName()));
		}

		return portal;
	}

	public void startPingAndUpdateHologram(Portal portal, int lifetimeSec) {
		plugin.getServerPinger().ping(portal.getTargetHost(), portal.getTargetPort())
				.thenAccept(info -> Bukkit.getScheduler().runTask(plugin, () -> {
					portal.setCachedInfo(info);
					updateHologram(portal, info, portal.getOwnerName());
				}));
	}

	public void updateHologram(Portal portal, ServerInfo info, String ownerName) {
		if (portal.isExpired()) return;
		ConfigManager cfg = plugin.getConfigManager();
		long remaining = Math.max(0, (portal.getExpiryTime() - System.currentTimeMillis()) / 1000);
		PortalDisplayUpdater.update(portal, info,
				cfg.getHologramFormat(), cfg.getHologramFormatUnreached(),
				ownerName, (int) remaining, cfg.getPortalLifetimeSeconds());
	}

	private void startHologramUpdater(Portal portal, int lifetimeSeconds) {
		int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			if (portal.isExpired() || portal.getHologram() == null || !portal.getHologram().isValid()) {
				cancelTask(portal.getUpdaterTaskId());
				portal.setUpdaterTaskId(-1);
				return;
			}
			updateHologram(portal, portal.getCachedInfo(), portal.getOwnerName());
		}, 0L, 20L).getTaskId();
		portal.setUpdaterTaskId(taskId);
	}

	private void spawnPortalEntities(Portal portal, Player player, Location targetLoc, int lifetimeSec, Material doorMaterial) {
		ConfigManager cfg = plugin.getConfigManager();
		Material material = doorMaterial != null ? doorMaterial : cfg.getDoorMaterial();

		BlockFace facing = player.getFacing();
		Door.Hinge hinge = pickHinge(player, targetLoc, facing);
		boolean leftHinge = hinge == Door.Hinge.LEFT;
		Transformation closedTrans = DoorAnimator.buildTransform(facing, leftHinge, false);

		BlockDisplay lower = spawnDoorDisplay(targetLoc.clone().add(0, 1, 0), material, facing, hinge, Bisected.Half.BOTTOM, closedTrans, portal);
		BlockDisplay upper = spawnDoorDisplay(targetLoc.clone().add(0, 2, 0), material, facing, hinge, Bisected.Half.TOP, closedTrans, portal);
		portal.setLowerDisplay(lower);
		portal.setUpperDisplay(upper);

		Location interactionLoc = targetLoc.clone().add(0.5, 1.0, 0.5);
		Interaction interaction = interactionLoc.getWorld().spawn(interactionLoc, Interaction.class);
		interaction.setPersistent(false);
		interaction.setInteractionWidth(cfg.getInteractionWidth());
		interaction.setInteractionHeight(cfg.getInteractionHeight());
		interaction.setResponsive(true);
		portalsByEntityId.put(interaction.getUniqueId(), portal);
		portal.setInteraction(interaction);

		TextDisplay hologram = targetLoc.getWorld().spawn(targetLoc.clone().add(0.5, cfg.getHologramHeight(), 0.5), TextDisplay.class);
		hologram.setPersistent(false);
		hologram.setDefaultBackground(false);
		hologram.setBillboard(Display.Billboard.CENTER);
		hologram.setSeeThrough(cfg.isHologramSeeThrough());
		hologram.setShadowed(cfg.isHologramShadowed());
		portal.setHologram(hologram);

		PortalDisplayUpdater.update(portal, null, cfg.getHologramFormat(), cfg.getHologramFormatUnreached(),
				player.getName(), lifetimeSec, lifetimeSec);

		playerPortalCount.merge(player.getUniqueId(), 1, Integer::sum);
		portal.setTaskId(Bukkit.getScheduler().runTaskLater(plugin,
				() -> removePortalAnimated(targetLoc), lifetimeSec * 20L).getTaskId());

		startHologramUpdater(portal, lifetimeSec);
	}

	private BlockDisplay spawnDoorDisplay(Location loc, Material material, BlockFace facing,
	                                      Door.Hinge hinge, Bisected.Half half, Transformation transform, Portal portal) {
		Door data = (Door) Bukkit.createBlockData(material);
		data.setHalf(half);
		data.setFacing(facing);
		data.setHinge(hinge);
		data.setOpen(false);

		BlockDisplay display = loc.getWorld().spawn(loc, BlockDisplay.class);
		display.setPersistent(false);
		display.setBlock(data);
		display.setBrightness(new Display.Brightness(15, 15));
		display.setTransformation(transform);
		portalsByEntityId.put(display.getUniqueId(), portal);
		return display;
	}

	public void removePortalAnimated(Location lowerLoc) {
		Portal portal = portalsByLowerLocation.get(lowerLoc);
		if (portal == null) return;

		cancelTask(portal.getAnimTaskId());
		portal.setAnimTaskId(-1);
		cancelTask(portal.getCheckTaskId());
		portal.setCheckTaskId(-1);
		cancelTask(portal.getUpdaterTaskId());
		portal.setUpdaterTaskId(-1);
		if (portal.getInteraction() != null) {
			portal.getInteraction().remove();
			portal.setInteraction(null);
		}

		final BlockDisplay lower = portal.getLowerDisplay();
		final BlockDisplay upper = portal.getUpperDisplay();
		final TextDisplay hologram = portal.getHologram();
		final Transformation lowerStart = lower != null ? lower.getTransformation() : null;
		final Transformation upperStart = upper != null ? upper.getTransformation() : null;
		final Transformation hgStart = hologram != null ? hologram.getTransformation() : null;

		final int duration = plugin.getConfigManager().getShrinkTicks();
		final int[] tick = {0};
		int id = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			tick[0]++;
			float scale = Math.max(0.001f, 1f - Math.min(1f, (float) tick[0] / duration));

			if (lower != null && lower.isValid()) applyShrink(lower, lowerStart, scale, true);
			if (upper != null && upper.isValid()) applyShrink(upper, upperStart, scale, true);
			if (hologram != null && hologram.isValid()) applyShrink(hologram, hgStart, scale, false);

			if (tick[0] >= duration) {
				int taskId = portal.getAnimTaskId();
				portal.setAnimTaskId(-1);
				cancelTask(taskId);
				removePortal(lowerLoc);
			}
		}, 1L, 1L).getTaskId();
		portal.setAnimTaskId(id);
	}

	private void applyShrink(Display display, Transformation base, float scale, boolean centerBlock) {
		Vector3f t = new Vector3f(base.getTranslation());
		if (centerBlock) {
			float off = (1f - scale) * 0.5f;
			t.add(off, off, off);
		}
		display.setInterpolationDelay(0);
		display.setInterpolationDuration(1);
		display.setTransformation(new Transformation(t, base.getLeftRotation(), new Vector3f(scale), base.getRightRotation()));
	}

	public void removePortal(Location lowerLoc) {
		Portal portal = portalsByLowerLocation.remove(lowerLoc);
		if (portal == null) return;
		if (portal.getLowerDisplay() != null) portal.getLowerDisplay().remove();
		if (portal.getUpperDisplay() != null) portal.getUpperDisplay().remove();
		if (portal.getInteraction() != null) portal.getInteraction().remove();
		if (portal.getHologram() != null) portal.getHologram().remove();
		portalsByEntityId.values().removeIf(p -> p == portal);
		playerPortalCount.computeIfPresent(portal.getOwner(), (uuid, count) -> count <= 1 ? null : count - 1);
		cancelTask(portal.getTaskId());
		cancelTask(portal.getAnimTaskId());
		cancelTask(portal.getCheckTaskId());
		cancelTask(portal.getUpdaterTaskId());
		cancelTask(portal.getAutoCloseTaskId());
	}

	public void removeAllPortals() {
		for (Location loc : portalsByLowerLocation.keySet()) removePortal(loc);
		portalsByLowerLocation.clear();
		portalsByEntityId.clear();
		playerPortalCount.clear();
	}

	public Portal getPortalByEntity(org.bukkit.entity.Entity entity) {
		return portalsByEntityId.get(entity.getUniqueId());
	}

	public Portal getPortalByLowerLoc(Location location) {
		return portalsByLowerLocation.get(location);
	}

	private void cancelTask(int taskId) {
		if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
	}

	private Door.Hinge pickHinge(Player player, Location targetBlock, BlockFace facing) {
		RayTraceResult hit = player.rayTraceBlocks(plugin.getConfigManager().getMaxPlacementDistance());
		if (hit == null) return Door.Hinge.LEFT;

		org.bukkit.util.Vector pos = hit.getHitPosition();
		double lx = pos.getX() - targetBlock.getBlockX();
		double lz = pos.getZ() - targetBlock.getBlockZ();

		double side = switch (facing) {
			case NORTH -> 0.5 - lx;
			case SOUTH -> lx - 0.5;
			case EAST -> 0.5 - lz;
			case WEST -> lz - 0.5;
			default -> 0.0;
		};
		return side > 0 ? Door.Hinge.RIGHT : Door.Hinge.LEFT;
	}
}
