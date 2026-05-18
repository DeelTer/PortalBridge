package ru.deelter.portalbridge.utils;

import lombok.RequiredArgsConstructor;
import ru.deelter.portalbridge.PortalBridgePlugin;
import ru.deelter.portalbridge.config.ConfigManager;
import ru.deelter.portalbridge.pinger.ServerInfo;

@RequiredArgsConstructor
public class TrustListManager {

    private final PortalBridgePlugin plugin;

    public boolean isWhitelisted(String host, int port) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isWhitelistEnabled()) return false;
        String address = host + ":" + port;
        return cfg.getWhitelist().contains(address) || cfg.getWhitelist().contains(host);
    }

    public boolean isBlacklisted(String host, int port) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isBlacklistEnabled()) return false;
        String address = host + ":" + port;
        return cfg.getBlacklist().contains(address) || cfg.getBlacklist().contains(host);
    }

    public boolean isAllowed(ServerInfo info, String host, int port) {
        if (isBlacklisted(host, port)) return false;
        if (isWhitelisted(host, port)) return true;

        if (!plugin.getConfigManager().isRequirePortalBridgeFlag()) return true;

        return info.hasPortalBridge();
    }
}