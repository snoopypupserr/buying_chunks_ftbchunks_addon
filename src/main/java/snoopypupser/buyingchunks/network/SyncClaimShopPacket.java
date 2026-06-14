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

public record SyncClaimShopPacket(Map<ChunkPos, ClaimShopEntry> forSaleChunks, ItemStack baseCost) implements CustomPacketPayload {

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
        Map<ChunkPos, ClaimShopEntry> copy = new HashMap<>(packet.forSaleChunks());
        buf.writeInt(copy.size());
        for (Map.Entry<ChunkPos, ClaimShopEntry> entry : copy.entrySet()) {
            buf.writeInt(entry.getKey().x);
            buf.writeInt(entry.getKey().z);
            writeItemStack(buf, entry.getValue().getPrice());
            buf.writeUtf(entry.getValue().getShopTeamName());
            buf.writeUUID(entry.getValue().getSellerUUID());
        }
        // NEU: BaseCost mitsenden
        writeItemStack(buf, packet.baseCost());
    }

    private static SyncClaimShopPacket decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        Map<ChunkPos, ClaimShopEntry> map = new HashMap<>();
        for (int i = 0; i < size; i++) {
            int x = buf.readInt();
            int z = buf.readInt();
            ItemStack price = readItemStack(buf);
            String shopTeamName = buf.readUtf();
            UUID sellerUUID = buf.readUUID();
            map.put(new ChunkPos(x, z), new ClaimShopEntry(price, shopTeamName, sellerUUID));
        }
        // NEU: BaseCost lesen
        ItemStack baseCost = readItemStack(buf);
        return new SyncClaimShopPacket(map, baseCost);
    }

    public static void handle(SyncClaimShopPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientClaimShopData.update(packet.forSaleChunks(), packet.baseCost()));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}