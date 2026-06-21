package snoopypupser.buyingchunks.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import snoopypupser.buyingchunks.BuyingChunks;

public class BuyingChunksNetwork {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(BuyingChunksNetwork::onRegisterPayloads);
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> net.neoforged.neoforge.network.handling.IPayloadHandler<T> proxy(
            String handlerClass, String methodName, Class<T> packetType) {
        return (packet, context) -> {
            try {
                Class.forName(handlerClass)
                        .getMethod(methodName, packetType, IPayloadContext.class)
                        .invoke(null, packet, context);
            } catch (Exception e) {
                BuyingChunks.LOGGER.error("Failed to handle packet via proxy", e);
            }
        };
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(BuyingChunks.MOD_ID).versioned("1.0.0");
        registrar.playToClient(
                SyncClaimShopPacket.TYPE,
                SyncClaimShopPacket.STREAM_CODEC,
                SyncClaimShopPacket::handle
        );
        registrar.playToClient(
                SyncAdminDataPacket.TYPE,
                SyncAdminDataPacket.STREAM_CODEC,
                SyncAdminDataPacket::handle
        );
        registrar.playToClient(
                OpenAdminScreenPacket.TYPE,
                OpenAdminScreenPacket.STREAM_CODEC,
                proxy("snoopypupser.buyingchunks.client.ClientPayloadHandler", "handleOpenAdminScreen", OpenAdminScreenPacket.class)
        );
        registrar.playToClient(
                TeamListRefreshPacket.TYPE,
                TeamListRefreshPacket.STREAM_CODEC,
                proxy("snoopypupser.buyingchunks.client.ClientPayloadHandler", "handleTeamListRefresh", TeamListRefreshPacket.class)
        );
        registrar.playToServer(
                BuyChunkPacket.TYPE,
                BuyChunkPacket.STREAM_CODEC,
                BuyChunkPacket::handle
        );
        registrar.playToServer(
                AdminActionPacket.TYPE,
                AdminActionPacket.STREAM_CODEC,
                AdminActionPacket::handle
        );
        registrar.playToClient(
                BuyErrorPacket.TYPE,
                BuyErrorPacket.STREAM_CODEC,
                proxy("snoopypupser.buyingchunks.client.ClientPayloadHandler", "handleBuyError", BuyErrorPacket.class)
        );
        registrar.playToClient(
                ListingToastPacket.TYPE,
                ListingToastPacket.STREAM_CODEC,
                proxy("snoopypupser.buyingchunks.client.ClientPayloadHandler", "handleListingToast", ListingToastPacket.class)
        );
        registrar.playToClient(
                QuickbuySyncPacket.TYPE,
                QuickbuySyncPacket.STREAM_CODEC,
                QuickbuySyncPacket::handle
        );
        registrar.playToClient(
                OpenMainDashboardScreenPacket.TYPE,
                OpenMainDashboardScreenPacket.STREAM_CODEC,
                proxy("snoopypupser.buyingchunks.client.ClientPayloadHandler", "handleOpenMainDashboardScreen", OpenMainDashboardScreenPacket.class)
        );
        registrar.playToClient(
                PurchaseEffectPacket.TYPE,
                PurchaseEffectPacket.STREAM_CODEC,
                proxy("snoopypupser.buyingchunks.client.ClientPayloadHandler", "handlePurchaseEffect", PurchaseEffectPacket.class)
        );
        registrar.playToServer(
                SellChunkPacket.TYPE,
                SellChunkPacket.STREAM_CODEC,
                SellChunkPacket::handle
        );
        registrar.playToServer(
                RemoveListingPacket.TYPE,
                RemoveListingPacket.STREAM_CODEC,
                RemoveListingPacket::handle
        );
        registrar.playToServer(
                ToggleQuickbuyPacket.TYPE,
                ToggleQuickbuyPacket.STREAM_CODEC,
                ToggleQuickbuyPacket::handle
        );

    }
}
