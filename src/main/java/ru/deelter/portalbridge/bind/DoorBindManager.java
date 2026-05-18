// DoorBindManager.java – с сохранением материала и установкой имени/зачарования
package ru.deelter.portalbridge.bind;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

public class DoorBindManager {

	private final NamespacedKey keyHost;
	private final NamespacedKey keyPort;
	private final NamespacedKey keyMaterial;

	public DoorBindManager(JavaPlugin plugin) {
		keyHost = new NamespacedKey(plugin, "bind_host");
		keyPort = new NamespacedKey(plugin, "bind_port");
		keyMaterial = new NamespacedKey(plugin, "bind_material");
	}

	public boolean isDoor(ItemStack item) {
		return item != null && item.getType().name().endsWith("_DOOR");
	}

	public void bind(@NonNull ItemStack item, String host, int port) {
		item.editMeta(meta -> {
			PersistentDataContainer container = meta.getPersistentDataContainer();
			container.set(keyHost, PersistentDataType.STRING, host);
			container.set(keyPort, PersistentDataType.INTEGER, port);
			container.set(keyMaterial, PersistentDataType.STRING, item.getType().name());

			String displayName = port == 25565 ? host : host + ":" + port;
			meta.displayName(Component.text(displayName).decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE));
			meta.addEnchant(Enchantment.UNBREAKING, 1, true);
		});
	}

	public BindData getBindData(ItemStack item) {
		if (item == null) return null;
		ItemMeta meta = item.getItemMeta();
		if (meta == null) return null;
		PersistentDataContainer pdc = meta.getPersistentDataContainer();
		String host = pdc.get(keyHost, PersistentDataType.STRING);
		Integer port = pdc.get(keyPort, PersistentDataType.INTEGER);
		String materialName = pdc.get(keyMaterial, PersistentDataType.STRING);
		if (host == null || port == null || materialName == null) return null;
		Material material = Material.getMaterial(materialName);
		return new BindData(host, port, material != null ? material : Material.OAK_DOOR);
	}

	public record BindData(String host, int port, Material material) {
	}
}