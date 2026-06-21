package snoopypupser.buyingchunks.network;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import snoopypupser.buyingchunks.BuyingChunks;

import java.util.ArrayList;
import java.util.List;

public record OpenMainDashboardScreenPacket(List<ItemStack> pendingIncome, boolean quickbuyEnabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenMainDashboardScreenPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BuyingChunks.MOD_ID, "open_main_dashboard_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenMainDashboardScreenPacket> STREAM_CODEC =
            StreamCodec.of(OpenMainDashboardScreenPacket::encode, OpenMainDashboardScreenPacket::decode);

    private static void encode(FriendlyByteBuf buf, OpenMainDashboardScreenPacket packet) {
        buf.writeBoolean(packet.quickbuyEnabled());
        buf.writeInt(packet.pendingIncome().size());
        for (ItemStack stack : packet.pendingIncome()) {
            buf.writeUtf(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            buf.writeInt(stack.getCount());
        }
    }

    private static OpenMainDashboardScreenPacket decode(FriendlyByteBuf buf) {
        boolean quickbuy = buf.readBoolean();
        int size = buf.readInt();
        List<ItemStack> income = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ResourceLocation id = ResourceLocation.parse(buf.readUtf());
            Item item = BuiltInRegistries.ITEM.get(id);
            int count = buf.readInt();
            income.add(new ItemStack(item, count));
        }
        return new OpenMainDashboardScreenPacket(income, quickbuy);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
