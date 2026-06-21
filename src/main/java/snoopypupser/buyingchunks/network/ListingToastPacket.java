package snoopypupser.buyingchunks.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import snoopypupser.buyingchunks.BuyingChunks;

public record ListingToastPacket(int chunkX, int chunkZ, ResourceLocation priceItemId, int priceCount) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ListingToastPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BuyingChunks.MOD_ID, "listing_toast"));

    public static final StreamCodec<FriendlyByteBuf, ListingToastPacket> STREAM_CODEC =
            StreamCodec.of(ListingToastPacket::encode, ListingToastPacket::decode);

    private static void encode(FriendlyByteBuf buf, ListingToastPacket packet) {
        buf.writeInt(packet.chunkX());
        buf.writeInt(packet.chunkZ());
        buf.writeResourceLocation(packet.priceItemId());
        buf.writeInt(packet.priceCount());
    }

    private static ListingToastPacket decode(FriendlyByteBuf buf) {
        return new ListingToastPacket(
                buf.readInt(), buf.readInt(),
                buf.readResourceLocation(), buf.readInt()
        );
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
