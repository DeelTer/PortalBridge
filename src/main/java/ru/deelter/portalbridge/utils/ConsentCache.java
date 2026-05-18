package ru.deelter.portalbridge.utils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import ru.deelter.portalbridge.config.ConfigManager;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ConsentCache {

	private final Cache<String, Boolean> consentCache;
	private final Cache<String, Boolean> cooldownCache;

	public ConsentCache(ConfigManager cfg) {
		this.consentCache = Caffeine.newBuilder()
				.expireAfterWrite(cfg.getConsentTtlSeconds(), TimeUnit.SECONDS)
				.build();
		this.cooldownCache = Caffeine.newBuilder()
				.expireAfterWrite(cfg.getConsentCooldownSeconds(), TimeUnit.SECONDS)
				.build();
	}

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
