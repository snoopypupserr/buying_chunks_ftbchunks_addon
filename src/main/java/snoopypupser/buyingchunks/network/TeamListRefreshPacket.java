package snoopypupser.buyingchunks.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import snoopypupser.buyingchunks.BuyingChunks;

public record TeamListRefreshPacket() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TeamListRefreshPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BuyingChunks.MOD_ID, "team_list_refresh"));

    public static final StreamCodec<FriendlyByteBuf, TeamListRefreshPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> {}, buf -> new TeamListRefreshPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
