package snoopypupser.buyingchunks.network;

import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import snoopypupser.buyingchunks.BuyingChunks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record RequestClaimedChunksPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestClaimedChunksPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BuyingChunks.MOD_ID, "request_claimed_chunks"));

    public static final StreamCodec<FriendlyByteBuf, RequestClaimedChunksPacket> STREAM_CODEC =
            StreamCodec.of((buf, p) -> {}, buf -> new RequestClaimedChunksPacket());

    public static void handle(RequestClaimedChunksPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            Optional<Team> teamOpt = FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
            if (teamOpt.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new ClaimedChunksResponsePacket(List.of()));
                return;
            }

            ClaimedChunkManager manager = FTBChunksAPI.api().getManager();
            List<ChunkPos> chunks = new ArrayList<>();
            for (var chunk : manager.getOrCreateData(teamOpt.get()).getClaimedChunks()) {
                chunks.add(chunk.getPos().chunkPos());
            }
            PacketDistributor.sendToPlayer(player, new ClaimedChunksResponsePacket(chunks));
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
