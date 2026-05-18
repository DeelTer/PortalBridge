package ru.deelter.portalbridge.pinger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;
import ru.deelter.portalbridge.PortalBridgePlugin;
import ru.deelter.portalbridge.flags.FlagCodec;
import ru.deelter.portalbridge.flags.ServerFlag;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@NoArgsConstructor
public final class MinecraftPinger {

    public static @NonNull CompletableFuture<Set<ServerFlag>> fetchFlags(@NonNull String host, int port) {
        int timeoutMillis = PortalBridgePlugin.getInstance().getConfigManager().getPingTimeoutMillis();
        return CompletableFuture.supplyAsync(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), timeoutMillis);
                socket.setSoTimeout(timeoutMillis);

                DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                DataInputStream inputStream = new DataInputStream(socket.getInputStream());

                ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
                DataOutputStream handshakeStream = new DataOutputStream(byteBuffer);
                handshakeStream.writeByte(0x00);
                writeVarInt(handshakeStream, -1);
                writeVarInt(handshakeStream, host.length());
                handshakeStream.writeBytes(host);
                handshakeStream.writeShort(port);
                writeVarInt(handshakeStream, 1);

                byte[] handshakePacket = byteBuffer.toByteArray();
                writeVarInt(outputStream, handshakePacket.length);
                outputStream.write(handshakePacket);

                writeVarInt(outputStream, 1);
                outputStream.writeByte(0x00);

                readVarInt(inputStream); // size
                int packetId = readVarInt(inputStream);
                if (packetId != 0x00) return EnumSet.noneOf(ServerFlag.class);

                int jsonLength = readVarInt(inputStream);
                byte[] jsonData = new byte[jsonLength];
                inputStream.readFully(jsonData);

                String json = new String(jsonData, StandardCharsets.UTF_8);
                if (PortalBridgePlugin.getInstance().getConfigManager().isDebug()) {
                    PortalBridgePlugin.getInstance().getLogger().info("SLP response for " + host + ":" + port + ": " + json);
                }
                return parseFlags(json);
            } catch (Exception exception) {
                if (PortalBridgePlugin.getInstance().getConfigManager().isDebug()) {
                    PortalBridgePlugin.getInstance().getLogger().warning("Failed to fetch flags from " + host + ":" + port + " - " + exception.getMessage());
                }
                return EnumSet.noneOf(ServerFlag.class);
            }
        });
    }

    private static @NonNull Set<ServerFlag> parseFlags(@NonNull String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has(FlagCodec.JSON_FIELD)) return EnumSet.noneOf(ServerFlag.class);
            Set<ServerFlag> flags = FlagCodec.decode(root.get(FlagCodec.JSON_FIELD).getAsString());
            if (PortalBridgePlugin.getInstance().getConfigManager().isDebug()) {
                PortalBridgePlugin.getInstance().getLogger().info("Decoded flags: " + flags);
            }
            return flags;
        } catch (Exception exception) {
            return EnumSet.noneOf(ServerFlag.class);
        }
    }

    private static void writeVarInt(@NonNull DataOutputStream outputStream, int value) throws IOException {
        while ((value & -128) != 0) {
            outputStream.writeByte(value & 127 | 128);
            value >>>= 7;
        }
        outputStream.writeByte(value);
    }

    private static int readVarInt(@NonNull DataInputStream inputStream) throws IOException {
        int result = 0;
        int shift = 0;
        byte currentByte;
        do {
            currentByte = inputStream.readByte();
            result |= (currentByte & 0x7F) << shift;
            shift += 7;
        } while ((currentByte & 0x80) != 0);
        return result;
    }
}