package ru.deelter.portalbridge;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.io.InputStream;
import java.net.URI;
import java.util.Scanner;

public class UpdateChecker {

	public static void init(@NonNull JavaPlugin plugin) {
		if (!plugin.getConfig().getBoolean("updates.check-enabled", true)) return;

		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			String gitUrl = "https://api.github.com/repos/" + plugin.getConfig().getString("updates.repo") + "/releases/latest";
			try (InputStream in = URI.create(gitUrl).toURL().openStream();
			     Scanner scanner = new Scanner(in)) {

				String response = scanner.useDelimiter("\\A").next();
				String latest = response.split("\"tag_name\":\"")[1].split("\"")[0];

				if (!latest.equals(plugin.getPluginMeta().getVersion())) {
					PortalBridgePlugin.getInstance().getLogger().warning("New version available: " + latest);
				}
			} catch (Exception ignored) {
			}
		});
	}
}