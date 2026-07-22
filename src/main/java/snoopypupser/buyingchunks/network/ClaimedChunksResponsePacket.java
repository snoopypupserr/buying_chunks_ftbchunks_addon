package snoopypupser.buyingchunks.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import snoopypupser.buyingchunks.BuyingChunks;

import java.util.ArrayList;
import java.util.List;

public record ClaimedChunksResponsePacket(List<ChunkPos> chunks) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClaimedChunksResponsePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BuyingChunks.MOD_ID, "claimed_chunks_response"));

    public static final StreamCodec<FriendlyByteBuf, ClaimedChunksResponsePacket> STREAM_CODEC =
            StreamCodec.of(ClaimedChunksResponsePacket::encode, ClaimedChunksResponsePacket::decode);

    private static void encode(FriendlyByteBuf buf, ClaimedChunksResponsePacket packet) {
        buf.writeInt(packet.chunks().size());
        for (ChunkPos pos : packet.chunks()) {
            buf.writeInt(pos.x);
            buf.writeInt(pos.z);
        }
    }

    private static ClaimedChunksResponsePacket decode(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<ChunkPos> chunks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            chunks.add(new ChunkPos(buf.readInt(), buf.readInt()));
        }
        return new ClaimedChunksResponsePacket(chunks);
    }

    public static void handle(ClaimedChunksResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            snoopypupser.buyingchunks.BuyingChunks.LOGGER.debug("Received claimed chunks response: {} chunks", packet.chunks().size());
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
