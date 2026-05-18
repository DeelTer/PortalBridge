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

        BindData data = bindManager.getBindData(item);
        if (data == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        // 1. Создаём портал мгновенно
        int lifetime = plugin.getConfigManager().getPortalLifetimeSeconds();
        Portal portal = plugin.getPortalManager().createPortal(player, data.host(), data.port(), lifetime, data.material(), null);
        if (portal == null) {
            player.sendMessage(plugin.getLang().getMessage("portal-creation-failed", player));
            return;
        }

        String address = data.port() == 25565 ? data.host() : data.host() + ":" + data.port();
        player.sendMessage(plugin.getLang().getMessage("portal-created", player, "target", address));

        // 2. Асинхронно получаем данные и обновляем голограмму
        plugin.getServerPinger().ping(data.host(), data.port()).thenAccept(info -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                portal.setCachedInfo(info);
                PortalDisplayUpdater.update(portal, info,
                        plugin.getConfigManager().getHologramFormat(),
                        plugin.getConfigManager().getHologramFormatUnreached(),
                        player.getName());
                // Если данные неполные или сервер недоступен, показываем дополнительное сообщение
                if (info == ServerInfo.EMPTY || info == ServerInfo.UNREACHABLE ||
                        (info.getMotd() != null && info.getFlags().isEmpty())) {
                    player.sendMessage(plugin.getLang().getMessage("portal-created-unreachable", player, "target", address));
                }
            });
        });
    }
}