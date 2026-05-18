package ru.deelter.portalbridge.pinger;

import lombok.Builder;
import lombok.Data;
import ru.deelter.portalbridge.flags.ServerFlag;

import java.util.EnumSet;
import java.util.Set;

@Data
@Builder
public class ServerInfo {

    public static final ServerInfo EMPTY = ServerInfo.builder().build();
    public static final ServerInfo UNREACHABLE = ServerInfo.builder().unreachable(true).build();

    private final String motd;
    @Builder.Default private final int online = 0;
    @Builder.Default private final int max = 0;
    @Builder.Default private final Set<ServerFlag> flags = EnumSet.noneOf(ServerFlag.class);
    private final String version;
    @Builder.Default private final long timestamp = System.currentTimeMillis();
    @Builder.Default private final boolean unreachable = false;

    public boolean hasPortalBridge() {
        return hasFlag(ServerFlag.PLUGIN_INSTALLED);
    }

    public boolean hasFlag(ServerFlag flag) {
        return flags != null && flags.contains(flag);
    }
}
