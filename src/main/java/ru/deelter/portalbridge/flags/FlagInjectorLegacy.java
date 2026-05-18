package ru.deelter.portalbridge.flags;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.jspecify.annotations.NonNull;

import java.util.Set;

public class FlagInjectorLegacy implements Listener {

	private static final char FLAG_SET   = '​';
	private static final char FLAG_UNSET = '‌';

	private final String encodedFlags;

	public FlagInjectorLegacy() {
		this.encodedFlags = encode(FlagDetector.detectFlags());
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onServerListPing(@NonNull ServerListPingEvent event) {
		event.motd(Component.text(encodedFlags).append(event.motd()));
	}

	private static @NonNull String encode(@NonNull Set<ServerFlag> flags) {
		StringBuilder sb = new StringBuilder();
		for (ServerFlag flag : ServerFlag.values()) {
			sb.append(flags.contains(flag) ? FLAG_SET : FLAG_UNSET);
		}
		return sb.toString();
	}
}
