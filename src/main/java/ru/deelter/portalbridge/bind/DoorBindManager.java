package ru.deelter.portalbridge.bind;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

public class DoorBindManager {

    private final NamespacedKey keyHost;
    private final NamespacedKey keyPort;

    public DoorBindManager(JavaPlugin plugin) {
        keyHost = new NamespacedKey(plugin, "bind_host");
        keyPort = new NamespacedKey(plugin, "bind_port");
    }

    public boolean isDoor(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return item.getType().name().endsWith("_DOOR");
    }

    public void bind(ItemStack item, String host, int port) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyHost, PersistentDataType.STRING, host);
        pdc.set(keyPort, PersistentDataType.INTEGER, port);
        item.setItemMeta(meta);
    }

    public @Nullable BindData getBindData(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String host = pdc.get(keyHost, PersistentDataType.STRING);
        Integer port = pdc.get(keyPort, PersistentDataType.INTEGER);
        if (host == null || port == null) return null;
        return new BindData(host, port);
    }

    public record BindData(String host, int port) {}
}
