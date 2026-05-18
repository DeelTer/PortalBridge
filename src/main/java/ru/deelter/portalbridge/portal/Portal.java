package ru.deelter.portalbridge.portal;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
import ru.deelter.portalbridge.pinger.ServerInfo;

import java.util.UUID;

/**
 * Represents an active portal entity within the world.
 * <p>
 * A portal consists of:
 * - Two BlockDisplay entities forming a wooden door (lower + upper half)
 * - An Interaction entity for detecting right-clicks
 * - A TextDisplay entity for the holographic information display
 * - Associated scheduler tasks for expiry, door animation, and player checking
 * <p>
 * Portals expire after a configured lifetime and are automatically removed.
 * Player transfers are initiated when players interact with the Interaction entity.
 *
 * @see PortalManager for creation/removal logic
 * @see PortalListener for player interaction handling
 */
@Getter
@RequiredArgsConstructor
public class Portal {

	private final Location lowerDoorLoc;
	private final String targetHost;
	private final int targetPort;
	private final long expiryTime;
	private final UUID owner;
	private final String ownerName;

	@Setter
	private BlockDisplay lowerDisplay;
	@Setter
	private BlockDisplay upperDisplay;
	@Setter
	private Interaction interaction;
	@Setter
	private TextDisplay hologram;
	@Setter
	private int taskId = -1;
	@Setter
	private int checkTaskId = -1;
	@Setter
	private int animTaskId = -1;
	@Setter
	private int autoCloseTaskId = -1;
	@Setter
	private int updaterTaskId = -1;
	@Setter
	private boolean open = false;
	@Setter
	private ServerInfo cachedInfo;

	public boolean isExpired() {
		return System.currentTimeMillis() >= expiryTime;
	}
}