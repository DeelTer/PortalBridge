package ru.deelter.portalbridge.portal;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import ru.deelter.portalbridge.pinger.ServerInfo;

public final class PortalDisplayUpdater {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private PortalDisplayUpdater() {}

    public static void update(Portal portal, ServerInfo info, String reachableFormat, String unreachableFormat, String ownerName) {
        if (portal.isExpired() || portal.getHologram() == null || !portal.getHologram().isValid()) return;

        String host = portal.getTargetHost();
        int port = portal.getTargetPort();
        String address = port == 25565 ? host : host + ":" + port;

        boolean reachable = info != null && !info.isUnreachable() && info != ServerInfo.EMPTY;

        TagResolver resolver = TagResolver.resolver(
                Placeholder.unparsed("motd",    reachable && info.getMotd()    != null ? info.getMotd()    : "?"),
                Placeholder.unparsed("online",  reachable ? String.valueOf(info.getOnline())               : "?"),
                Placeholder.unparsed("max",     reachable ? String.valueOf(info.getMax())                  : "?"),
                Placeholder.unparsed("version", reachable && info.getVersion() != null ? info.getVersion() : "?"),
                Placeholder.unparsed("player",  ownerName),
                Placeholder.unparsed("address", address),
                Placeholder.unparsed("host",    host),
                Placeholder.unparsed("port",    String.valueOf(port))
        );

        portal.getHologram().text(MM.deserialize(reachable ? reachableFormat : unreachableFormat, resolver));
    }
}
