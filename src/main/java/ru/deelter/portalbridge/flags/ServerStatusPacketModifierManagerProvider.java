package ru.deelter.portalbridge.flags;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class ServerStatusPacketModifierManagerProvider {

	@Getter
	@Setter
	private IServerStatusPacketModifierManager.Factory factory = SimpleServerStatusPacketModifierManager::new;
}
