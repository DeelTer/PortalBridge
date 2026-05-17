package ru.deelter.portalbridge.bind;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import ru.deelter.portalbridge.PortalBridgePlugin;
import ru.deelter.portalbridge.bind.DoorBindManager.BindData;
import ru.deelter.portalbridge.pinger.ServerInfo;

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

        BindData data = bindManager.getBindData(item);
        if (data == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        plugin.getServerPinger().ping(data.host(), data.port()).thenAccept(info ->
            Bukkit.getScheduler().runTask(plugin, () -> tryOpen(player, data, info)));
    }

    private void tryOpen(Player player, BindData data, ServerInfo info) {
        var trust = plugin.getTrustListManager();

        if (trust.isBlacklisted(data.host(), data.port())) {
            player.sendMessage(plugin.getLang().getMessage("server-blacklisted", player));
            return;
        }
        if (!trust.isAllowed(info, data.host(), data.port())) {
            player.sendMessage(plugin.getLang().getMessage("portal-create-untrusted", player));
            return;
        }

        int lifetime = plugin.getConfigManager().getPortalLifetimeSeconds();
        String error = plugin.getPortalManager().createPortal(player, data.host(), data.port(), lifetime);
        if (error != null) {
            player.sendMessage(plugin.getLang().getMessage(error, player));
            return;
        }

        String address = data.port() == 25565 ? data.host() : data.host() + ":" + data.port();
        player.sendMessage(plugin.getLang().getMessage("portal-created", player, "target", address));
    }
}
