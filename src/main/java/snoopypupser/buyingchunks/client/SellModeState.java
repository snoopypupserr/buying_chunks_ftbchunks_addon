package snoopypupser.buyingchunks.client;

public class SellModeState {

    private static boolean sellMode = false;
    private static String sellItemId = "minecraft:diamond";
    private static int sellAmount = 1;

    public static boolean isSellMode() {
        return sellMode;
    }

    public static void setSellMode(boolean mode) {
        sellMode = mode;
    }

    public static String getSellItemId() {
        return sellItemId;
    }

    public static int getSellAmount() {
        return sellAmount;
    }

    public static void setSellPrice(String itemId, int amount) {
        sellItemId = itemId;
        sellAmount = amount;
    }
}
