package ru.deelter.portalbridge.utils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ConsentCache {

    private final Cache<String, Boolean> consentCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    private final Cache<String, Long> cooldownCache = Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.SECONDS)
            .build();

    public boolean hasConsent(UUID playerId, String host, int port) {
        String key = playerId + ":" + host + ":" + port;
        return consentCache.getIfPresent(key) != null;
    }

    public void grantConsent(UUID playerId, String host, int port) {
        String key = playerId + ":" + host + ":" + port;
        consentCache.put(key, true);
    }

    public boolean isOnCooldown(UUID playerId, String host, int port) {
        String key = playerId + ":" + host + ":" + port;
        return cooldownCache.getIfPresent(key) != null;
    }

    public void setCooldown(UUID playerId, String host, int port) {
        String key = playerId + ":" + host + ":" + port;
        cooldownCache.put(key, System.currentTimeMillis());
    }
}