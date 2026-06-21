package snoopypupser.buyingchunks.network;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import snoopypupser.buyingchunks.BuyingChunks;
import snoopypupser.buyingchunks.claimshop.ClaimShopEntry;
import snoopypupser.buyingchunks.claimshop.ClaimShopSavedData;
import snoopypupser.buyingchunks.claimshop.ClaimShopSync;
import snoopypupser.buyingchunks.util.WebhookSender;

import java.util.Optional;

public record RemoveListingPacket(int chunkX, int chunkZ) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RemoveListingPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BuyingChunks.MOD_ID, "remove_listing"));

    public static final StreamCodec<FriendlyByteBuf, RemoveListingPacket> STREAM_CODEC =
            StreamCodec.of(RemoveListingPacket::encode, RemoveListingPacket::decode);

    private static void encode(FriendlyByteBuf buf, RemoveListingPacket packet) {
        buf.writeInt(packet.chunkX());
        buf.writeInt(packet.chunkZ());
    }

    private static RemoveListingPacket decode(FriendlyByteBuf buf) {
        return new RemoveListingPacket(buf.readInt(), buf.readInt());
    }

    public static void handle(RemoveListingPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ServerLevel level = (ServerLevel) player.level();
            ChunkPos chunkPos = new ChunkPos(packet.chunkX(), packet.chunkZ());

            ClaimShopSavedData savedData = ClaimShopSavedData.get(level);

            if (!savedData.getData().isPlayerSellEnabled()) {
                player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.playersell_disabled")));
                return;
            }

            ClaimShopEntry entry = savedData.getData().getEntry(chunkPos);
            if (entry == null) {
                player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.info.notforsale", chunkPos.x, chunkPos.z)));
                return;
            }

            ClaimedChunkManager manager = FTBChunksAPI.api().getManager();
            ClaimedChunk claimed = manager.getChunk(new dev.ftb.mods.ftblibrary.math.ChunkDimPos(level.dimension(), chunkPos));
            if (claimed != null) {
                Optional<Team> playerTeam = FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
                Team chunkTeam = claimed.getTeamData().getTeam();
                if (playerTeam.isEmpty() || !playerTeam.get().getId().equals(chunkTeam.getId())) {
                    player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.notowner")));
                    return;
                }
            }

            savedData.getData().removeFromSale(chunkPos);
            savedData.setDirty();
            ClaimShopSync.syncToAll(player.getServer());

            WebhookSender.sendChunkEvent(player, chunkPos, level.dimension().location().toString(),
                    entry.getPrice(), entry.getShopTeamName(), WebhookSender.EVENT_REMOVE);

            player.sendSystemMessage(BuyingChunks.prefix(Component.translatable(
                    "uc7core.claimshop.remove.success", chunkPos.x, chunkPos.z
            )));
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
