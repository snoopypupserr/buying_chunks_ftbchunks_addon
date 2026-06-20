package snoopypupser.buyingchunks.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
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

    public static void handle(ListingToastPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            Item item = BuiltInRegistries.ITEM.get(packet.priceItemId());
            Component title = Component.translatable("uc7core.claimshop.toast.listed.title");
            Component body = Component.translatable("uc7core.claimshop.toast.listed.body",
                    packet.chunkX(), packet.chunkZ(),
                    packet.priceCount(),
                    item.getDescription());
            mc.getToasts().addToast(new net.minecraft.client.gui.components.toasts.SystemToast(
                    new net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId(),
                    title, body
            ));
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
