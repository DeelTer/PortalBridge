package ru.deelter.portalbridge.portal;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import ru.deelter.portalbridge.PortalBridgePlugin;
import ru.deelter.portalbridge.config.ConfigManager;
import ru.deelter.portalbridge.flags.ServerFlag;
import ru.deelter.portalbridge.pinger.ServerInfo;

public final class PortalDisplayUpdater {

	private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

	private PortalDisplayUpdater() {
	}

	public static void update(Portal portal, ServerInfo serverInfo, String reachableFormat, String unreachableFormat,
	                          String ownerName, int secondsLeft, int totalLifetimeSeconds) {
		if (portal.isExpired() || portal.getHologram() == null || !portal.getHologram().isValid()) return;

		String targetHost = portal.getTargetHost();
		int targetPort = portal.getTargetPort();
		String serverAddress = targetPort == 25565 ? targetHost : targetHost + ":" + targetPort;

		boolean isReachable = serverInfo != null && !serverInfo.isUnreachable() && serverInfo != ServerInfo.EMPTY;
		String format = isReachable ? reachableFormat : unreachableFormat;

		String onlineModeIcon;
		if (isReachable) {
			onlineModeIcon = serverInfo.hasFlag(ServerFlag.ONLINE_MODE) ? "<green>✔</green>" : "<red>✘</red>";
		} else {
			onlineModeIcon = "<gray>?</gray>";
		}

		// Цветной таймер с порогами из конфига
		ConfigManager cfg = PortalBridgePlugin.getInstance().getConfigManager();
		int greenPercent = cfg.getTimerGreenPercent();
		int yellowPercent = cfg.getTimerYellowPercent();

		String coloredTime;
		if (totalLifetimeSeconds <= 0) {
			coloredTime = "<gray>" + secondsLeft;
		} else {
			float percent = (float) secondsLeft / totalLifetimeSeconds * 100f;
			if (percent >= greenPercent) {
				coloredTime = "<green>" + secondsLeft;
			} else if (percent >= yellowPercent) {
				coloredTime = "<yellow>" + secondsLeft;
			} else {
				coloredTime = "<red>" + secondsLeft;
			}
		}

		TagResolver tagResolver = TagResolver.resolver(
				Placeholder.unparsed("motd", isReachable && serverInfo.getMotd() != null ? serverInfo.getMotd() : "?"),
				Placeholder.unparsed("online", isReachable ? String.valueOf(serverInfo.getOnline()) : "?"),
				Placeholder.unparsed("max", isReachable ? String.valueOf(serverInfo.getMax()) : "?"),
				Placeholder.unparsed("version", isReachable && serverInfo.getVersion() != null ? serverInfo.getVersion() : "?"),
				Placeholder.unparsed("player", ownerName),
				Placeholder.unparsed("address", serverAddress),
				Placeholder.unparsed("host", targetHost),
				Placeholder.unparsed("port", String.valueOf(targetPort)),
				Placeholder.parsed("online_icon", onlineModeIcon),
				Placeholder.parsed("time", coloredTime),
				Placeholder.parsed("colored_time", coloredTime)
		);

		portal.getHologram().text(MINI_MESSAGE.deserialize(format, tagResolver));
	}
}