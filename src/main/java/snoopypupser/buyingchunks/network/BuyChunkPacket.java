package snoopypupser.buyingchunks.network;

import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import snoopypupser.buyingchunks.BuyingChunks;
import snoopypupser.buyingchunks.claimshop.ClaimShopEntry;
import snoopypupser.buyingchunks.claimshop.ClaimShopSavedData;
import snoopypupser.buyingchunks.claimshop.ClaimShopSync;
import snoopypupser.buyingchunks.util.WebhookSender;

import java.util.Optional;
import java.util.UUID;

public record BuyChunkPacket(int chunkX, int chunkZ) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BuyChunkPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BuyingChunks.MOD_ID, "buy_chunk"));

    public static final StreamCodec<FriendlyByteBuf, BuyChunkPacket> STREAM_CODEC =
            StreamCodec.of(BuyChunkPacket::encode, BuyChunkPacket::decode);

    private static final UUID NO_SELLER = new UUID(0, 0);

    private static void encode(FriendlyByteBuf buf, BuyChunkPacket packet) {
        buf.writeInt(packet.chunkX());
        buf.writeInt(packet.chunkZ());
    }

    private static BuyChunkPacket decode(FriendlyByteBuf buf) {
        return new BuyChunkPacket(buf.readInt(), buf.readInt());
    }

    private static void playGenericError(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void playNoMoneyError(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 1.5f);
    }

    private static void playSuccess(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.0f);
    }

    public static void handle(BuyChunkPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer buyer = (ServerPlayer) context.player();
            ServerLevel level = (ServerLevel) buyer.level();
            ChunkPos pos = new ChunkPos(packet.chunkX(), packet.chunkZ());
            ChunkDimPos dimPos = new ChunkDimPos(level.dimension(), pos);

            ClaimShopSavedData savedData = ClaimShopSavedData.get(level);
            ClaimShopEntry entry = savedData.getData().getEntry(pos);

            var registry = buyer.getServer().registryAccess();

            if (entry == null) {
                playGenericError(buyer);
                Component errMsg = Component.translatable("uc7core.claimshop.error.notforsale");
                PacketDistributor.sendToPlayer(buyer, new BuyErrorPacket(Component.Serializer.toJson(errMsg, registry)));
                return;
            }

            ItemStack price = entry.getPrice();

            if (!hasEnoughItems(buyer, price)) {
                playNoMoneyError(buyer);
                Component errMsg = Component.translatable(
                        "uc7core.claimshop.error.notenoughitems",
                        price.getCount(),
                        price.getItem().getDescription().getString()
                );
                PacketDistributor.sendToPlayer(buyer, new BuyErrorPacket(Component.Serializer.toJson(errMsg, registry)));
                return;
            }

            Optional<Team> buyerTeamOpt = FTBTeamsAPI.api().getManager().getTeamForPlayer(buyer);
            if (buyerTeamOpt.isEmpty()) {
                playGenericError(buyer);
                Component errMsg = Component.translatable("uc7core.claimshop.error.noteam");
                PacketDistributor.sendToPlayer(buyer, new BuyErrorPacket(Component.Serializer.toJson(errMsg, registry)));
                return;
            }

            Team buyerTeam = buyerTeamOpt.get();
            if (buyerTeam.isServerTeam()) {
                playGenericError(buyer);
                Component errMsg = Component.translatable("uc7core.claimshop.error.serverbuyerteam");
                PacketDistributor.sendToPlayer(buyer, new BuyErrorPacket(Component.Serializer.toJson(errMsg, registry)));
                return;
            }

            if (entry.getSellerUUID().equals(buyer.getUUID())) {
                playGenericError(buyer);
                Component errMsg = Component.translatable("uc7core.claimshop.error.ownchunk");
                PacketDistributor.sendToPlayer(buyer, new BuyErrorPacket(Component.Serializer.toJson(errMsg, registry)));
                return;
            }

            Optional<Team> shopTeamOpt = FTBTeamsAPI.api().getManager().getTeams().stream()
                    .filter(t -> t.getName().getString().equals(entry.getShopTeamName()))
                    .findFirst();

            if (shopTeamOpt.isPresent()) {
                UUID shopTeamId = shopTeamOpt.get().getId();

                if (buyerTeam.getId().equals(shopTeamId)) {
                    playGenericError(buyer);
                    Component errMsg = Component.translatable("uc7core.claimshop.error.ownteamchunk");
                    PacketDistributor.sendToPlayer(buyer, new BuyErrorPacket(Component.Serializer.toJson(errMsg, registry)));
                    return;
                }

                if (!savedData.getData().canBuy(shopTeamId, buyerTeam.getId())) {
                    playGenericError(buyer);
                    int limit = savedData.getData().getTeamChunkLimit(shopTeamId);
                    Component errMsg = Component.translatable(
                            "uc7core.claimshop.error.chunklimit",
                            limit,
                            entry.getShopTeamName()
                    );
                    PacketDistributor.sendToPlayer(buyer, new BuyErrorPacket(Component.Serializer.toJson(errMsg, registry)));
                    return;
                }
            }

            ClaimedChunkManager manager = FTBChunksAPI.api().getManager();

            var existingChunk = manager.getChunk(dimPos);
            if (existingChunk == null) {
                playGenericError(buyer);
                Component errMsg = Component.translatable("uc7core.claimshop.error.notclaimed");
                PacketDistributor.sendToPlayer(buyer, new BuyErrorPacket(Component.Serializer.toJson(errMsg, registry)));
                savedData.getData().removeFromSale(pos);
                savedData.setDirty();
                ClaimShopSync.syncToAll(buyer.getServer());
                return;
            }
            existingChunk.getTeamData().unclaim(buyer.createCommandSourceStack(), dimPos, false);

            removeItems(buyer, price);
            savedData.getData().removeFromSale(pos);

            shopTeamOpt.ifPresent(shopTeam ->
                    savedData.getData().incrementBoughtCount(shopTeam.getId(), buyerTeam.getId())
            );

            // Auto-Reclaim: original Server-Team merken
            shopTeamOpt.ifPresent(shopTeam -> {
                if (shopTeam.isServerTeam() && savedData.getData().isAutoReclaimEnabled(shopTeam.getId())) {
                    savedData.getData().setChunkOriginalTeam(pos, shopTeam.getId());
                }
            });

            // Income: nur wenn kein Server-Team verkauft hat
            UUID sellerUUID = entry.getSellerUUID();
            boolean isServerTeamSale = shopTeamOpt.map(Team::isServerTeam).orElse(false);

            if (!isServerTeamSale && sellerUUID != null && !sellerUUID.equals(NO_SELLER)) {
                boolean incomeEnabled = shopTeamOpt
                        .map(t -> savedData.getData().isPlayerIncomeEnabled(t.getId()))
                        .orElse(true);

                ServerPlayer seller = buyer.getServer().getPlayerList().getPlayer(sellerUUID);
                if (seller != null) {
                    seller.sendSystemMessage(BuyingChunks.prefix(Component.translatable(
                            "uc7core.claimshop.sale.notification",
                            pos.x, pos.z,
                            buyer.getGameProfile().getName()
                    )));

                    if (incomeEnabled) {
                        giveOrDrop(seller, price.copy());
                        seller.sendSystemMessage(BuyingChunks.prefix(Component.translatable(
                                "uc7core.claimshop.income.received",
                                price.getCount(),
                                price.getItem().getDescription().getString(),
                                buyer.getGameProfile().getName()
                        )));
                    }
                } else if (incomeEnabled) {
                    savedData.getData().addPendingIncome(sellerUUID, price.copy());
                    BuyingChunks.LOGGER.info("ClaimShop: Seller {} is offline, queuing income {}x {}",
                            sellerUUID, price.getCount(), price.getItem().getDescription().getString());
                }
            }

            savedData.setDirty();

            BuyingChunks.markShopClaim(pos);
            manager.getOrCreateData(buyerTeam).claim(
                    buyer.createCommandSourceStack(),
                    dimPos,
                    false
            );

            ClaimShopSync.syncToAll(buyer.getServer());
            playSuccess(buyer);

            double cx = pos.x * 16 + 8;
            double cz = pos.z * 16 + 8;
            PurchaseEffectPacket buyerEffect = new PurchaseEffectPacket(pos.x, pos.z, entry.getTeamColor(), true);
            PurchaseEffectPacket spectatorEffect = new PurchaseEffectPacket(pos.x, pos.z, entry.getTeamColor(), false);
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(cx, p.getY(), cz) < 64 * 64) {
                    PacketDistributor.sendToPlayer(p, p.equals(buyer) ? buyerEffect : spectatorEffect);
                }
            }

            WebhookSender.sendChunkEvent(buyer, pos, level.dimension().location().toString(),
                    price, entry.getShopTeamName(), WebhookSender.EVENT_PURCHASE);

            buyer.sendSystemMessage(BuyingChunks.prefix(Component.translatable(
                    "uc7core.claimshop.buy.success",
                    pos.x, pos.z,
                    price.getCount(),
                    price.getItem().getDescription().getString()
            )));
        });
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static boolean hasEnoughItems(ServerPlayer player, ItemStack required) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (ItemStack.isSameItem(stack, required)) {
                count += stack.getCount();
                if (count >= required.getCount()) return true;
            }
        }
        return false;
    }

    private static void removeItems(ServerPlayer player, ItemStack required) {
        int toRemove = required.getCount();
        for (ItemStack stack : player.getInventory().items) {
            if (toRemove <= 0) break;
            if (ItemStack.isSameItem(stack, required)) {
                int remove = Math.min(stack.getCount(), toRemove);
                stack.shrink(remove);
                toRemove -= remove;
            }
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}