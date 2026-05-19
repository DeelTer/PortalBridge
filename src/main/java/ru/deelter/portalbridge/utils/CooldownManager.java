package ru.deelter.portalbridge.utils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.bukkit.Location;
import org.jspecify.annotations.NonNull;
import ru.deelter.portalbridge.config.ConfigManager;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Manages three portal protection mechanisms:
 * <ul>
 *   <li><b>Placement cooldown</b> — prevents placing portals too frequently (anti-troll spawn camping).</li>
 *   <li><b>Transfer immunity</b> — applied on {@code PlayerJoinEvent}; blocks portals briefly so a player
 *       who just arrived cannot be immediately re-teleported.</li>
 *   <li><b>Spawn protection</b> — blocks portal placement within a configurable radius of world spawn.</li>
 * </ul>
 * All cooldown durations and enable-flags are read from {@link ConfigManager}.
 * Caffeine caches handle automatic expiry — no manual cleanup needed.
 */
public class CooldownManager {

	private final ConfigManager cfg;
	private final Cache<UUID, Boolean> placementCooldown;
	private final Cache<UUID, Boolean> transferImmunity;

	public CooldownManager(@NonNull ConfigManager cfg) {
		this.cfg = cfg;
		this.placementCooldown = Caffeine.newBuilder()
				.expireAfterWrite(cfg.getPlacementCooldownSeconds(), TimeUnit.SECONDS)
				.build();
		this.transferImmunity = Caffeine.newBuilder()
				.expireAfterWrite(cfg.getTransferImmunitySeconds(), TimeUnit.SECONDS)
				.build();
	}

	public boolean isOnPlacementCooldown(UUID uuid) {
		return cfg.isPlacementCooldownEnabled() && placementCooldown.getIfPresent(uuid) != null;
	}

	public void applyPlacementCooldown(UUID uuid) {
		placementCooldown.put(uuid, Boolean.TRUE);
	}

	public boolean hasTransferImmunity(UUID uuid) {
		return cfg.isTransferImmunityEnabled() && transferImmunity.getIfPresent(uuid) != null;
	}

	public void applyTransferImmunity(UUID uuid) {
		transferImmunity.put(uuid, Boolean.TRUE);
	}

	public boolean isNearSpawn(Location location) {
		if (!cfg.isSpawnProtectionEnabled()) return false;
		Location spawn = location.getWorld().getSpawnLocation();
		double radius = cfg.getSpawnProtectionRadius();
		return location.distanceSquared(spawn) < radius * radius;
	}
}
