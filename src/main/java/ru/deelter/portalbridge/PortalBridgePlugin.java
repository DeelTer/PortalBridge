package ru.deelter.portalbridge;

import lombok.Getter;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;
import ru.deelter.portalbridge.commands.AdminCommand;
import ru.deelter.portalbridge.commands.HubCommand;
import ru.deelter.portalbridge.commands.PortalCommand;
import ru.deelter.portalbridge.config.ConfigManager;
import ru.deelter.portalbridge.flags.FlagInjector;
import ru.deelter.portalbridge.lang.Lang;
import ru.deelter.portalbridge.pinger.ServerPinger;
import ru.deelter.portalbridge.portal.PortalListener;
import ru.deelter.portalbridge.portal.PortalManager;
import ru.deelter.portalbridge.utils.TrustListManager;

@Getter
public class PortalBridgePlugin extends JavaPlugin {

    @Getter
    private static PortalBridgePlugin instance;
    private ConfigManager configManager;
    private Lang lang;
    private PortalManager portalManager;
    private ServerPinger serverPinger;
    private TrustListManager trustListManager;

    @Override
    public void onEnable() {
        instance = this;
        configManager = new ConfigManager(this);
        configManager.loadConfig();
        lang = new Lang(this);
        trustListManager = new TrustListManager(this);
        serverPinger = new ServerPinger(this);
        portalManager = new PortalManager(this);

        getCommand("portal").setExecutor(new PortalCommand(this));
        getCommand("hub").setExecutor(new HubCommand(this));
        getCommand("portalbridge").setExecutor(new AdminCommand(this));

        getServer().getPluginManager().registerEvents(new PortalListener(this), this);
        getServer().getPluginManager().registerEvents(new FlagInjector(), this);

        UpdateChecker.init(this);
        new Metrics(this, 31401);

        getLogger().info("PortalBridge enabled");
    }

    @Override
    public void onDisable() {
        if (portalManager != null) portalManager.removeAllPortals();
        getLogger().info("PortalBridge disabled");
    }
}