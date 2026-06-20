package snoopypupser.buyingchunks.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import snoopypupser.buyingchunks.BuyingChunks;
import snoopypupser.buyingchunks.client.MainDashboardScreen;

public record OpenMainDashboardScreenPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenMainDashboardScreenPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BuyingChunks.MOD_ID, "open_main_dashboard_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenMainDashboardScreenPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> {}, buf -> new OpenMainDashboardScreenPacket());

    public static void handle(OpenMainDashboardScreenPacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
                new MainDashboardScreen().openGui()
        );
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
