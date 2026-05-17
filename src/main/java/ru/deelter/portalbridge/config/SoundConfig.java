package ru.deelter.portalbridge.config;

import org.bukkit.Location;
import org.bukkit.Sound;

public record SoundConfig(Sound sound, float volume, float pitch) {

    public void play(Location location) {
        if (location.getWorld() != null)
            location.getWorld().playSound(location, sound, volume, pitch);
    }
}
