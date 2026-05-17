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

@Getter
@RequiredArgsConstructor
public class Portal {

    private final Location lowerDoorLoc;
    private final String targetHost;
    private final int targetPort;
    private final long expiryTime;
    private final UUID owner;

    @Setter private BlockDisplay lowerDisplay;
    @Setter private BlockDisplay upperDisplay;
    @Setter private Interaction interaction;
    @Setter private TextDisplay hologram;
    @Setter private int taskId = -1;
    @Setter private int checkTaskId = -1;
    @Setter private int animTaskId = -1;
    @Setter private boolean open = false;
    @Setter private ServerInfo cachedInfo;

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiryTime;
    }
}