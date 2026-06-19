package snoopypupser.buyingchunks.command;

import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.api.property.TeamProperties;
import dev.ftb.mods.ftbteams.data.AbstractTeam;
import dev.ftb.mods.ftbteams.data.ServerTeam;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import snoopypupser.buyingchunks.BuyingChunks;
import snoopypupser.buyingchunks.claimshop.ClaimShopData;
import snoopypupser.buyingchunks.claimshop.ClaimShopSavedData;
import snoopypupser.buyingchunks.claimshop.ClaimShopSync;
import snoopypupser.buyingchunks.network.AdminActionPacket;
import snoopypupser.buyingchunks.network.SyncAdminDataPacket;
import snoopypupser.buyingchunks.network.TeamListRefreshPacket;

import java.util.Optional;
import java.util.UUID;

public class AdminActionHandler {

    public static void handle(AdminActionPacket packet, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();

        if (!player.hasPermissions(2)) {
            player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.nopermission")));
            return;
        }

        ServerLevel level = player.getServer().overworld();
        ClaimShopSavedData savedData = ClaimShopSavedData.get(level);
        ClaimShopData data = savedData.getData();
        CommandSourceStack source = player.getServer().createCommandSourceStack();

        switch (packet.actionType()) {
            case AdminActionPacket.ACTION_SET_BASE_COST -> {
                ResourceLocation id = ResourceLocation.parse(packet.itemId());
                Item item = BuiltInRegistries.ITEM.get(id);
                if (item == null || item == BuiltInRegistries.ITEM.get(ResourceLocation.parse("air"))) {
                    player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.invalid_item", packet.itemId())));
                    return;
                }
                int amount = Math.max(1, Math.min(64, packet.amount()));
                data.setBaseCost(item, amount);
                savedData.setDirty();
                ClaimShopSync.syncToAll(player.getServer());
                syncAdminToPlayer(player);
                player.sendSystemMessage(BuyingChunks.prefix(Component.translatable(
                        "uc7core.claimshop.basecost.set.success", amount, item.getDescription().getString()
                )));
            }
            case AdminActionPacket.ACTION_REMOVE_BASE_COST -> {
                data.removeBaseCost();
                savedData.setDirty();
                ClaimShopSync.syncToAll(player.getServer());
                syncAdminToPlayer(player);
                player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.basecost.remove.success")));
            }
            case AdminActionPacket.ACTION_SET_PLAYER_SELL -> {
                data.setPlayerSellEnabled(packet.enabled());
                savedData.setDirty();
                syncAdminToPlayer(player);
                player.sendSystemMessage(BuyingChunks.prefix(Component.translatable(
                        packet.enabled() ? "uc7core.claimshop.playersell.enabled" : "uc7core.claimshop.playersell.disabled"
                )));
            }
            case AdminActionPacket.ACTION_SET_TEAM_PRICE -> {
                Optional<Team> team = findTeam(packet.teamId());
                if (team.isEmpty()) {
                    player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.teamnotfound", packet.teamId())));
                    return;
                }
                ResourceLocation id = ResourceLocation.parse(packet.itemId());
                Item item = BuiltInRegistries.ITEM.get(id);
                if (item == null || item == BuiltInRegistries.ITEM.get(ResourceLocation.parse("air"))) {
                    player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.invalid_item", packet.itemId())));
                    return;
                }
                int amount = Math.max(1, Math.min(64, packet.amount()));
                data.setTeamPrice(team.get().getId(), new ItemStack(item, amount));
                savedData.setDirty();
                syncAdminToPlayer(player);
                player.sendSystemMessage(BuyingChunks.prefix(Component.translatable(
                        "uc7core.claimshop.teamprice.set.success",
                        team.get().getName().getString(), amount, item.getDescription().getString()
                )));
            }
            case AdminActionPacket.ACTION_REMOVE_TEAM_PRICE -> {
                Optional<Team> team = findTeam(packet.teamId());
                if (team.isEmpty()) {
                    player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.teamnotfound", packet.teamId())));
                    return;
                }
                data.removeTeamPrice(team.get().getId());
                savedData.setDirty();
                syncAdminToPlayer(player);
                player.sendSystemMessage(BuyingChunks.prefix(Component.translatable(
                        "uc7core.claimshop.teamprice.remove.success", team.get().getName().getString()
                )));
            }
            case AdminActionPacket.ACTION_SET_CHUNK_LIMIT -> {
                Optional<Team> team = findTeam(packet.teamId());
                if (team.isEmpty()) {
                    player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.teamnotfound", packet.teamId())));
                    return;
                }
                int limit = Math.max(1, Math.min(9999, packet.amount()));
                data.setTeamChunkLimit(team.get().getId(), limit);
                savedData.setDirty();
                syncAdminToPlayer(player);
                player.sendSystemMessage(BuyingChunks.prefix(Component.translatable(
                        "uc7core.claimshop.chunklimit.set.success", team.get().getName().getString(), limit
                )));
            }
            case AdminActionPacket.ACTION_REMOVE_CHUNK_LIMIT -> {
                Optional<Team> team = findTeam(packet.teamId());
                if (team.isEmpty()) {
                    player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.teamnotfound", packet.teamId())));
                    return;
                }
                data.removeTeamChunkLimit(team.get().getId());
                savedData.setDirty();
                syncAdminToPlayer(player);
                player.sendSystemMessage(BuyingChunks.prefix(Component.translatable(
                        "uc7core.claimshop.chunklimit.remove.success", team.get().getName().getString()
                )));
            }
            case AdminActionPacket.ACTION_SET_PLAYER_INCOME -> {
                Optional<Team> team = findTeam(packet.teamId());
                if (team.isEmpty()) {
                    player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.teamnotfound", packet.teamId())));
                    return;
                }
                data.setPlayerIncomeEnabled(team.get().getId(), packet.enabled());
                savedData.setDirty();
                syncAdminToPlayer(player);
                player.sendSystemMessage(BuyingChunks.prefix(Component.translatable(
                        packet.enabled() ? "uc7core.claimshop.playerincome.enabled" : "uc7core.claimshop.playerincome.disabled",
                        team.get().getName().getString()
                )));
            }
            case AdminActionPacket.ACTION_SET_AUTO_RECLAIM -> {
                Optional<Team> team = findTeam(packet.teamId());
                if (team.isEmpty()) {
                    player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.teamnotfound", packet.teamId())));
                    return;
                }
                if (!team.get().isServerTeam()) {
                    player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.notserverteam")));
                    return;
                }
                data.setAutoReclaim(team.get().getId(), packet.enabled());
                savedData.setDirty();
                syncAdminToPlayer(player);
                player.sendSystemMessage(BuyingChunks.prefix(Component.translatable(
                        packet.enabled() ? "uc7core.claimshop.autoreclaim.enabled" : "uc7core.claimshop.autoreclaim.disabled",
                        team.get().getName().getString()
                )));
            }
            case AdminActionPacket.ACTION_CREATE_SERVER_TEAM -> {
                String name = packet.itemId().trim();
                if (name.length() < 3) {
                    player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.admin.createserverteam.error.tooshort")));
                    return;
                }
                player.getServer().getCommands().performPrefixedCommand(
                        player.getServer().createCommandSourceStack(),
                        "ftbteams server create " + name
                );
                PacketDistributor.sendToPlayer(player, new TeamListRefreshPacket());
            }
            case AdminActionPacket.ACTION_DELETE_SERVER_TEAM -> {
                Optional<Team> team = findTeam(packet.teamId());
                if (team.isEmpty()) {
                    player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.teamnotfound", packet.teamId())));
                    return;
                }
                ((ServerTeam) team.get()).delete(source);
                PacketDistributor.sendToPlayer(player, new TeamListRefreshPacket());
            }
            case AdminActionPacket.ACTION_UPDATE_TEAM_NAME -> {
                Optional<Team> team = findTeam(packet.teamId());
                if (team.isEmpty()) {
                    player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.teamnotfound", packet.teamId())));
                    return;
                }
                String newName = packet.itemId().trim();
                if (newName.length() < 3) {
                    player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.admin.createserverteam.error.tooshort")));
                    return;
                }
                team.get().setProperty(TeamProperties.DISPLAY_NAME, newName);
                team.get().markDirty();
                player.sendSystemMessage(BuyingChunks.prefix(Component.translatable(
                        "uc7core.claimshop.admin.serverteam.rename.success", newName)));
            }
            case AdminActionPacket.ACTION_UPDATE_TEAM_COLOR -> {
                Optional<Team> team = findTeam(packet.teamId());
                if (team.isEmpty()) {
                    player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.teamnotfound", packet.teamId())));
                    return;
                }
                int rgb = packet.amount();
                ((AbstractTeam) team.get()).settings(source, TeamProperties.COLOR, String.format("#%06X", rgb & 0xFFFFFF));
            }
            case AdminActionPacket.ACTION_SET_BOOL_PROPERTY -> {
                Optional<Team> team = findTeam(packet.teamId());
                if (team.isEmpty()) {
                    player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.teamnotfound", packet.teamId())));
                    return;
                }
                String key = packet.itemId();
                boolean value = packet.enabled();
                AbstractTeam t = (AbstractTeam) team.get();
                switch (key) {
                    case "allow_explosions" -> t.settings(source, FTBChunksProperties.ALLOW_EXPLOSIONS, value ? "true" : "false");
                    case "allow_mob_griefing" -> t.settings(source, FTBChunksProperties.ALLOW_MOB_GRIEFING, value ? "true" : "false");
                    case "allow_pvp" -> t.settings(source, FTBChunksProperties.ALLOW_PVP, value ? "true" : "false");
                    case "allow_all_fake_players" -> t.settings(source, FTBChunksProperties.ALLOW_ALL_FAKE_PLAYERS, value ? "true" : "false");
                    case "allow_fake_players_by_id" -> t.settings(source, FTBChunksProperties.ALLOW_FAKE_PLAYERS_BY_ID, value ? "true" : "false");
                    default -> {
                        player.sendSystemMessage(BuyingChunks.prefix(Component.literal("Unknown property: " + key)));
                        return;
                    }
                }
            }
            case AdminActionPacket.ACTION_SET_PRIVACY_PROPERTY -> {
                Optional<Team> team = findTeam(packet.teamId());
                if (team.isEmpty()) {
                    player.sendSystemMessage(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.teamnotfound", packet.teamId())));
                    return;
                }
                String[] parts = packet.itemId().split("=", 2);
                if (parts.length < 2) return;
                String key = parts[0];
                PrivacyMode mode = PrivacyMode.valueOf(parts[1]);
                AbstractTeam t = (AbstractTeam) team.get();
                switch (key) {
                    case "block_edit_mode" -> t.settings(source, FTBChunksProperties.BLOCK_EDIT_MODE, mode.getSerializedName());
                    case "block_interact_mode" -> t.settings(source, FTBChunksProperties.BLOCK_INTERACT_MODE, mode.getSerializedName());
                    case "entity_interact_mode" -> t.settings(source, FTBChunksProperties.ENTITY_INTERACT_MODE, mode.getSerializedName());
                    case "nonliving_entity_attack_mode" -> t.settings(source, FTBChunksProperties.NONLIVING_ENTITY_ATTACK_MODE, mode.getSerializedName());
                    case "claim_visibility" -> t.settings(source, FTBChunksProperties.CLAIM_VISIBILITY, mode.getSerializedName());
                    case "location_mode" -> t.settings(source, FTBChunksProperties.LOCATION_MODE, mode.getSerializedName());
                    case "block_edit_and_interact_mode" -> t.settings(source, FTBChunksProperties.BLOCK_EDIT_AND_INTERACT_MODE, mode.getSerializedName());
                    default -> {
                        player.sendSystemMessage(BuyingChunks.prefix(Component.literal("Unknown property: " + key)));
                        return;
                    }
                }
            }
        }
    }

    private static void syncAdminToPlayer(ServerPlayer player) {
        ServerLevel level = player.getServer().overworld();
        ClaimShopSavedData savedData = ClaimShopSavedData.get(level);
        ClaimShopData data = savedData.getData();

        SyncAdminDataPacket packet = new SyncAdminDataPacket(
                data.getAllTeamPrices(),
                data.getAllTeamChunkLimits(),
                data.isPlayerSellEnabled(),
                data.getPlayerIncomeDisabledSet(),
                data.getAutoReclaimTeamsSet()
        );
        PacketDistributor.sendToPlayer(player, packet);
    }

    private static Optional<Team> findTeam(UUID teamId) {
        if (teamId.equals(AdminActionPacket.NO_TEAM)) return Optional.empty();
        return FTBTeamsAPI.api().getManager().getTeams().stream()
                .filter(t -> t.getId().equals(teamId))
                .findFirst();
    }
}
