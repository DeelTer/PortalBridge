package ru.deelter.portalbridge.pinger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jspecify.annotations.NonNull;
import ru.deelter.portalbridge.PortalBridgePlugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ServerPinger {

	private final Cache<String, ServerInfo> cache;

	public ServerPinger(@NonNull PortalBridgePlugin plugin) {
		this.cache = Caffeine.newBuilder()
				.expireAfterWrite(plugin.getConfig().getInt("cache.ping-ttl-ms", 15000), TimeUnit.MILLISECONDS)
				.maximumSize(plugin.getConfig().getInt("cache.max-size", 100))
				.build();
	}

	public CompletableFuture<ServerInfo> ping(String host, int port) {
		String key = host + ":" + port;
		ServerInfo cached = cache.getIfPresent(key);
		if (cached != null && !cached.isExpired()) {
			return CompletableFuture.completedFuture(cached);
		}
		CompletableFuture<ServerInfo> future = MinecraftPinger.ping(host, port);
		future.thenAccept(info -> cache.put(key, info));
		return future;
	}
}