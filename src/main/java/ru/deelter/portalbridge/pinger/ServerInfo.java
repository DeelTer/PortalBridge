package ru.deelter.portalbridge.pinger;

import lombok.Builder;
import lombok.Data;
import ru.deelter.portalbridge.flags.ServerFlag;

import java.util.EnumSet;
import java.util.Set;

/**
 * Immutable data model for a remote server's status information.
 * <p>
 * Contains: player counts, version, MOTD, server capabilities (flags), and reachability.
 * Instances are fetched asynchronously via {@link ServerPinger} combining:
 * - mcsrvstat.us API (player count, MOTD, version)
 * - Raw Minecraft SLP protocol (server flags like auth plugins, anti-cheat, etc.)
 * <p>
 * Special instances:
 * - {@link #EMPTY}: Server responded but with no meaningful data
 * - {@link #UNREACHABLE}: Server did not respond or connection failed
 * <p>
 * Instances are cached by {@link com.github.benmanes.caffeine.cache.Cache} based on
 * portal lifetime to reduce external API calls.
 *
 * @see ServerPinger for fetching logic
 * @see ServerFlag for available capability flags
 */
@Data
@Builder
public class ServerInfo {

	public static final ServerInfo EMPTY = ServerInfo.builder().build();
	public static final ServerInfo UNREACHABLE = ServerInfo.builder().unreachable(true).build();

	private final String motd;
	@Builder.Default
	private final int online = 0;
	@Builder.Default
	private final int max = 0;
	@Builder.Default
	private final Set<ServerFlag> flags = EnumSet.noneOf(ServerFlag.class);
	private final String version;
	@Builder.Default
	private final long timestamp = System.currentTimeMillis();
	@Builder.Default
	private final boolean unreachable = false;

	public boolean hasPortalBridge() {
		return hasFlag(ServerFlag.PLUGIN_INSTALLED);
	}

	public boolean hasFlag(ServerFlag flag) {
		return flags != null && flags.contains(flag);
	}
}
