package snoopypupser.buyingchunks.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import snoopypupser.buyingchunks.BuyingChunks;

public record BuyErrorPacket(String messageJson) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BuyErrorPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BuyingChunks.MOD_ID, "buy_error"));

    public static final StreamCodec<FriendlyByteBuf, BuyErrorPacket> STREAM_CODEC =
            StreamCodec.of(BuyErrorPacket::encode, BuyErrorPacket::decode);

    private static void encode(FriendlyByteBuf buf, BuyErrorPacket packet) {
        buf.writeUtf(packet.messageJson());
    }

    private static BuyErrorPacket decode(FriendlyByteBuf buf) {
        return new BuyErrorPacket(buf.readUtf());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
