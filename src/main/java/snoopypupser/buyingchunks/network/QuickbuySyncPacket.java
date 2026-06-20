package snoopypupser.buyingchunks.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import snoopypupser.buyingchunks.BuyingChunks;
import snoopypupser.buyingchunks.claimshop.ClientClaimShopData;

public record QuickbuySyncPacket(boolean enabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<QuickbuySyncPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BuyingChunks.MOD_ID, "quickbuy_sync"));

    public static final StreamCodec<FriendlyByteBuf, QuickbuySyncPacket> STREAM_CODEC =
            StreamCodec.of(QuickbuySyncPacket::encode, QuickbuySyncPacket::decode);

    private static void encode(FriendlyByteBuf buf, QuickbuySyncPacket packet) {
        buf.writeBoolean(packet.enabled());
    }

    private static QuickbuySyncPacket decode(FriendlyByteBuf buf) {
        return new QuickbuySyncPacket(buf.readBoolean());
    }

    public static void handle(QuickbuySyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientClaimShopData.setQuickbuyEnabled(packet.enabled()));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
