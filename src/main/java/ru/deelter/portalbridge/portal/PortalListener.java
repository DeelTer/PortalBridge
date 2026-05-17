package ru.deelter.portalbridge.listener;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.BlockBreakEvent;
import ru.deelter.portalbridge.PortalBridgePlugin;
import ru.deelter.portalbridge.portal.Portal;

public class PortalListener implements Listener {
    @EventHandler
    public void onDoorClick(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.IRON_DOOR) return;
        Portal portal = PortalBridgePlugin.getInstance().getPortalManager().getPortalAt(block.getLocation());
        if (portal == null) return;
        event.setCancelled(true);
        Player p = event.getPlayer();
        // Animation: open door (set open state)
        block.setBlockData(block.getBlockData().clone(), false);
        // Actually, iron door has OPEN property
        // Play sound
        p.playSound(block.getLocation(), org.bukkit.Sound.BLOCK_IRON_DOOR_OPEN, 1f, 1f);
        // Transfer after short delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> p.transfer(portal.getTargetHost(), portal.getTargetPort()), 10L);
    }
    
    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (PortalBridgePlugin.getInstance().getPortalManager().getPortalAt(e.getBlock().getLocation()) != null)
            e.setCancelled(true);
    }
}