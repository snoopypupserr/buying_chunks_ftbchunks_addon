package snoopypupser.buyingchunks.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import snoopypupser.buyingchunks.BuyingChunks;
import snoopypupser.buyingchunks.claimshop.ClaimShopSavedData;

public record ToggleQuickbuyPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToggleQuickbuyPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BuyingChunks.MOD_ID, "toggle_quickbuy"));

    public static final StreamCodec<FriendlyByteBuf, ToggleQuickbuyPacket> STREAM_CODEC =
            StreamCodec.of(ToggleQuickbuyPacket::encode, ToggleQuickbuyPacket::decode);

    private static void encode(FriendlyByteBuf buf, ToggleQuickbuyPacket packet) {}

    private static ToggleQuickbuyPacket decode(FriendlyByteBuf buf) {
        return new ToggleQuickbuyPacket();
    }

    public static void handle(ToggleQuickbuyPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ServerLevel level = (ServerLevel) player.level();
            ClaimShopSavedData savedData = ClaimShopSavedData.get(level);

            boolean nowEnabled = savedData.getData().toggleQuickbuy(player.getUUID());
            savedData.setDirty();

            PacketDistributor.sendToPlayer(player, new QuickbuySyncPacket(nowEnabled));

            if (nowEnabled) {
                player.sendSystemMessage(BuyingChunks.prefix(Component.translatable(
                        "uc7core.claimshop.quickbuy.enabled"
                )));
            } else {
                player.sendSystemMessage(BuyingChunks.prefix(Component.translatable(
                        "uc7core.claimshop.quickbuy.disabled"
                )));
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
