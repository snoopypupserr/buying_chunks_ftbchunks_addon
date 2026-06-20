package snoopypupser.buyingchunks.claimshop;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class ClientClaimShopData {

    private static Map<ResourceLocation, Map<ChunkPos, ClaimShopEntry>> forSaleChunks = new HashMap<>();
    private static ItemStack baseCost = ItemStack.EMPTY;
    private static boolean dirty = false;
    private static Runnable onUpdateCallback = null;

    private static Map<UUID, Integer> teamChunkLimits = new HashMap<>();
    private static Map<UUID, Map<UUID, Integer>> teamBoughtCounts = new HashMap<>();

    public static void setOnUpdateCallback(Runnable callback) {
        onUpdateCallback = callback;
    }

    public static void clearOnUpdateCallback() {
        onUpdateCallback = null;
    }

    public static void update(ResourceLocation dimension, Map<ChunkPos, ClaimShopEntry> data, ItemStack newBaseCost,
                              Map<UUID, Integer> limits, Map<UUID, Map<UUID, Integer>> bought) {
        forSaleChunks.put(dimension, new HashMap<>(data));
        if (dimension.equals(Level.OVERWORLD.location())) {
            baseCost = newBaseCost.copy();
        }
        dirty = true;
        teamChunkLimits = new HashMap<>(limits);
        teamBoughtCounts = new HashMap<>();
        for (Map.Entry<UUID, Map<UUID, Integer>> e : bought.entrySet()) {
            teamBoughtCounts.put(e.getKey(), new HashMap<>(e.getValue()));
        }
        if (onUpdateCallback != null) {
            onUpdateCallback.run();
            onUpdateCallback = null;
        }
    }

    public static ItemStack getBaseCost() {
        return baseCost;
    }

    public static boolean isForSale(ResourceLocation dimension, ChunkPos pos) {
        Map<ChunkPos, ClaimShopEntry> dimEntries = forSaleChunks.get(dimension);
        return dimEntries != null && dimEntries.containsKey(pos);
    }

    public static ClaimShopEntry getEntry(ResourceLocation dimension, ChunkPos pos) {
        Map<ChunkPos, ClaimShopEntry> dimEntries = forSaleChunks.get(dimension);
        return dimEntries != null ? dimEntries.get(pos) : null;
    }

    public static Map<ChunkPos, ClaimShopEntry> getAllForDimension(ResourceLocation dimension) {
        Map<ChunkPos, ClaimShopEntry> dimEntries = forSaleChunks.get(dimension);
        return dimEntries != null ? Collections.unmodifiableMap(dimEntries) : Collections.emptyMap();
    }

    public static Map<ResourceLocation, Map<ChunkPos, ClaimShopEntry>> getAll() {
        return Collections.unmodifiableMap(forSaleChunks);
    }

    public static int getChunkLimit(UUID shopTeamId) {
        return teamChunkLimits.getOrDefault(shopTeamId, -1);
    }

    public static int getBoughtCount(UUID shopTeamId, UUID buyerTeamId) {
        Map<UUID, Integer> counts = teamBoughtCounts.get(shopTeamId);
        return counts != null ? counts.getOrDefault(buyerTeamId, 0) : 0;
    }

    private static boolean quickbuyEnabled = false;

    public static void setQuickbuyEnabled(boolean enabled) {
        quickbuyEnabled = enabled;
    }

    public static boolean isQuickbuyEnabled() {
        return quickbuyEnabled;
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