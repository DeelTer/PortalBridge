package ru.deelter.portalbridge.flags;

import org.jspecify.annotations.Nullable;

/**
 * Server capability flags detected and transmitted via PortalBridge.
 * <p>
 * Flags are detected automatically by {@link FlagDetector} based on:
 * - Installed plugins (auth systems, anti-cheat, proxies)
 * - Server configuration (online mode, whitelisting)
 * - Server properties (accepts-transfers, logs IP)
 * <p>
 * Each flag is encoded as a single Unicode character (ZWSP for set, ZWNJ for unset)
 * and transmitted in two ways:
 * 1. <b>Legacy</b>: Invisible characters prepended to server MOTD (via {@code FlagInjectorLegacy})
 * 2. <b>Modern</b>: JSON field in server status response (via {@code PortalBridgeFlagModifier})
 * <p>
 * Flags are used to:
 * - Validate if target server has PortalBridge installed (if {@code require-portalbridge-flag: true})
 * - Determine if player authentication is required (AUTH flag)
 * - Show online-mode status in hologram (ONLINE_MODE flag)
 * - Verify server can accept player transfers (TRANSFERS flag)
 * - Inform players about server features
 * <p>
 * Flag order (ordinal) determines bit position in legacy MOTD encoding.
 * DO NOT reorder without updating {@code FlagCodec}.
 *
 * @see FlagDetector for flag detection logic
 * @see FlagCodec for hex encoding/decoding
 */
public enum ServerFlag {
	/**
	 * PortalBridge plugin is installed on this server
	 */
	PLUGIN_INSTALLED,
	/**
	 * Server is a proxy (Velocity, BungeeCord) accepting player transfers
	 */
	PROXY,
	/**
	 * Server enforces online mode (verified Minecraft accounts required)
	 */
	ONLINE_MODE,
	/**
	 * Authentication plugin detected (requires player consent before transfer)
	 */
	AUTH,
	/**
	 * Anti-cheat system detected
	 */
	ANTI_CHEAT,
	/**
	 * Whitelist is enabled on this server
	 */
	WHITELIST,
	/**
	 * Server accepts player transfers (server.properties: accepts-transfers=true)
	 */
	TRANSFERS,
	/**
	 * Server logs player IP addresses
	 */
	LOGS_IP;

	public static @Nullable ServerFlag fromOrdinal(int ordinal) {
		ServerFlag[] values = values();
		if (ordinal < 0 || ordinal >= values.length) return null;
		return values[ordinal];
	}
}
