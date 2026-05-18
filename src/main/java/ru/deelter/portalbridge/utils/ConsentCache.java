package ru.deelter.portalbridge.utils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ConsentCache {

    private final Cache<String, Boolean> consentCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    private final Cache<String, Boolean> cooldownCache = Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.SECONDS)
            .build();

    private String buildKey(UUID playerId, String host, int port) {
        return playerId + ":" + host + ":" + port;
    }

    public boolean hasConsent(UUID playerId, String host, int port) {
        return consentCache.getIfPresent(buildKey(playerId, host, port)) != null;
    }

    public void grantConsent(UUID playerId, String host, int port) {
        consentCache.put(buildKey(playerId, host, port), true);
    }

    public boolean isOnCooldown(UUID playerId, String host, int port) {
        return cooldownCache.getIfPresent(buildKey(playerId, host, port)) != null;
    }

    public void setCooldown(UUID playerId, String host, int port) {
        cooldownCache.put(buildKey(playerId, host, port), true);
    }
}
