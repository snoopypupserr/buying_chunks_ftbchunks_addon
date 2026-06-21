package snoopypupser.buyingchunks.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ServerConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue WELCOME_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> WELCOME_MESSAGE;
    public static final ModConfigSpec.BooleanValue API_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> API_URL;
    static {
        BUILDER.push("Welcome");
        WELCOME_ENABLED = BUILDER
                .comment("Send a welcome message to players on their first join")
                .define("welcomeEnabled", true);
        WELCOME_MESSAGE = BUILDER
                .comment("Welcome message shown on first join. Use § for color codes.")
                .define("welcomeMessage",
                        "§aThanks for downloading §2BuyingChunks§a!\n" +
                        "§7Use §e/ftbshop gui §7to browse, sell, and buy chunks.\n" +
                        "§7Admins: §e/ftbshop admin §7for server settings.");
        BUILDER.pop();
        BUILDER.push("Webhook API");
        API_ENABLED = BUILDER
                .comment("Enable sending chunk events (buy/sell/remove/trade) to a custom webhook URL.\n" +
                        "Useful for integrating with your own Discord bot or API.\n" +
                        "The payload is a Discord-style embed JSON: {\"embeds\":[...]} with fields:\n" +
                        "  Chunk, Dimension, Buyer/Seller, Team, Price, Event.")
                .define("apiEnabled", false);
        API_URL = BUILDER
                .comment("Custom API/webhook URL to POST chunk events to.\n" +
                        "The JSON payload uses Discord embed format.\n" +
                        "\n" +
                        "How to create a Discord webhook:\n" +
                        "1. Open Discord -> Server Settings -> Integrations\n" +
                        "2. Click 'Webhooks' -> 'Create Webhook' / 'New Webhook'\n" +
                        "3. Give it a name (e.g. 'Buying Chunks') and select a channel\n" +
                        "4. Click 'Copy Webhook URL'\n" +
                        "5. Paste the copied URL below\n" +
                        "\n" +
                        "Example: https://discord.com/api/webhooks/1234567890/abcdefg")
                .define("apiUrl", "");
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
