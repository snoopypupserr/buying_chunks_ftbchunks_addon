package snoopypupser.buyingchunks.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import snoopypupser.buyingchunks.BuyingChunks;
import snoopypupser.buyingchunks.config.ServerConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public class WebhookSender {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public static final int EVENT_PURCHASE = 0;
    public static final int EVENT_LISTING = 1;
    public static final int EVENT_REMOVE = 2;

    public static void sendChunkEvent(ServerPlayer player, ChunkPos pos, String dimName,
                                      ItemStack price, String teamName, int eventType) {
        if (!ServerConfig.API_ENABLED.get()) return;
        String apiUrl = ServerConfig.API_URL.get();
        if (apiUrl == null || apiUrl.isBlank()) return;

        JsonObject embed = new JsonObject();
        JsonArray fields = new JsonArray();

        embed.addProperty("color", switch (eventType) {
            case EVENT_PURCHASE -> 0x4CAF50;
            case EVENT_LISTING -> 0x5DADE2;
            case EVENT_REMOVE -> 0xFF9800;
            default -> 0x888888;
        });

        embed.addProperty("title", switch (eventType) {
            case EVENT_PURCHASE -> "\u2705 Chunk Purchased";
            case EVENT_LISTING -> "\ud83e\uddfe Chunk Listed for Sale";
            case EVENT_REMOVE -> "\ud83d\uddd1\ufe0f Chunk Removed from Sale";
            default -> "Chunk Event";
        });

        fields.add(field("Chunk", "[" + pos.x + ", " + pos.z + "]", true));
        fields.add(field("Dimension", dimName, true));

        if (eventType == EVENT_PURCHASE) {
            fields.add(field("Buyer", player.getGameProfile().getName(), true));
        } else {
            fields.add(field("Seller", player.getGameProfile().getName(), true));
        }

        fields.add(field("Team", teamName, true));
        fields.add(field("Price", price.getCount() + "x " + price.getItem().getDescription().getString(), true));
        embed.add("fields", fields);
        embed.addProperty("timestamp", Instant.now().toString());

        JsonObject payload = new JsonObject();
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);

        String json = payload.toString();
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (Exception e) {
                BuyingChunks.LOGGER.warn("WebhookSender: Failed to send to {}: {}", apiUrl, e.getMessage());
            }
        });
    }

    private static JsonObject field(String name, String value, boolean inline) {
        JsonObject f = new JsonObject();
        f.addProperty("name", name);
        f.addProperty("value", value);
        f.addProperty("inline", inline);
        return f;
    }
}
