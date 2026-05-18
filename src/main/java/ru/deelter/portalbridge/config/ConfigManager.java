package ru.deelter.portalbridge.config;

import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.util.List;

@Getter
public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    private boolean requirePortalBridgeFlag;
    private boolean requireAcceptTransfers;

    private List<String> whitelist;
    private List<String> blacklist;
    private boolean whitelistEnabled;
    private boolean blacklistEnabled;

    private String hubHost;
    private int hubPort;

    private int portalLifetimeSeconds;
    private double collisionRadius;
    private double playerProximityRadius;
    private int maxPortalsPerPlayer;
    private int maxPlacementDistance;

    private int openTicks;
    private int shrinkTicks;

    private Material doorMaterial;
    private float interactionWidth;
    private float interactionHeight;

    private double hologramHeight;
    private boolean hologramSeeThrough;
    private boolean hologramShadowed;
    private String hologramFormat;
    private String hologramFormatUnreached;

    private SoundConfig openSound;
    private SoundConfig closeSound;
    private SoundConfig placeSound;

    private ParticleConfig placeParticles;

    private String untrustedAction;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        requirePortalBridgeFlag = config.getBoolean("require-portalbridge-flag", true);
        requireAcceptTransfers = config.getBoolean("require-accept-transfers", false);

        whitelistEnabled = config.getBoolean("trust-lists.whitelist.enabled", true);
        blacklistEnabled = config.getBoolean("trust-lists.blacklist.enabled", true);
        whitelist = config.getStringList("trust-lists.whitelist.entries");
        blacklist = config.getStringList("trust-lists.blacklist.entries");

        hubHost = config.getString("hub.host", "localhost");
        hubPort = config.getInt("hub.port", 25565);

        portalLifetimeSeconds = config.getInt("portal.lifetime-seconds", 30);
        collisionRadius = config.getDouble("portal.collision-radius", 5.0);
        playerProximityRadius = config.getDouble("portal.player-proximity-block-radius", 2.0);
        maxPortalsPerPlayer = config.getInt("portal.max-portals-per-player", 1);
        maxPlacementDistance = config.getInt("portal.max-placement-distance", 4);

        openTicks = config.getInt("portal.animation.open-ticks", 5);
        shrinkTicks = config.getInt("portal.animation.shrink-ticks", 6);

        doorMaterial = parseMaterial(config.getString("portal.door.material", "SPRUCE_DOOR"), Material.SPRUCE_DOOR);
        interactionWidth = (float) config.getDouble("portal.door.interaction-width", 1.4);
        interactionHeight = (float) config.getDouble("portal.door.interaction-height", 2.4);

        hologramHeight = config.getDouble("portal.hologram.height", 3.4);
        hologramSeeThrough = config.getBoolean("portal.hologram.see-through", true);
        hologramShadowed = config.getBoolean("portal.hologram.shadowed", true);
        hologramFormat = config.getString("portal.hologram.format",
                "<gold><bold><motd></bold>\n<green>● <online><gray>/<max>\n<gray><version>\n<dark_gray>↑ <player>");
        hologramFormatUnreached = config.getString("portal.hologram.format-unreached", hologramFormat);

        openSound = parseSound("portal.sounds.open", Sound.BLOCK_WOODEN_DOOR_OPEN, "BLOCK_WOODEN_DOOR_OPEN", 1.0f, 1.0f);
        closeSound = parseSound("portal.sounds.close", Sound.BLOCK_WOODEN_DOOR_CLOSE, "BLOCK_WOODEN_DOOR_CLOSE", 1.0f, 1.0f);
        placeSound = parseSound("portal.sounds.place", Sound.BLOCK_WOODEN_DOOR_OPEN, "BLOCK_WOODEN_DOOR_OPEN", 0.7f, 1.3f);

        placeParticles = parseParticles("portal.particles");

        untrustedAction = config.getString("untrusted.action", "WARN_AND_BLOCK");
    }

    private Material parseMaterial(@NonNull String name, Material fallback) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid door material '" + name + "', using " + fallback.name());
            return fallback;
        }
    }

    @Contract("_, _, _, _, _ -> new")
    private @NonNull SoundConfig parseSound(String path, Sound fallbackSound, String fallbackId, float fallbackVol, float fallbackPitch) {
        String id = config.getString(path + ".id", fallbackId);
        float vol = (float) config.getDouble(path + ".volume", fallbackVol);
        float pitch = (float) config.getDouble(path + ".pitch", fallbackPitch);
        Sound sound;
        try {
            sound = Sound.valueOf(id.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound '" + id + "' at " + path + ", using " + fallbackId);
            sound = fallbackSound;
        }
        return new SoundConfig(sound, vol, pitch);
    }

    @Contract("_ -> new")
    private @NonNull ParticleConfig parseParticles(String path) {
        boolean enabled = config.getBoolean(path + ".enabled", true);
        String typeName = config.getString(path + ".type", "PORTAL");
        int count = config.getInt(path + ".count", 40);
        double spread = config.getDouble(path + ".spread", 0.3);
        Particle type;
        try {
            type = Particle.valueOf(typeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid particle type '" + typeName + "', using PORTAL");
            type = Particle.PORTAL;
        }
        return new ParticleConfig(enabled, type, count, spread);
    }
}