package snoopypupser.buyingchunks.network;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.TeamProperties;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import snoopypupser.buyingchunks.BuyingChunks;
import snoopypupser.buyingchunks.claimshop.ClaimShopSavedData;
import snoopypupser.buyingchunks.claimshop.ClaimShopSync;
import snoopypupser.buyingchunks.util.WebhookSender;

import java.util.Optional;

public record SellChunkPacket(String itemId, int amount) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SellChunkPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BuyingChunks.MOD_ID, "sell_chunk"));

    public static final StreamCodec<FriendlyByteBuf, SellChunkPacket> STREAM_CODEC =
            StreamCodec.of(SellChunkPacket::encode, SellChunkPacket::decode);

    private static void encode(FriendlyByteBuf buf, SellChunkPacket packet) {
        buf.writeUtf(packet.itemId());
        buf.writeInt(packet.amount());
    }

    private static SellChunkPacket decode(FriendlyByteBuf buf) {
        return new SellChunkPacket(buf.readUtf(), buf.readInt());
    }

    public static void handle(SellChunkPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ServerLevel level = (ServerLevel) player.level();
            ChunkPos chunkPos = new ChunkPos(player.blockPosition());

            ClaimShopSavedData savedData = ClaimShopSavedData.get(level);

            if (!savedData.getData().isPlayerSellEnabled()) {
                player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.playersell_disabled")));
                return;
            }

            ClaimedChunkManager manager = FTBChunksAPI.api().getManager();
            ClaimedChunk claimed = manager.getChunk(new dev.ftb.mods.ftblibrary.math.ChunkDimPos(level.dimension(), chunkPos));
            if (claimed == null) {
                player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.notclaimed")));
                return;
            }

            Optional<Team> playerTeam = FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
            Team chunkTeam = claimed.getTeamData().getTeam();
            if (playerTeam.isEmpty() || !playerTeam.get().getId().equals(chunkTeam.getId())) {
                player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.notowner")));
                return;
            }

            if (savedData.getData().isForSale(chunkPos)) {
                player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.alreadyforsale")));
                return;
            }

            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(packet.itemId()));
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.invalid_item")));
                return;
            }

            String shopTeamName = playerTeam.map(t -> t.getName().getString())
                    .orElse(player.getGameProfile().getName());
            int teamColor = playerTeam.map(t -> t.getProperty(TeamProperties.COLOR).rgb() & 0xFFFFFF)
                    .orElse(0xFFFFFF);

            ItemStack price = new ItemStack(item, packet.amount());
            savedData.getData().setForSale(chunkPos, price, shopTeamName, player.getUUID(), teamColor);
            savedData.setDirty();
            ClaimShopSync.syncToAll(player.getServer());

            PacketDistributor.sendToPlayer(player, new ListingToastPacket(
                    chunkPos.x, chunkPos.z,
                    BuiltInRegistries.ITEM.getKey(item),
                    packet.amount()
            ));

            WebhookSender.sendChunkEvent(player, chunkPos, level.dimension().location().toString(),
                    price, shopTeamName, WebhookSender.EVENT_LISTING);

            player.sendSystemMessage(BuyingChunks.prefix(Component.translatable(
                    "uc7core.claimshop.set.success",
                    chunkPos.x, chunkPos.z, packet.amount(),
                    item.getDescription().getString(),
                    shopTeamName
            )));
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
