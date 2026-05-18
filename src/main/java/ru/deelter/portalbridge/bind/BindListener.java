package ru.deelter.portalbridge.bind;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
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
import ru.deelter.portalbridge.pinger.ServerInfo;
import ru.deelter.portalbridge.portal.Portal;
import ru.deelter.portalbridge.portal.PortalDisplayUpdater;

@RequiredArgsConstructor
public class BindListener implements Listener {

    private final PortalBridgePlugin plugin;

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
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

        int lifetimeSeconds = plugin.getConfigManager().getPortalLifetimeSeconds();
        Portal portal = plugin.getPortalManager().createPortal(player, bindData.host(), bindData.port(), lifetimeSeconds, bindData.material(), null);
        if (portal == null) {
            player.sendMessage(plugin.getLang().getMessage("portal-creation-failed", player));
            return;
        }

        String address = bindData.port() == 25565 ? bindData.host() : bindData.host() + ":" + bindData.port();
        player.sendMessage(plugin.getLang().getMessage("portal-created", player, "target", address));

        plugin.getServerPinger().ping(bindData.host(), bindData.port()).thenAccept(serverInfo -> {
            Bukkit.getScheduler().runTask(plugin, () -> updatePortalWithInfo(portal, serverInfo, player));
        });
    }

    private void updatePortalWithInfo(@NonNull Portal portal, ServerInfo serverInfo, @NonNull Player player) {
        portal.setCachedInfo(serverInfo);
        PortalDisplayUpdater.update(portal, serverInfo,
                plugin.getConfigManager().getHologramFormat(),
                plugin.getConfigManager().getHologramFormatUnreached(),
                player.getName());
    }
}