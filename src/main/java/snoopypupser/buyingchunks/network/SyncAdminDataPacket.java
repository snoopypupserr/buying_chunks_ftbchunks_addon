package snoopypupser.buyingchunks.network;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import snoopypupser.buyingchunks.BuyingChunks;
import snoopypupser.buyingchunks.client.ClientAdminData;

import java.util.*;

public record SyncAdminDataPacket(
        Map<UUID, ItemStack> teamPrices,
        Map<UUID, Integer> teamChunkLimits,
        boolean playerSellEnabled,
        Set<UUID> playerIncomeDisabled,
        Set<UUID> autoReclaimTeams
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncAdminDataPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BuyingChunks.MOD_ID, "sync_admin_data"));

    public static final StreamCodec<FriendlyByteBuf, SyncAdminDataPacket> STREAM_CODEC =
            StreamCodec.of(SyncAdminDataPacket::encode, SyncAdminDataPacket::decode);

    private static void writeItemStack(FriendlyByteBuf buf, ItemStack stack) {
        buf.writeUtf(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        buf.writeInt(stack.getCount());
    }

    private static ItemStack readItemStack(FriendlyByteBuf buf) {
        ResourceLocation id = ResourceLocation.parse(buf.readUtf());
        Item item = BuiltInRegistries.ITEM.get(id);
        int count = buf.readInt();
        return new ItemStack(item, count);
    }

    private static void encode(FriendlyByteBuf buf, SyncAdminDataPacket packet) {
        buf.writeBoolean(packet.playerSellEnabled());

        buf.writeInt(packet.teamPrices().size());
        for (Map.Entry<UUID, ItemStack> entry : packet.teamPrices().entrySet()) {
            buf.writeUUID(entry.getKey());
            writeItemStack(buf, entry.getValue());
        }

        buf.writeInt(packet.teamChunkLimits().size());
        for (Map.Entry<UUID, Integer> entry : packet.teamChunkLimits().entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeInt(entry.getValue());
        }

        buf.writeInt(packet.playerIncomeDisabled().size());
        for (UUID id : packet.playerIncomeDisabled()) {
            buf.writeUUID(id);
        }

        buf.writeInt(packet.autoReclaimTeams().size());
        for (UUID id : packet.autoReclaimTeams()) {
            buf.writeUUID(id);
        }
    }

    private static SyncAdminDataPacket decode(FriendlyByteBuf buf) {
        boolean playerSellEnabled = buf.readBoolean();

        int tpSize = buf.readInt();
        Map<UUID, ItemStack> teamPrices = new HashMap<>();
        for (int i = 0; i < tpSize; i++) {
            UUID teamId = buf.readUUID();
            ItemStack price = readItemStack(buf);
            teamPrices.put(teamId, price);
        }

        int clSize = buf.readInt();
        Map<UUID, Integer> teamChunkLimits = new HashMap<>();
        for (int i = 0; i < clSize; i++) {
            UUID teamId = buf.readUUID();
            int limit = buf.readInt();
            teamChunkLimits.put(teamId, limit);
        }

        int idSize = buf.readInt();
        Set<UUID> playerIncomeDisabled = new HashSet<>();
        for (int i = 0; i < idSize; i++) {
            playerIncomeDisabled.add(buf.readUUID());
        }

        int arSize = buf.readInt();
        Set<UUID> autoReclaimTeams = new HashSet<>();
        for (int i = 0; i < arSize; i++) {
            autoReclaimTeams.add(buf.readUUID());
        }

        return new SyncAdminDataPacket(teamPrices, teamChunkLimits, playerSellEnabled, playerIncomeDisabled, autoReclaimTeams);
    }

    public static void handle(SyncAdminDataPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientAdminData.update(packet));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
