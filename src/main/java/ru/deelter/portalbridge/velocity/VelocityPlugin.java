package ru.deelter.portalbridge.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.NonNull;
import ru.deelter.portalbridge.flags.FlagEncoder;

import javax.inject.Inject;
import java.util.EnumSet;
import java.util.Set;

@Getter
@Plugin(id = "portalbridge", name = "PortalBridge", version = "1.0.0")
public class VelocityPlugin {

	private final ProxyServer server;
	private final Set<FlagEncoder.ServerFlag> cachedFlags;

	@Inject
	public VelocityPlugin(ProxyServer server) {
		this.server = server;
		this.cachedFlags = initFlags();
	}

	private @NonNull Set<FlagEncoder.ServerFlag> initFlags() {
		Set<FlagEncoder.ServerFlag> flags = EnumSet.noneOf(FlagEncoder.ServerFlag.class);

		flags.add(FlagEncoder.ServerFlag.PLUGIN_INSTALLED);
		flags.add(FlagEncoder.ServerFlag.PROXY);

		if (server.getConfiguration().isOnlineMode()) {
			flags.add(FlagEncoder.ServerFlag.ONLINE_MODE);
		}
		return flags;
	}

	@Subscribe
	public void onPing(@NonNull ProxyPingEvent event) {
		String flagString = FlagEncoder.encode(cachedFlags);
		Component original = event.getPing().getDescriptionComponent();
		Component newMotd = Component.text(flagString).append(original);
		event.setPing(event.getPing().asBuilder()
				.description(newMotd)
				.build());
	}
}