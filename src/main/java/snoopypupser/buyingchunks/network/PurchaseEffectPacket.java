package snoopypupser.buyingchunks.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import snoopypupser.buyingchunks.BuyingChunks;

public record PurchaseEffectPacket(int chunkX, int chunkZ, int teamColor, boolean isBuyer) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PurchaseEffectPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BuyingChunks.MOD_ID, "purchase_effect"));

    public static final StreamCodec<FriendlyByteBuf, PurchaseEffectPacket> STREAM_CODEC =
            StreamCodec.of(PurchaseEffectPacket::encode, PurchaseEffectPacket::decode);

    private static void encode(FriendlyByteBuf buf, PurchaseEffectPacket packet) {
        buf.writeInt(packet.chunkX());
        buf.writeInt(packet.chunkZ());
        buf.writeInt(packet.teamColor());
        buf.writeBoolean(packet.isBuyer());
    }

    private static PurchaseEffectPacket decode(FriendlyByteBuf buf) {
        return new PurchaseEffectPacket(buf.readInt(), buf.readInt(), buf.readInt(), buf.readBoolean());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
