package snoopypupser.buyingchunks.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import snoopypupser.buyingchunks.BuyingChunks;

public record OpenAdminScreenPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenAdminScreenPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BuyingChunks.MOD_ID, "open_admin_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenAdminScreenPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> {}, buf -> new OpenAdminScreenPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
