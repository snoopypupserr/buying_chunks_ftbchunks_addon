package snoopypupser.buyingchunks.claimshop;

import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import snoopypupser.buyingchunks.network.SyncClaimShopPacket;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClaimShopSync {

    public static void syncToAll(MinecraftServer server) {
        ClaimShopData globalData = ClaimShopSavedData.get(server.overworld()).getData();
        ItemStack globalBaseCost = globalData.getBaseCost();
        Map<UUID, Integer> globalLimits = globalData.getAllTeamChunkLimits();
        Map<UUID, Map<UUID, Integer>> globalBought = globalData.getAllTeamBoughtCounts();

        for (ServerLevel level : server.getAllLevels()) {
            ClaimShopSavedData savedData = ClaimShopSavedData.get(level);
            Map<ChunkPos, ClaimShopEntry> filtered = filterClaimed(level, savedData);
            SyncClaimShopPacket packet = new SyncClaimShopPacket(
                    level.dimension().location(),
                    filtered,
                    level.dimension().location().equals(ServerLevel.OVERWORLD.location()) ? globalBaseCost : ItemStack.EMPTY,
                    globalLimits,
                    globalBought
            );
            PacketDistributor.sendToAllPlayers(packet);
        }
    }

    public static void syncToPlayer(ServerPlayer player) {
        ClaimShopData globalData = ClaimShopSavedData.get(player.getServer().overworld()).getData();
        ItemStack globalBaseCost = globalData.getBaseCost();
        Map<UUID, Integer> globalLimits = globalData.getAllTeamChunkLimits();
        Map<UUID, Map<UUID, Integer>> globalBought = globalData.getAllTeamBoughtCounts();

        for (ServerLevel level : player.getServer().getAllLevels()) {
            ClaimShopSavedData savedData = ClaimShopSavedData.get(level);
            Map<ChunkPos, ClaimShopEntry> filtered = filterClaimed(level, savedData);
            SyncClaimShopPacket packet = new SyncClaimShopPacket(
                    level.dimension().location(),
                    filtered,
                    level.dimension().location().equals(ServerLevel.OVERWORLD.location()) ? globalBaseCost : ItemStack.EMPTY,
                    globalLimits,
                    globalBought
            );
            PacketDistributor.sendToPlayer(player, packet);
        }
    }

    // --- For-Sale Chunk Filter ---

    private static Map<ChunkPos, ClaimShopEntry> filterClaimed(ServerLevel level, ClaimShopSavedData savedData) {
        Map<ChunkPos, ClaimShopEntry> raw = savedData.getData().getAllForSaleMap();
        Map<ChunkPos, ClaimShopEntry> filtered = new HashMap<>();
        ClaimedChunkManager manager = FTBChunksAPI.api().getManager();
        boolean changed = false;

        for (Map.Entry<ChunkPos, ClaimShopEntry> e : raw.entrySet()) {
            ChunkDimPos dimPos = new ChunkDimPos(level.dimension(), e.getKey());
            if (manager.getChunk(dimPos) != null) {
                filtered.put(e.getKey(), e.getValue());
            } else {
                savedData.getData().removeFromSale(e.getKey());
                changed = true;
            }
        }
        if (changed) savedData.setDirty();
        return filtered;
    }
}