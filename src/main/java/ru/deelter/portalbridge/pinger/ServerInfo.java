package ru.deelter.portalbridge.pinger;

import lombok.Builder;
import lombok.Data;
import ru.deelter.portalbridge.flags.FlagEncoder;

@Data
@Builder
public class ServerInfo {
    public static final ServerInfo EMPTY = ServerInfo.builder().motd(null).online(0).max(0).flagsRaw("").version(null).unreachable(false).build();
    public static final ServerInfo UNREACHABLE = ServerInfo.builder().motd(null).online(0).max(0).flagsRaw("").version(null).unreachable(true).build();

    private final String motd;
    private final int online;
    private final int max;
    private final String flagsRaw;
    private final String version;
    @Builder.Default private final long timestamp = System.currentTimeMillis();
    @Builder.Default private final boolean unreachable = false;

    public boolean hasPortalBridge() {
        return FlagEncoder.hasFlag(flagsRaw, FlagEncoder.ServerFlag.PLUGIN_INSTALLED);
    }

    public boolean hasFlag(FlagEncoder.ServerFlag flag) {
        return FlagEncoder.hasFlag(flagsRaw, flag);
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - timestamp > 15000;
    }
}