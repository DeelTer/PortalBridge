package ru.deelter.portalbridge.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.jspecify.annotations.NonNull;
import ru.deelter.portalbridge.PortalBridgePlugin;
import ru.deelter.portalbridge.config.ConfigManager;

/**
 * Listens for player kicks (including bans) and redirects them to the fallback hub server
 * instead of disconnecting them, if {@code fallback.enabled} is true in config.
 *
 * Runs at HIGHEST priority to act after other plugins have set their kick reasons.
 */
@RequiredArgsConstructor
public class FallbackListener implements Listener {

	private final PortalBridgePlugin plugin;

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerKick(@NonNull PlayerKickEvent event) {
		ConfigManager config = plugin.getConfigManager();
		if (!config.isFallbackEnabled()) return;

		event.setCancelled(true);
		event.getPlayer().transfer(config.getFallbackHost(), config.getFallbackPort());
	}
}
