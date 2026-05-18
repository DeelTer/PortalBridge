package ru.deelter.portalbridge.lang;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.deelter.portalbridge.PortalBridgePlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Getter
public class Lang {

	private final PortalBridgePlugin plugin;
	private final Map<String, Map<String, String>> messages = new HashMap<>();
	private final MiniMessage miniMessage = MiniMessage.miniMessage();
	private String defaultLang;
	private boolean autoDetect;

	public Lang(PortalBridgePlugin plugin) {
		this.plugin = plugin;
		reload();
	}

	public void reload() {
		messages.clear();
		defaultLang = plugin.getConfig().getString("language.default", "en");
		autoDetect = plugin.getConfig().getBoolean("language.auto-detect", true);

		File langFolder = new File(plugin.getDataFolder(), "lang");
		if (!langFolder.exists()) langFolder.mkdirs();

		saveDefaultLang("en");
		saveDefaultLang("ru");

		File[] files = langFolder.listFiles((dir, name) -> name.endsWith(".yml"));
		if (files != null) {
			for (File file : files) {
				String code = file.getName().replace(".yml", "");
				YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
				Map<String, String> map = new HashMap<>();
				if (cfg.isConfigurationSection("messages")) {
					for (String key : cfg.getConfigurationSection("messages").getKeys(false)) {
						map.put(key, cfg.getString("messages." + key));
					}
				}
				messages.put(code, map);
			}
		}
	}

	private void saveDefaultLang(String lang) {
		File file = new File(plugin.getDataFolder(), "lang/" + lang + ".yml");
		if (!file.exists()) plugin.saveResource("lang/" + lang + ".yml", false);
	}

	public Component getMessage(String key, CommandSender sender, TagResolver... resolvers) {
		String raw = getRaw(key, sender);
		if (raw == null || raw.isEmpty()) return null;
		TagResolver combined = TagResolver.resolver(resolvers);
		return miniMessage.deserialize(raw, combined);
	}

	public Component getMessage(String key, CommandSender sender, String placeholder, String value) {
		return getMessage(key, sender, Placeholder.unparsed(placeholder, value));
	}

	/**
	 * Like getMessage, but substitutes placeholders directly in the raw string before MiniMessage parsing.
	 * Use this when the placeholder appears inside a MiniMessage tag argument (e.g. click:run_command),
	 * where Adventure's tag resolver does not resolve inner tags due to quoting.
	 */
	public Component getMessageRaw(String key, CommandSender sender, String placeholder, String value) {
		String raw = getRaw(key, sender);
		if (raw == null || raw.isEmpty()) return null;
		raw = raw.replace("<" + placeholder + ">", value);
		return miniMessage.deserialize(raw);
	}

	private String getRaw(String key, CommandSender sender) {
		String lang = defaultLang;
		if (autoDetect && sender instanceof Player p) {
			Locale loc = p.locale();
			String shortLang = loc.getLanguage().toLowerCase();
			if (messages.containsKey(shortLang)) lang = shortLang;
		}
		Map<String, String> map = messages.get(lang);
		if (map != null && map.containsKey(key)) return map.get(key);
		Map<String, String> def = messages.get(defaultLang);
		if (def != null && def.containsKey(key)) return def.get(key);
		return key;
	}
}