package ru.deelter.portalbridge.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.jspecify.annotations.NonNull;
import ru.deelter.portalbridge.PortalBridgePlugin;
import ru.deelter.portalbridge.flags.FlagEncoder;
import java.util.EnumSet;

public class FlagInjector implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPing(@NonNull ServerListPingEvent event) {
        String flags = FlagEncoder.encode(EnumSet.of(FlagEncoder.Flag.PORTAL_BRIDGE_INSTALLED));
        Component original = event.motd();
        Component newMotd = Component.text(flags).append(original);
        event.motd(newMotd);
    }
}