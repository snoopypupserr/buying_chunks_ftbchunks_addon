package snoopypupser.buyingchunks.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import snoopypupser.buyingchunks.BuyingChunks;
import snoopypupser.buyingchunks.command.AdminActionHandler;

import java.util.UUID;

public record AdminActionPacket(
        byte actionType,
        UUID teamId,
        String itemId,
        int amount,
        boolean enabled,
        String dimension
) implements CustomPacketPayload {

    public static final byte ACTION_SET_BASE_COST = 0;
    public static final byte ACTION_REMOVE_BASE_COST = 1;
    public static final byte ACTION_SET_PLAYER_SELL = 2;
    public static final byte ACTION_SET_TEAM_PRICE = 3;
    public static final byte ACTION_REMOVE_TEAM_PRICE = 4;
    public static final byte ACTION_SET_CHUNK_LIMIT = 5;
    public static final byte ACTION_REMOVE_CHUNK_LIMIT = 6;
    public static final byte ACTION_SET_PLAYER_INCOME = 7;
    public static final byte ACTION_SET_AUTO_RECLAIM = 8;
    public static final byte ACTION_CREATE_SERVER_TEAM = 9;
    public static final byte ACTION_DELETE_SERVER_TEAM = 10;
    public static final byte ACTION_UPDATE_TEAM_NAME = 11;
    public static final byte ACTION_UPDATE_TEAM_COLOR = 12;
    public static final byte ACTION_SET_BOOL_PROPERTY = 14;
    public static final byte ACTION_SET_PRIVACY_PROPERTY = 15;

    public static final UUID NO_TEAM = new UUID(0, 0);
    public static final String NO_DIMENSION = "";

    public static final CustomPacketPayload.Type<AdminActionPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BuyingChunks.MOD_ID, "admin_action"));

    public static final StreamCodec<FriendlyByteBuf, AdminActionPacket> STREAM_CODEC =
            StreamCodec.of(AdminActionPacket::encode, AdminActionPacket::decode);

    private static void encode(FriendlyByteBuf buf, AdminActionPacket packet) {
        buf.writeByte(packet.actionType());
        buf.writeUUID(packet.teamId());
        buf.writeUtf(packet.itemId());
        buf.writeInt(packet.amount());
        buf.writeBoolean(packet.enabled());
        buf.writeUtf(packet.dimension());
    }

    private static AdminActionPacket decode(FriendlyByteBuf buf) {
        byte actionType = buf.readByte();
        UUID teamId = buf.readUUID();
        String itemId = buf.readUtf();
        int amount = buf.readInt();
        boolean enabled = buf.readBoolean();
        String dimension = buf.readUtf();
        return new AdminActionPacket(actionType, teamId, itemId, amount, enabled, dimension);
    }

    public static void handle(AdminActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> AdminActionHandler.handle(packet, context));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
