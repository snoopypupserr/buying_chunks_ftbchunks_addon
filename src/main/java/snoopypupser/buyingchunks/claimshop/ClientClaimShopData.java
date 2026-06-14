package snoopypupser.buyingchunks.claimshop;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class ClientClaimShopData {

    private static Map<ChunkPos, ClaimShopEntry> forSaleChunks = new HashMap<>();
    private static ItemStack baseCost = ItemStack.EMPTY;
    private static boolean dirty = false;
    private static Runnable onUpdateCallback = null;

    public static void setOnUpdateCallback(Runnable callback) {
        onUpdateCallback = callback;
    }

    public static void clearOnUpdateCallback() {
        onUpdateCallback = null;
    }

    public static void update(Map<ChunkPos, ClaimShopEntry> data, ItemStack newBaseCost) {
        forSaleChunks = new HashMap<>(data);
        baseCost = newBaseCost.copy();
        dirty = true;
        if (onUpdateCallback != null) {
            onUpdateCallback.run();
            onUpdateCallback = null;
        }
    }

    public static ItemStack getBaseCost() {
        return baseCost;
    }

    public static boolean isForSale(ChunkPos pos) {
        return forSaleChunks.containsKey(pos);
    }

    public static ClaimShopEntry getEntry(ChunkPos pos) {
        return forSaleChunks.get(pos);
    }

    public static Map<ChunkPos, ClaimShopEntry> getAll() {
        return Collections.unmodifiableMap(forSaleChunks);
    }

    public static void markDirty() {
        dirty = true;
    }

    public static boolean isDirtyAndReset() {
        if (dirty) {
            dirty = false;
            return true;
        }
        return false;
    }
}