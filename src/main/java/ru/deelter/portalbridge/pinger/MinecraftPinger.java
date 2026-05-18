package ru.deelter.portalbridge.pinger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import ru.deelter.portalbridge.PortalBridgePlugin;
import ru.deelter.portalbridge.flags.FlagEncoder;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class MinecraftPinger {

	private static final int TIMEOUT_MILLIS = 3000;

	@Contract("_, _ -> new")
	public static @NonNull CompletableFuture<ServerInfo> ping(String host, int port) {
		return CompletableFuture.supplyAsync(() -> {
			try (Socket socket = new Socket()) {
				socket.connect(new InetSocketAddress(host, port), TIMEOUT_MILLIS);
				socket.setSoTimeout(TIMEOUT_MILLIS);

				DataOutputStream out = new DataOutputStream(socket.getOutputStream());
				DataInputStream in = new DataInputStream(socket.getInputStream());

				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				DataOutputStream handshake = new DataOutputStream(baos);
				handshake.writeByte(0x00);
				writeVarInt(handshake, -1);
				writeVarInt(handshake, host.length());
				handshake.writeBytes(host);
				handshake.writeShort(port);
				writeVarInt(handshake, 1);
				byte[] handshakePacket = baos.toByteArray();
				writeVarInt(out, handshakePacket.length);
				out.write(handshakePacket);

				writeVarInt(out, 1);
				out.writeByte(0x00);

				int size = readVarInt(in);
				int packetId = readVarInt(in);
				if (packetId == 0x00) {
					int jsonLength = readVarInt(in);
					byte[] jsonData = new byte[jsonLength];
					in.readFully(jsonData);
					String json = new String(jsonData, StandardCharsets.UTF_8);
					return parseResponse(json);
				}
			} catch (Exception e) {
				PortalBridgePlugin.getInstance().getLogger().warning("Failed to ping " + host + ":" + port + " - " + e.getMessage());
				return ServerInfo.UNREACHABLE;
			}
			return ServerInfo.EMPTY;
		});
	}

	private static ServerInfo parseResponse(String json) {
		try {
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			PortalBridgePlugin.getInstance().getLogger().info("Ping response: " + json);

			JsonObject players = root.getAsJsonObject("players");
			int online = players.get("online").getAsInt();
			int max = players.get("max").getAsInt();
			String version = root.getAsJsonObject("version").get("name").getAsString();

			String motdRaw = "";
			if (root.has("description")) {
				JsonElement desc = root.get("description");
				if (desc.isJsonPrimitive()) {
					motdRaw = desc.getAsString();
				} else if (desc.isJsonObject()) {
					JsonObject descObj = desc.getAsJsonObject();
					if (descObj.has("text")) {
						motdRaw = descObj.get("text").getAsString();
					}
					if (descObj.has("extra")) {
						StringBuilder extraBuilder = new StringBuilder();
						for (JsonElement e : descObj.getAsJsonArray("extra")) {
							if (e.isJsonPrimitive()) {
								extraBuilder.append(e.getAsString());
							} else if (e.isJsonObject()) {
								extraBuilder.append(extractFullText(e.getAsJsonObject()));
							}
						}
						if (motdRaw.isEmpty()) {
							motdRaw = extraBuilder.toString();
						} else {
							motdRaw = motdRaw + extraBuilder.toString();
						}
					}
				} else {
					motdRaw = desc.toString();
				}
			}
			String flags = extractInvisibleFlags(motdRaw);
			String cleanMotd = FlagEncoder.stripFlags(motdRaw);
			if (cleanMotd.isEmpty()) cleanMotd = "Minecraft Server";

			return ServerInfo.builder()
					.motd(cleanMotd)
					.online(online)
					.max(max)
					.flagsRaw(flags)
					.version(version)
					.build();
		} catch (Exception e) {
			PortalBridgePlugin.getInstance().getLogger().warning("Failed to parse ping response: " + e.getMessage());
			return ServerInfo.EMPTY;
		}
	}

	private static @NonNull String extractFullText(@NonNull JsonObject component) {
		StringBuilder sb = new StringBuilder();
		if (component.has("text")) {
			sb.append(component.get("text").getAsString());
		}
		if (component.has("extra")) {
			for (JsonElement element : component.getAsJsonArray("extra")) {
				if (element.isJsonObject()) {
					sb.append(extractFullText(element.getAsJsonObject()));
				} else if (element.isJsonPrimitive()) {
					sb.append(element.getAsString());
				}
			}
		}
		return sb.toString();
	}

	private static @NonNull String extractInvisibleFlags(@NonNull String motd) {
		StringBuilder flags = new StringBuilder();
		for (char c : motd.toCharArray()) {
			if (c == '\u200B' || c == '\u200C') flags.append(c);
			else break;
		}
		return flags.toString();
	}

	private static void writeVarInt(DataOutputStream out, int value) throws IOException {
		while ((value & -128) != 0) {
			out.writeByte(value & 127 | 128);
			value >>>= 7;
		}
		out.writeByte(value);
	}

	private static int readVarInt(@NonNull DataInputStream in) throws IOException {
		int result = 0;
		int shift = 0;
		byte b;
		do {
			b = in.readByte();
			result |= (b & 0x7F) << shift;
			shift += 7;
		} while ((b & 0x80) != 0);
		return result;
	}
}