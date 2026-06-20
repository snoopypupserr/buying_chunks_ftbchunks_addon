package snoopypupser.buyingchunks.claimshop;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import snoopypupser.buyingchunks.network.SyncClaimShopPacket;

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
            SyncClaimShopPacket packet = new SyncClaimShopPacket(
                    level.dimension().location(),
                    savedData.getData().getAllForSaleMap(),
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
            SyncClaimShopPacket packet = new SyncClaimShopPacket(
                    level.dimension().location(),
                    savedData.getData().getAllForSaleMap(),
                    level.dimension().location().equals(ServerLevel.OVERWORLD.location()) ? globalBaseCost : ItemStack.EMPTY,
                    globalLimits,
                    globalBought
            );
            PacketDistributor.sendToPlayer(player, packet);
        }
    }
}