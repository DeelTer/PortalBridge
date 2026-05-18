package ru.deelter.portalbridge.pinger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jspecify.annotations.NonNull;
import ru.deelter.portalbridge.PortalBridgePlugin;
import ru.deelter.portalbridge.flags.ServerFlag;
import ru.deelter.portalbridge.utils.SrvResolver;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ServerPinger {

    private final Cache<String, ServerInfo> cache;

    public ServerPinger(@NonNull PortalBridgePlugin plugin) {
        long ttlSeconds = plugin.getConfig().getLong("cache.ping-ttl-seconds", plugin.getConfig().getLong("portal.lifetime-seconds", 30));
        long maxSize = plugin.getConfig().getLong("cache.max-size", 100);
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                .maximumSize(maxSize)
                .build();
    }

    public @NonNull CompletableFuture<ServerInfo> ping(@NonNull String host, int port) {
        if (port == 25565) {
            SrvResolver.SrvRecord srvRecord = SrvResolver.resolve(host);
            if (srvRecord != null) {
                host = srvRecord.target();
                port = srvRecord.port();
            }
        }
        String cacheKey = host + ":" + port;
        ServerInfo cachedInfo = cache.getIfPresent(cacheKey);
        if (cachedInfo != null) return CompletableFuture.completedFuture(cachedInfo);

        CompletableFuture<ServerInfo> apiInfoFuture = McsrvstatPinger.fetch(host, port);
        CompletableFuture<Set<ServerFlag>> flagsFuture = MinecraftPinger.fetchFlags(host, port);

        return apiInfoFuture.thenCombine(flagsFuture, (apiInfo, flags) -> {
            ServerInfo mergedInfo = mergeInfo(apiInfo, flags);
            if (PortalBridgePlugin.getInstance().getConfigManager().isDebug()) {
                PortalBridgePlugin.getInstance().getLogger().info("Merged info for " + cacheKey + ": motd=" + apiInfo.getMotd() + ", flags=" + flags);
            }
            cache.put(cacheKey, mergedInfo);
            return mergedInfo;
        });
    }

    public void invalidate(@NonNull String host, int port) {
        cache.invalidate(host + ":" + port);
    }

    private @NonNull ServerInfo mergeInfo(@NonNull ServerInfo apiInfo, @NonNull Set<ServerFlag> flags) {
        if (apiInfo == ServerInfo.UNREACHABLE) {
            return ServerInfo.builder().unreachable(true).flags(EnumSet.copyOf(flags)).build();
        }
        return ServerInfo.builder()
                .motd(apiInfo.getMotd())
                .online(apiInfo.getOnline())
                .max(apiInfo.getMax())
                .version(apiInfo.getVersion())
                .flags(EnumSet.copyOf(flags))
                .unreachable(apiInfo.isUnreachable())
                .build();
    }
}