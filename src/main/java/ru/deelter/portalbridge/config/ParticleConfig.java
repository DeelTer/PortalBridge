package ru.deelter.portalbridge.config;

import org.bukkit.Location;
import org.bukkit.Particle;

public record ParticleConfig(boolean enabled, Particle type, int count, double spread) {

	public void spawn(Location location) {
		if (!enabled || location.getWorld() == null) return;
		location.getWorld().spawnParticle(type, location, count, spread, spread, spread);
	}
}
