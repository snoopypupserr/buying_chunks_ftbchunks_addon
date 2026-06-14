package snoopypupser.buyingchunks.claimshop;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import snoopypupser.buyingchunks.network.SyncClaimShopPacket;

public class ClaimShopSync {

    public static void syncToAll(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ClaimShopSavedData savedData = ClaimShopSavedData.get(level);
        SyncClaimShopPacket packet = new SyncClaimShopPacket(
                savedData.getData().getAllForSaleMap(),
                savedData.getData().getBaseCost()
        );
        PacketDistributor.sendToAllPlayers(packet);
    }

    public static void syncToPlayer(ServerPlayer player) {
        ServerLevel level = player.getServer().overworld();
        ClaimShopSavedData savedData = ClaimShopSavedData.get(level);
        SyncClaimShopPacket packet = new SyncClaimShopPacket(
                savedData.getData().getAllForSaleMap(),
                savedData.getData().getBaseCost()
        );
        PacketDistributor.sendToPlayer(player, packet);
    }
}