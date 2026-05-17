package ru.deelter.portalbridge.config;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;

@Getter
public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private boolean requirePortalBridgeFlag;
    private List<String> whitelist;
    private List<String> blacklist;
    private boolean whitelistEnabled;
    private boolean blacklistEnabled;
    private int portalLifetimeSeconds;
    private double collisionRadius;
    private double playerProximityRadius;
    private int maxPortalsPerPlayer;
    private int maxPlacementDistance;
    private String untrustedAction;
    private String warningMessage;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        requirePortalBridgeFlag = config.getBoolean("require-portalbridge-flag", true);
        whitelistEnabled = config.getBoolean("trust-lists.whitelist.enabled", true);
        blacklistEnabled = config.getBoolean("trust-lists.blacklist.enabled", true);
        whitelist = config.getStringList("trust-lists.whitelist.entries");
        blacklist = config.getStringList("trust-lists.blacklist.entries");

        portalLifetimeSeconds = config.getInt("portal.lifetime-seconds", 30);
        collisionRadius = config.getDouble("portal.collision-radius", 5.0);
        playerProximityRadius = config.getDouble("portal.player-proximity-block-radius", 2.0);
        maxPortalsPerPlayer = config.getInt("portal.max-portals-per-player", 1);
        maxPlacementDistance = config.getInt("portal.max-placement-distance", 4);

        untrustedAction = config.getString("untrusted.action", "WARN_AND_BLOCK");
        warningMessage = config.getString("untrusted.warning-message",
                "<red>⚠ Target server does not have PortalBridge installed!</red>\n<click:run_command:/portal force><green>Continue anyway</green></click>");
    }
}