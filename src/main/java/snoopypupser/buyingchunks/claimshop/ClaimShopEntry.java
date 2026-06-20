package snoopypupser.buyingchunks.claimshop;

import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class ClaimShopEntry {
    private final ItemStack price;
    private final String shopTeamName;
    private final UUID sellerUUID;
    private final int teamColor;

    public ClaimShopEntry(ItemStack price, String shopTeamName, UUID sellerUUID) {
        this(price, shopTeamName, sellerUUID, 0xFFFFFF);
    }

    public ClaimShopEntry(ItemStack price, String shopTeamName, UUID sellerUUID, int teamColor) {
        this.price = price;
        this.shopTeamName = shopTeamName;
        this.sellerUUID = sellerUUID;
        this.teamColor = teamColor;
    }

    public ItemStack getPrice() { return price; }
    public String getShopTeamName() { return shopTeamName; }
    public UUID getSellerUUID() { return sellerUUID; }
    public int getTeamColor() { return teamColor; }
}