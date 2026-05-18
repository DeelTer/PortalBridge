package ru.deelter.portalbridge.pinger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import ru.deelter.portalbridge.PortalBridgePlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class McsrvstatPinger {

	private McsrvstatPinger() {
	}

	private static final String API_URL = "https://api.mcsrvstat.us/3/";
	private static final Duration TIMEOUT = Duration.ofSeconds(5);
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(TIMEOUT)
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	public static @NonNull CompletableFuture<ServerInfo> fetch(@NonNull String host, int port) {
		String url = API_URL + host + ":" + port;
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(TIMEOUT)
				.header("User-Agent", "PortalBridge")
				.GET().build();
		return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(HttpResponse::body)
				.thenApply(McsrvstatPinger::parseResponse)
				.exceptionally(exception -> {
					if (PortalBridgePlugin.getInstance().getConfigManager().isDebug()) {
						PortalBridgePlugin.getInstance().getLogger().warning("mcsrvstat fetch failed for " + host + ":" + port + " - " + exception.getMessage());
					}
					return ServerInfo.UNREACHABLE;
				});
	}

	private static @NonNull ServerInfo parseResponse(@NonNull String json) {
		try {
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();

			if (PortalBridgePlugin.getInstance().getConfigManager().isDebug()) {
				PortalBridgePlugin.getInstance().getLogger().info("mcsrvstat raw response: " + json);
			}

			boolean online = root.has("online") && root.get("online").getAsBoolean();
			boolean hasData = root.has("motd") || (root.has("players") && root.getAsJsonObject("players").has("online"));

			// Если сервер не онлайн, но есть данные (например, MOTD), считаем его доступным для отображения
			if (!online && !hasData) {
				return ServerInfo.UNREACHABLE;
			}

			int onlinePlayers = 0, maxPlayers = 0;
			if (root.has("players")) {
				JsonObject playersObject = root.getAsJsonObject("players");
				if (playersObject.has("online")) onlinePlayers = playersObject.get("online").getAsInt();
				if (playersObject.has("max")) maxPlayers = playersObject.get("max").getAsInt();
			}

			String version = root.has("version") ? root.get("version").getAsString() : null;
			String motd = extractMotd(root);

			if ((motd == null || motd.isEmpty()) && root.has("description")) {
				JsonElement descriptionElement = root.get("description");
				if (descriptionElement.isJsonPrimitive()) {
					motd = descriptionElement.getAsString();
				} else if (descriptionElement.isJsonObject()) {
					JsonObject descriptionObject = descriptionElement.getAsJsonObject();
					if (descriptionObject.has("text")) motd = descriptionObject.get("text").getAsString();
				}
			}

			if (motd == null || motd.isEmpty()) motd = "Minecraft Server";

			return ServerInfo.builder()
					.motd(motd)
					.online(onlinePlayers)
					.max(maxPlayers)
					.version(version)
					.build();
		} catch (Exception exception) {
			if (PortalBridgePlugin.getInstance().getConfigManager().isDebug()) {
				PortalBridgePlugin.getInstance().getLogger().warning("mcsrvstat parse failed: " + exception.getMessage());
				exception.printStackTrace();
			}
			return ServerInfo.EMPTY;
		}
	}

	private static @Nullable String extractMotd(@NonNull JsonObject root) {
		if (!root.has("motd")) return null;
		JsonElement motdElement = root.get("motd");
		if (motdElement.isJsonObject()) {
			JsonObject motdObject = motdElement.getAsJsonObject();
			for (String key : new String[]{"clean", "raw", "html"}) {
				if (motdObject.has(key) && motdObject.get(key).isJsonArray()) {
					return joinJsonArray(motdObject.getAsJsonArray(key));
				}
			}
		} else if (motdElement.isJsonPrimitive()) {
			return motdElement.getAsString();
		}
		return null;
	}

	private static @NonNull String joinJsonArray(@NonNull JsonArray array) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < array.size(); i++) {
			if (i > 0) sb.append('\n');
			sb.append(array.get(i).getAsString());
		}
		return sb.toString();
	}
}