package ru.deelter.portalbridge.bind;

import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;
import ru.deelter.portalbridge.PortalBridgePlugin;
import ru.deelter.portalbridge.bind.DoorBindManager.BindData;
import ru.deelter.portalbridge.portal.Portal;

@RequiredArgsConstructor
public class BindListener implements Listener {

	private final PortalBridgePlugin plugin;

	@EventHandler
	public void onInteract(@NonNull PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) return;
		Action action = event.getAction();
		if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;

		ItemStack item = event.getItem();
		DoorBindManager bindManager = plugin.getDoorBindManager();
		if (!bindManager.isDoor(item)) return;

		BindData bindData = bindManager.getBindData(item);
		if (bindData == null) return;

		event.setCancelled(true);
		Player player = event.getPlayer();

		if (plugin.getTrustListManager().isBlacklisted(bindData.host(), bindData.port())) {
			player.sendMessage(plugin.getLang().getMessage("server-blacklisted", player));
			return;
		}

		if (plugin.getCooldownManager().isOnPlacementCooldown(player.getUniqueId())) {
			player.sendMessage(plugin.getLang().getMessage("placement-on-cooldown", player));
			plugin.getConfigManager().getUnavailableSound().play(player.getLocation());
			return;
		}

		Location placementLocation = player.getLocation();
		if (plugin.getCooldownManager().isNearSpawn(placementLocation)) {
			player.sendMessage(plugin.getLang().getMessage("spawn-protection", player));
			plugin.getConfigManager().getUnavailableSound().play(placementLocation);
			return;
		}

		int lifetimeSec = plugin.getConfigManager().getPortalLifetimeSeconds();
		Portal portal = plugin.getPortalManager().createPortal(
				player, bindData.host(), bindData.port(), lifetimeSec, bindData.material(), null);
		if (portal == null) return;

		plugin.getCooldownManager().applyPlacementCooldown(player.getUniqueId());

		String address = bindData.port() == 25565 ? bindData.host() : bindData.host() + ":" + bindData.port();
		player.sendMessage(plugin.getLang().getMessage("portal-created", player, "target", address));
		plugin.getPortalManager().startPingAndUpdateHologram(portal, lifetimeSec);
	}
}
