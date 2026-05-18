package ru.deelter.portalbridge.pinger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;
import ru.deelter.portalbridge.PortalBridgePlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@NoArgsConstructor
public final class McsrvstatPinger {

    private static final String API_URL = "https://api.mcsrvstat.us/3/";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static @NonNull CompletableFuture<ServerInfo> fetch(@NonNull String host, int port) {
        String url = API_URL + host + ":" + port;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("User-Agent", "PortalBridge")
                .GET().build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(McsrvstatPinger::parse)
                .exceptionally(e -> {
                    PortalBridgePlugin.getInstance().getLogger().warning("mcsrvstat fetch failed for " + host + ":" + port + " - " + e.getMessage());
                    return ServerInfo.UNREACHABLE;
                });
    }

    private static @NonNull ServerInfo parse(@NonNull String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            // Отладка: выводим полученный JSON при debug=true
            if (PortalBridgePlugin.getInstance().getConfigManager().isDebug()) {
                PortalBridgePlugin.getInstance().getLogger().info("mcsrvstat raw response: " + json);
            }

            if (!root.has("online") || !root.get("online").getAsBoolean()) return ServerInfo.UNREACHABLE;

            int online = 0, max = 0;
            if (root.has("players")) {
                JsonObject players = root.getAsJsonObject("players");
                if (players.has("online")) online = players.get("online").getAsInt();
                if (players.has("max"))    max    = players.get("max").getAsInt();
            }

            String version = root.has("version") ? root.get("version").getAsString() : null;
            String motd = extractMotd(root);

            // Если motd не извлекли, пробуем получить из поля "description" (бывает у некоторых серверов)
            if ((motd == null || motd.isEmpty()) && root.has("description")) {
                JsonElement desc = root.get("description");
                if (desc.isJsonPrimitive()) {
                    motd = desc.getAsString();
                } else if (desc.isJsonObject()) {
                    JsonObject descObj = desc.getAsJsonObject();
                    if (descObj.has("text")) motd = descObj.get("text").getAsString();
                }
            }

            // Окончательный fallback
            if (motd == null || motd.isEmpty()) motd = "Minecraft Server";

            return ServerInfo.builder()
                    .motd(motd)
                    .online(online)
                    .max(max)
                    .version(version)
                    .build();
        } catch (Exception e) {
            PortalBridgePlugin.getInstance().getLogger().warning("mcsrvstat parse failed: " + e.getMessage());
            if (PortalBridgePlugin.getInstance().getConfigManager().isDebug()) {
                e.printStackTrace();
            }
            return ServerInfo.EMPTY;
        }
    }

    private static String extractMotd(@NonNull JsonObject root) {
        if (!root.has("motd")) return null;
        JsonElement motdEl = root.get("motd");
        if (motdEl.isJsonObject()) {
            JsonObject motdObj = motdEl.getAsJsonObject();
            // Приоритет: clean -> raw -> html
            if (motdObj.has("clean") && motdObj.get("clean").isJsonArray()) {
                JsonArray clean = motdObj.getAsJsonArray("clean");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < clean.size(); i++) {
                    if (i > 0) sb.append('\n');
                    sb.append(clean.get(i).getAsString());
                }
                return sb.toString();
            } else if (motdObj.has("raw") && motdObj.get("raw").isJsonArray()) {
                JsonArray raw = motdObj.getAsJsonArray("raw");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < raw.size(); i++) {
                    if (i > 0) sb.append('\n');
                    sb.append(raw.get(i).getAsString());
                }
                return sb.toString();
            } else if (motdObj.has("html") && motdObj.get("html").isJsonArray()) {
                JsonArray html = motdObj.getAsJsonArray("html");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < html.size(); i++) {
                    if (i > 0) sb.append('\n');
                    sb.append(html.get(i).getAsString());
                }
                return sb.toString();
            }
        } else if (motdEl.isJsonPrimitive()) {
            return motdEl.getAsString();
        }
        return null;
    }
}