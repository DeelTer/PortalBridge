// PortalDisplayUpdater.java – полный класс с поддержкой unreachable
package ru.deelter.portalbridge.portal;

import lombok.NoArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import ru.deelter.portalbridge.pinger.ServerInfo;

@NoArgsConstructor
public final class PortalDisplayUpdater {

	public static void update(Portal portal, ServerInfo info, String reachableFormat, String unreachableFormat, String ownerName) {
		if (portal.isExpired() || portal.getHologram() == null || !portal.getHologram().isValid()) return;

		String host = portal.getTargetHost();
		int port = portal.getTargetPort();
		String address = port == 25565 ? host : host + ":" + port;

		boolean reachable = info != null && info != ServerInfo.UNREACHABLE && !info.isUnreachable() && info != ServerInfo.EMPTY;
		String format = reachable ? reachableFormat : unreachableFormat;

		String motdValue;
		String onlineValue;
		String maxValue;
		String versionValue;

		if (reachable && info.getMotd() != null && !info.getMotd().isEmpty()) {
			motdValue = info.getMotd();
			onlineValue = String.valueOf(info.getOnline());
			maxValue = String.valueOf(info.getMax());
			versionValue = info.getVersion() != null ? info.getVersion() : "?";
		} else {
			motdValue = "?";
			onlineValue = "?";
			maxValue = "?";
			versionValue = "?";
		}

		TagResolver resolver = TagResolver.resolver(
				Placeholder.unparsed("motd", motdValue),
				Placeholder.unparsed("online", onlineValue),
				Placeholder.unparsed("max", maxValue),
				Placeholder.unparsed("version", versionValue),
				Placeholder.unparsed("player", ownerName),
				Placeholder.unparsed("address", address),
				Placeholder.unparsed("host", host),
				Placeholder.unparsed("port", String.valueOf(port))
		);

		Component text = MiniMessage.miniMessage().deserialize(format, resolver);
		portal.getHologram().text(text);
	}
}