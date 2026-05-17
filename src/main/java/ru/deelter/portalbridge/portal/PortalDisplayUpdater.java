package ru.deelter.portalbridge.portal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import ru.deelter.portalbridge.pinger.ServerInfo;

import java.util.UUID;

public final class PortalDisplayUpdater {

    private PortalDisplayUpdater() {}

    public static void update(Portal portal, ServerInfo info, String format, String ownerName) {
        if (portal.isExpired() || portal.getHologram() == null || !portal.getHologram().isValid()) return;

        String host = portal.getTargetHost();
        int port = portal.getTargetPort();
        String address = port == 25565 ? host : host + ":" + port;

        TagResolver resolver = TagResolver.resolver(
                Placeholder.unparsed("motd",    info != null && info.getMotd()    != null ? info.getMotd()    : "Connecting..."),
                Placeholder.unparsed("online",  info != null ? String.valueOf(info.getOnline())  : "?"),
                Placeholder.unparsed("max",     info != null ? String.valueOf(info.getMax())     : "?"),
                Placeholder.unparsed("version", info != null && info.getVersion() != null ? info.getVersion() : "?"),
                Placeholder.unparsed("player",  ownerName),
                Placeholder.unparsed("address", address),
                Placeholder.unparsed("host",    host),
                Placeholder.unparsed("port",    String.valueOf(port))
        );

        Component text = MiniMessage.miniMessage().deserialize(format, resolver);
        portal.getHologram().text(text);
    }
}
