package snoopypupser.buyingchunks.network;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.TeamProperties;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import snoopypupser.buyingchunks.BuyingChunks;
import snoopypupser.buyingchunks.claimshop.ClaimShopSavedData;
import snoopypupser.buyingchunks.claimshop.ClaimShopSync;
import snoopypupser.buyingchunks.util.WebhookSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record BulkSellChunksPacket(String itemId, int amount, List<ChunkPos> chunkPositions) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BulkSellChunksPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BuyingChunks.MOD_ID, "bulk_sell_chunks"));

    public static final StreamCodec<FriendlyByteBuf, BulkSellChunksPacket> STREAM_CODEC =
            StreamCodec.of(BulkSellChunksPacket::encode, BulkSellChunksPacket::decode);

    private static void encode(FriendlyByteBuf buf, BulkSellChunksPacket packet) {
        buf.writeUtf(packet.itemId());
        buf.writeInt(packet.amount());
        buf.writeInt(packet.chunkPositions().size());
        for (ChunkPos pos : packet.chunkPositions()) {
            buf.writeInt(pos.x);
            buf.writeInt(pos.z);
        }
    }

    private static BulkSellChunksPacket decode(FriendlyByteBuf buf) {
        String itemId = buf.readUtf();
        int amount = buf.readInt();
        int count = buf.readInt();
        List<ChunkPos> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            positions.add(new ChunkPos(buf.readInt(), buf.readInt()));
        }
        return new BulkSellChunksPacket(itemId, amount, positions);
    }

    public static void handle(BulkSellChunksPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ServerLevel level = (ServerLevel) player.level();

            ClaimShopSavedData savedData = ClaimShopSavedData.get(level);

            if (!savedData.getData().isPlayerSellEnabled()) {
                player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.playersell_disabled")));
                return;
            }

            Optional<Team> playerTeamOpt = FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
            if (playerTeamOpt.isEmpty()) {
                player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.noteam")));
                return;
            }
            Team playerTeam = playerTeamOpt.get();

            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(packet.itemId()));
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.invalid_item")));
                return;
            }

            ItemStack price = new ItemStack(item, packet.amount());
            String shopTeamName = playerTeam.getName().getString();
            int teamColor = playerTeam.getProperty(TeamProperties.COLOR).rgb() & 0xFFFFFF;

            ClaimedChunkManager manager = FTBChunksAPI.api().getManager();
            int listed = 0;

            for (ChunkPos pos : packet.chunkPositions()) {
                if (savedData.getData().isForSale(pos)) continue;

                dev.ftb.mods.ftblibrary.math.ChunkDimPos dimPos = new dev.ftb.mods.ftblibrary.math.ChunkDimPos(level.dimension(), pos);
                ClaimedChunk claimed = manager.getChunk(dimPos);
                if (claimed == null) continue;
                if (!claimed.getTeamData().getTeam().getId().equals(playerTeam.getId())) continue;

                savedData.getData().setForSale(pos, price.copy(), shopTeamName, player.getUUID(), teamColor);
                listed++;

                WebhookSender.sendChunkEvent(player, pos, level.dimension().location().toString(),
                        price, shopTeamName, WebhookSender.EVENT_LISTING);
            }

            savedData.setDirty();
            ClaimShopSync.syncToAll(player.getServer());

            if (listed > 0) {
                player.sendSystemMessage(BuyingChunks.prefix(Component.translatable(
                        "uc7core.claimshop.sell.bulk.success",
                        listed,
                        packet.amount(),
                        item.getDescription().getString(),
                        shopTeamName
                )));
            } else {
                player.sendSystemMessage(BuyingChunks.prefix(Component.translatable(
                        "uc7core.claimshop.sell.bulk.no_chunks"
                )));
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
