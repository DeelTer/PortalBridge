package ru.deelter.portalbridge;

import lombok.Getter;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import ru.deelter.portalbridge.bind.BindListener;
import ru.deelter.portalbridge.bind.DoorBindManager;
import ru.deelter.portalbridge.commands.PortalCommand;
import ru.deelter.portalbridge.config.ConfigManager;
import ru.deelter.portalbridge.flags.IServerStatusPacketModifierManager;
import ru.deelter.portalbridge.flags.PortalBridgeFlagModifier;
import ru.deelter.portalbridge.lang.Lang;
import ru.deelter.portalbridge.pinger.ServerPinger;
import ru.deelter.portalbridge.portal.PortalListener;
import ru.deelter.portalbridge.portal.PortalManager;
import ru.deelter.portalbridge.utils.ConsentCache;
import ru.deelter.portalbridge.utils.TrustListManager;

@Getter
public class PortalBridgePlugin extends JavaPlugin {

	private static PortalBridgePlugin instance;

	public static PortalBridgePlugin getInstance() { return instance; }

	private ConsentCache consentCache;
	private ConfigManager configManager;
	private Lang lang;
	private PortalManager portalManager;
	private ServerPinger serverPinger;
	private TrustListManager trustListManager;
	private DoorBindManager doorBindManager;
	private IServerStatusPacketModifierManager flagManager;

	@Override
	public void onEnable() {
		instance = this;
		configManager = new ConfigManager(this);
		configManager.loadConfig();
		lang = new Lang(this);
		trustListManager = new TrustListManager(this);
		serverPinger = new ServerPinger(this);
		portalManager = new PortalManager(this);
		doorBindManager = new DoorBindManager(this);
		consentCache = new ConsentCache();

		if (!Bukkit.isAcceptingTransfers()) {
			if (configManager.isRequireAcceptTransfers()) {
				getLogger().warning("Please set `accepts-transfers=true` in server.properties file");
				Bukkit.getPluginManager().disablePlugin(this);
				return;
			}
			getLogger().warning("The disabled accept-transfers in server.properties may cause errors when connecting to this server. Ignore this log if you are on a proxy server.");
		}

		var pluginPortalCommand = getCommand("portal");
		if (pluginPortalCommand == null) {
			getLogger().warning("Enable `/portal` command with plugin.yml");
			Bukkit.getPluginManager().disablePlugin(this);
			return;
		}
		PortalCommand portalCommand = new PortalCommand(this);
		pluginPortalCommand.setExecutor(portalCommand);
		pluginPortalCommand.setTabCompleter(portalCommand);

		PluginManager pluginManager = Bukkit.getPluginManager();
		pluginManager.registerEvents(new PortalListener(this), this);
		pluginManager.registerEvents(new BindListener(this), this);

		enableFlagInjection();

		UpdateChecker.init(this);
		new Metrics(this, 31401);

		getLogger().info("PortalBridge enabled");
	}

	private void enableFlagInjection() {
		try {
			flagManager = IServerStatusPacketModifierManager.create();
			flagManager.enable();
			flagManager.registerModifier(this, new PortalBridgeFlagModifier());
		} catch (Throwable t) {
			getLogger().warning("Failed to enable flag injection: " + t.getMessage());
			flagManager = null;
		}
	}

	@Override
	public void onDisable() {
		if (portalManager != null) portalManager.removeAllPortals();
		if (flagManager != null && flagManager.isEnabled()) {
			try {
				flagManager.unregisterModifiersByPlugin(this);
				flagManager.disable();
			} catch (Throwable t) {
				getLogger().warning("Failed to disable flag injection: " + t.getMessage());
			}
		}
		getLogger().info("PortalBridge disabled");
	}
}
