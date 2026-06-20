package snoopypupser.buyingchunks.claimshop;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.event.ClaimedChunkEvent;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import snoopypupser.buyingchunks.BuyingChunks;
import dev.ftb.mods.ftbteams.api.property.TeamProperties;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.network.PacketDistributor;
import snoopypupser.buyingchunks.network.ListingToastPacket;

import java.util.Optional;
import java.util.UUID;

public class ClaimShopEventHandler {

    private static final UUID NO_SELLER = new UUID(0, 0);

    public void register() {
        ClaimedChunkEvent.AFTER_CLAIM.register(this::onChunkClaimed);
        ClaimedChunkEvent.AFTER_UNCLAIM.register(this::onChunkUnclaimed);
    }

    private void onChunkUnclaimed(CommandSourceStack source, ClaimedChunk chunk) {
        BuyingChunks.LOGGER.info("UNCLAIM fired. Source entity: {}, Chunk: {}",
                source.getEntity() != null ? source.getEntity().getName().getString() : "null",
                chunk.getPos());

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            BuyingChunks.LOGGER.info("UNCLAIM: No player in source, skipping.");
            return;
        }

        BuyingChunks.LOGGER.info("UNCLAIM: Player {} unclaimed chunk {}", player.getGameProfile().getName(), chunk.getPos());

        ServerLevel level = (ServerLevel) player.level();
        ClaimShopSavedData savedData = ClaimShopSavedData.get(level);
        ChunkPos pos = chunk.getPos().chunkPos();

        if (savedData.getData().isForSale(pos)) {
            savedData.getData().removeFromSale(pos);
            savedData.setDirty();
            BuyingChunks.LOGGER.info("UNCLAIM: Removed chunk {} from shop.", pos);
            player.getServer().execute(() -> ClaimShopSync.syncToAll(player.getServer()));
            return;
        }

        UUID originalTeamId = savedData.getData().getChunkOriginalTeam(pos);
        if (originalTeamId == null) {
            BuyingChunks.LOGGER.info("UNCLAIM: No original team tracked for chunk {}, skipping.", pos);
            return;
        }

        if (!savedData.getData().isAutoReclaimEnabled(originalTeamId)) {
            BuyingChunks.LOGGER.info("UNCLAIM: Auto-reclaim not enabled for team {}, skipping.", originalTeamId);
            savedData.getData().removeChunkOriginalTeam(pos);
            savedData.setDirty();
            return;
        }

        Optional<Team> serverTeamOpt = FTBTeamsAPI.api().getManager().getTeams().stream()
                .filter(t -> t.getId().equals(originalTeamId))
                .findFirst();

        if (serverTeamOpt.isEmpty()) {
            BuyingChunks.LOGGER.info("UNCLAIM: Original team {} not found, skipping.", originalTeamId);
            savedData.getData().removeChunkOriginalTeam(pos);
            savedData.setDirty();
            return;
        }

        Team serverTeam = serverTeamOpt.get();
        BuyingChunks.LOGGER.info("AUTO-RECLAIM: Reclaiming chunk {} back to team '{}'", pos, serverTeam.getName().getString());

        ChunkDimPos dimPos = new ChunkDimPos(level.dimension(), pos);
        ClaimedChunkManager manager = FTBChunksAPI.api().getManager();

        player.getServer().execute(() -> {
            manager.getOrCreateData(serverTeam).claim(
                    player.getServer().createCommandSourceStack(),
                    dimPos,
                    false
            );
            savedData.getData().removeChunkOriginalTeam(pos);
            savedData.setDirty();
            ClaimShopSync.syncToAll(player.getServer());
        });
    }

    private void onChunkClaimed(CommandSourceStack source, ClaimedChunk chunk) {
        BuyingChunks.LOGGER.info("CLAIM fired. Source entity: {}, Chunk: {}",
                source.getEntity() != null ? source.getEntity().getName().getString() : "null",
                chunk.getPos());

        ServerLevel level = (ServerLevel) source.getLevel();
        ClaimShopSavedData savedData = ClaimShopSavedData.get(level);

        Team claimTeam = chunk.getTeamData().getTeam();
        UUID teamId = claimTeam.getId();

        BuyingChunks.LOGGER.info("CLAIM: Team is '{}' (id: {}), isServerTeam: {}",
                claimTeam.getName().getString(), teamId, claimTeam.isServerTeam());

        if (!savedData.getData().hasTeamPrice(teamId)) {
            BuyingChunks.LOGGER.info("CLAIM: No team price set for team '{}', skipping.", claimTeam.getName().getString());
            return;
        }

        ItemStack price = savedData.getData().getTeamPrice(teamId);
        if (price.isEmpty()) {
            BuyingChunks.LOGGER.info("CLAIM: Team price is empty, skipping.");
            return;
        }

        ChunkPos chunkPos = chunk.getPos().chunkPos();
        if (savedData.getData().isForSale(chunkPos)) {
            BuyingChunks.LOGGER.info("CLAIM: Chunk {} already for sale, skipping auto-listing.", chunkPos);
            return;
        }

        // Server-Team: direkt zum Verkauf stellen, kein Spieler nötig
        if (claimTeam.isServerTeam()) {
            BuyingChunks.LOGGER.info("CLAIM: Server team, setting chunk for sale automatically.");
            int teamColor = claimTeam.getProperty(TeamProperties.COLOR).rgb() & 0xFFFFFF;
            savedData.getData().setForSale(
                    chunk.getPos().chunkPos(),
                    price.copy(),
                    claimTeam.getName().getString(),
                    NO_SELLER,
                    teamColor
            );
            savedData.setDirty();
            source.getServer().execute(() -> ClaimShopSync.syncToAll(source.getServer()));
            return;
        }

        // Ab hier brauchen wir einen Spieler
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            BuyingChunks.LOGGER.info("CLAIM: No player in source for non-server team, skipping.");
            return;
        }

        BuyingChunks.LOGGER.info("CLAIM: Player {} claimed chunk {}", player.getGameProfile().getName(), chunk.getPos());

        int playerCount = countItems(player, price);
        BuyingChunks.LOGGER.info("CLAIM: Player has {}x {}, needs {}x {}",
                playerCount, price.getItem().getDescriptionId(),
                price.getCount(), price.getItem().getDescriptionId());

        if (!hasEnoughItems(player, price)) {
            BuyingChunks.LOGGER.info("CLAIM: Not enough items, unclaiming chunk {}.", chunk.getPos());
            chunk.getTeamData().unclaim(source, chunk.getPos(), true);
            player.sendSystemMessage(BuyingChunks.prefix(Component.translatable(
                    "uc7core.claimshop.error.notenoughitems",
                    price.getCount(),
                    price.getItem().getDescription().getString()
            )));
            return;
        }

        removeItems(player, price);
        BuyingChunks.LOGGER.info("CLAIM: Removed {}x {} from player {}.",
                price.getCount(), price.getItem().getDescriptionId(), player.getGameProfile().getName());

        player.sendSystemMessage(BuyingChunks.prefix(Component.translatable(
                "uc7core.claimshop.teamprice.paid",
                price.getCount(),
                price.getItem().getDescription().getString()
        )));
    }

    private boolean hasEnoughItems(ServerPlayer player, ItemStack required) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (ItemStack.isSameItem(stack, required)) {
                count += stack.getCount();
                if (count >= required.getCount()) return true;
            }
        }
        return false;
    }

    private int countItems(ServerPlayer player, ItemStack required) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (ItemStack.isSameItem(stack, required)) count += stack.getCount();
        }
        return count;
    }

    private void removeItems(ServerPlayer player, ItemStack required) {
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
}