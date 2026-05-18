package ru.deelter.portalbridge.pinger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jspecify.annotations.NonNull;
import ru.deelter.portalbridge.PortalBridgePlugin;
import ru.deelter.portalbridge.flags.ServerFlag;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ServerPinger {

	private final Cache<String, ServerInfo> cache;

	public ServerPinger(@NonNull PortalBridgePlugin plugin) {
		long ttlSeconds = plugin.getConfig().getLong("cache.ping-ttl-seconds",
				plugin.getConfig().getLong("portal.lifetime-seconds", 30));
		long maxSize = plugin.getConfig().getLong("cache.max-size", 100);
		this.cache = Caffeine.newBuilder()
				.expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
				.maximumSize(maxSize)
				.build();
	}

	public @NonNull CompletableFuture<ServerInfo> ping(@NonNull String host, int port) {
		String key = host + ":" + port;
		ServerInfo cached = cache.getIfPresent(key);
		if (cached != null) return CompletableFuture.completedFuture(cached);

		CompletableFuture<ServerInfo> infoFut = McsrvstatPinger.fetch(host, port);
		CompletableFuture<Set<ServerFlag>> flagsFut = MinecraftPinger.fetchFlags(host, port);

		return infoFut.thenCombine(flagsFut, (info, flags) -> {
			ServerInfo merged = mergeFlags(info, flags);
			cache.put(key, merged);
			return merged;
		});
	}

	public void invalidate(@NonNull String host, int port) {
		cache.invalidate(host + ":" + port);
	}

	private @NonNull ServerInfo mergeFlags(@NonNull ServerInfo info, @NonNull Set<ServerFlag> flags) {
		if (info == ServerInfo.UNREACHABLE) {
			return ServerInfo.builder().unreachable(true).flags(EnumSet.copyOf(flags)).build();
		}
		return ServerInfo.builder()
				.motd(info.getMotd())
				.online(info.getOnline())
				.max(info.getMax())
				.version(info.getVersion())
				.flags(EnumSet.copyOf(flags))
				.unreachable(info.isUnreachable())
				.build();
	}
}
