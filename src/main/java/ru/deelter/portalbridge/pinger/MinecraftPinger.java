package ru.deelter.portalbridge.pinger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.NonNull;
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

public final class MinecraftPinger {

	private static final int TIMEOUT_MILLIS = 3000;

	private MinecraftPinger() {}

	public static @NonNull CompletableFuture<Set<ServerFlag>> fetchFlags(@NonNull String host, int port) {
		return CompletableFuture.supplyAsync(() -> {
			try (Socket socket = new Socket()) {
				socket.connect(new InetSocketAddress(host, port), TIMEOUT_MILLIS);
				socket.setSoTimeout(TIMEOUT_MILLIS);

				DataOutputStream out = new DataOutputStream(socket.getOutputStream());
				DataInputStream in = new DataInputStream(socket.getInputStream());

				ByteArrayOutputStream buf = new ByteArrayOutputStream();
				DataOutputStream handshake = new DataOutputStream(buf);
				handshake.writeByte(0x00);
				writeVarInt(handshake, -1);
				writeVarInt(handshake, host.length());
				handshake.writeBytes(host);
				handshake.writeShort(port);
				writeVarInt(handshake, 1);

				byte[] packet = buf.toByteArray();
				writeVarInt(out, packet.length);
				out.write(packet);

				writeVarInt(out, 1);
				out.writeByte(0x00);

				readVarInt(in); // size
				int packetId = readVarInt(in);
				if (packetId != 0x00) return EnumSet.noneOf(ServerFlag.class);

				int jsonLength = readVarInt(in);
				byte[] data = new byte[jsonLength];
				in.readFully(data);
				return parseFlags(new String(data, StandardCharsets.UTF_8));
			} catch (Exception e) {
				return EnumSet.noneOf(ServerFlag.class);
			}
		});
	}

	private static @NonNull Set<ServerFlag> parseFlags(@NonNull String json) {
		try {
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			if (!root.has(FlagCodec.JSON_FIELD)) return EnumSet.noneOf(ServerFlag.class);
			return FlagCodec.decode(root.get(FlagCodec.JSON_FIELD).getAsString());
		} catch (Exception e) {
			return EnumSet.noneOf(ServerFlag.class);
		}
	}

	private static void writeVarInt(@NonNull DataOutputStream out, int value) throws IOException {
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
