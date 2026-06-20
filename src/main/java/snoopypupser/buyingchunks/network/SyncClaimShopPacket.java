package snoopypupser.buyingchunks.network;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import snoopypupser.buyingchunks.BuyingChunks;
import snoopypupser.buyingchunks.claimshop.ClientClaimShopData;
import snoopypupser.buyingchunks.claimshop.ClaimShopEntry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record SyncClaimShopPacket(
        ResourceLocation dimension,
        Map<ChunkPos, ClaimShopEntry> forSaleChunks,
        ItemStack baseCost,
        Map<UUID, Integer> teamChunkLimits,
        Map<UUID, Map<UUID, Integer>> teamBoughtCounts
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncClaimShopPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BuyingChunks.MOD_ID, "sync_claim_shop"));

    public static final StreamCodec<FriendlyByteBuf, SyncClaimShopPacket> STREAM_CODEC =
            StreamCodec.of(SyncClaimShopPacket::encode, SyncClaimShopPacket::decode);

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

    private static void encode(FriendlyByteBuf buf, SyncClaimShopPacket packet) {
        buf.writeResourceLocation(packet.dimension());
        Map<ChunkPos, ClaimShopEntry> copy = new HashMap<>(packet.forSaleChunks());
        buf.writeInt(copy.size());
        for (Map.Entry<ChunkPos, ClaimShopEntry> entry : copy.entrySet()) {
            buf.writeInt(entry.getKey().x);
            buf.writeInt(entry.getKey().z);
            writeItemStack(buf, entry.getValue().getPrice());
            buf.writeUtf(entry.getValue().getShopTeamName());
            buf.writeUUID(entry.getValue().getSellerUUID());
            buf.writeInt(entry.getValue().getTeamColor());
        }
        writeItemStack(buf, packet.baseCost());

        buf.writeInt(packet.teamChunkLimits().size());
        for (Map.Entry<UUID, Integer> e : packet.teamChunkLimits().entrySet()) {
            buf.writeUUID(e.getKey());
            buf.writeInt(e.getValue());
        }

        buf.writeInt(packet.teamBoughtCounts().size());
        for (Map.Entry<UUID, Map<UUID, Integer>> shop : packet.teamBoughtCounts().entrySet()) {
            buf.writeUUID(shop.getKey());
            buf.writeInt(shop.getValue().size());
            for (Map.Entry<UUID, Integer> buyer : shop.getValue().entrySet()) {
                buf.writeUUID(buyer.getKey());
                buf.writeInt(buyer.getValue());
            }
        }
    }

    private static SyncClaimShopPacket decode(FriendlyByteBuf buf) {
        ResourceLocation dimension = buf.readResourceLocation();
        int size = buf.readInt();
        Map<ChunkPos, ClaimShopEntry> map = new HashMap<>();
        for (int i = 0; i < size; i++) {
            int x = buf.readInt();
            int z = buf.readInt();
            ItemStack price = readItemStack(buf);
            String shopTeamName = buf.readUtf();
            UUID sellerUUID = buf.readUUID();
            int teamColor = buf.readInt();
            map.put(new ChunkPos(x, z), new ClaimShopEntry(price, shopTeamName, sellerUUID, teamColor));
        }
        ItemStack baseCost = readItemStack(buf);

        int limitSize = buf.readInt();
        Map<UUID, Integer> limits = new HashMap<>();
        for (int i = 0; i < limitSize; i++) {
            limits.put(buf.readUUID(), buf.readInt());
        }

        int boughtSize = buf.readInt();
        Map<UUID, Map<UUID, Integer>> bought = new HashMap<>();
        for (int i = 0; i < boughtSize; i++) {
            UUID shopId = buf.readUUID();
            int innerSize = buf.readInt();
            Map<UUID, Integer> inner = new HashMap<>();
            for (int j = 0; j < innerSize; j++) {
                inner.put(buf.readUUID(), buf.readInt());
            }
            bought.put(shopId, inner);
        }

        return new SyncClaimShopPacket(dimension, map, baseCost, limits, bought);
    }

    public static void handle(SyncClaimShopPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientClaimShopData.update(
                packet.dimension(), packet.forSaleChunks(), packet.baseCost(),
                packet.teamChunkLimits(), packet.teamBoughtCounts()));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}