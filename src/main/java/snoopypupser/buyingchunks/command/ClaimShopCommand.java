package snoopypupser.buyingchunks.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import snoopypupser.buyingchunks.BuyingChunks;
import snoopypupser.buyingchunks.claimshop.ClaimShopData;
import snoopypupser.buyingchunks.claimshop.ClaimShopEntry;
import snoopypupser.buyingchunks.claimshop.ClaimShopSavedData;
import snoopypupser.buyingchunks.claimshop.ClaimShopSync;
import snoopypupser.buyingchunks.network.OpenAdminScreenPacket;
import snoopypupser.buyingchunks.network.SyncAdminDataPacket;

import java.util.Optional;

public class ClaimShopCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
                Commands.literal("ftbshop")

                        .then(Commands.literal("set")
                                .then(Commands.argument("item", ResourceArgument.resource(context, Registries.ITEM))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> setForSale(
                                                        ctx.getSource(),
                                                        ResourceArgument.getResource(ctx, "item", Registries.ITEM).value(),
                                                        IntegerArgumentType.getInteger(ctx, "amount")
                                                ))
                                        )
                                )
                        )

                        .then(Commands.literal("remove")
                                .executes(ctx -> removeFromSale(ctx.getSource()))
                        )

                        .then(Commands.literal("info")
                                .executes(ctx -> getInfo(ctx.getSource()))
                        )

                        .then(Commands.literal("admin")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> openAdminDashboard(ctx.getSource()))
                        )
        );
    }

    private static int setForSale(CommandSourceStack source, Item item, int amount) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = (ServerLevel) player.level();
            ChunkPos chunkPos = new ChunkPos(player.blockPosition());

            ClaimShopSavedData savedData = ClaimShopSavedData.get(level);
            boolean isAdmin = source.hasPermission(2);
            boolean playerSellOn = savedData.getData().isPlayerSellEnabled();

            if (!isAdmin && !playerSellOn) {
                source.sendFailure(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.playersell_disabled")));
                return 0;
            }

            ClaimedChunkManager manager = FTBChunksAPI.api().getManager();
            ClaimedChunk claimed = manager.getChunk(new dev.ftb.mods.ftblibrary.math.ChunkDimPos(level.dimension(), chunkPos));
            if (claimed == null) {
                source.sendFailure(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.notclaimed")));
                return 0;
            }

            if (!isAdmin) {
                Optional<Team> playerTeam = FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
                Team chunkTeam = claimed.getTeamData().getTeam();
                if (playerTeam.isEmpty() || !playerTeam.get().getId().equals(chunkTeam.getId())) {
                    source.sendFailure(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.notowner")));
                    return 0;
                }
            }

            Optional<Team> team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
            String shopTeamName = team.map(t -> t.getName().getString())
                    .orElse(player.getGameProfile().getName());

            ItemStack price = new ItemStack(item, amount);
            savedData.getData().setForSale(chunkPos, price, shopTeamName, player.getUUID());
            savedData.setDirty();
            ClaimShopSync.syncToAll(player.getServer());

            source.sendSuccess(() -> BuyingChunks.prefix(Component.translatable(
                    "uc7core.claimshop.set.success",
                    chunkPos.x, chunkPos.z, amount,
                    item.getDescription().getString(),
                    shopTeamName
            )), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error", e.getMessage())));
            return 0;
        }
    }

    private static int removeFromSale(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = (ServerLevel) player.level();
            ChunkPos chunkPos = new ChunkPos(player.blockPosition());

            ClaimShopSavedData savedData = ClaimShopSavedData.get(level);
            boolean isAdmin = source.hasPermission(2);
            boolean playerSellOn = savedData.getData().isPlayerSellEnabled();

            if (!isAdmin && !playerSellOn) {
                source.sendFailure(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.playersell_disabled")));
                return 0;
            }

            ClaimShopEntry entry = savedData.getData().getEntry(chunkPos);
            if (entry == null) {
                source.sendFailure(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.info.notforsale", chunkPos.x, chunkPos.z)));
                return 0;
            }

            if (!isAdmin) {
                ClaimedChunkManager manager = FTBChunksAPI.api().getManager();
                ClaimedChunk claimed = manager.getChunk(new dev.ftb.mods.ftblibrary.math.ChunkDimPos(level.dimension(), chunkPos));
                if (claimed != null) {
                    Optional<Team> playerTeam = FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
                    Team chunkTeam = claimed.getTeamData().getTeam();
                    if (playerTeam.isEmpty() || !playerTeam.get().getId().equals(chunkTeam.getId())) {
                        source.sendFailure(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.notowner")));
                        return 0;
                    }
                }
            }

            savedData.getData().removeFromSale(chunkPos);
            savedData.setDirty();
            ClaimShopSync.syncToAll(player.getServer());

            source.sendSuccess(() -> BuyingChunks.prefix(Component.translatable(
                    "uc7core.claimshop.remove.success", chunkPos.x, chunkPos.z
            )), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error", e.getMessage())));
            return 0;
        }
    }

    private static int getInfo(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = (ServerLevel) player.level();
            ChunkPos chunkPos = new ChunkPos(player.blockPosition());

            ClaimShopSavedData savedData = ClaimShopSavedData.get(level);
            ClaimShopEntry entry = savedData.getData().getEntry(chunkPos);

            if (entry != null) {
                source.sendSuccess(() -> BuyingChunks.prefix(Component.translatable(
                        "uc7core.claimshop.info.forsale",
                        chunkPos.x, chunkPos.z,
                        entry.getPrice().getCount(),
                        entry.getPrice().getItem().getDescription().getString(),
                        entry.getShopTeamName()
                )), false);
            } else {
                source.sendSuccess(() -> BuyingChunks.prefix(Component.translatable(
                        "uc7core.claimshop.info.notforsale", chunkPos.x, chunkPos.z
                )), false);
            }
            return 1;
        } catch (Exception e) {
            source.sendFailure(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error", e.getMessage())));
            return 0;
        }
    }

    private static int openAdminDashboard(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = source.getLevel();
            ClaimShopSavedData savedData = ClaimShopSavedData.get(level);
            ClaimShopData data = savedData.getData();

            SyncAdminDataPacket adminPacket = new SyncAdminDataPacket(
                    data.getAllTeamPrices(),
                    data.getAllTeamChunkLimits(),
                    data.isPlayerSellEnabled(),
                    data.getPlayerIncomeDisabledSet(),
                    data.getAutoReclaimTeamsSet()
            );
            PacketDistributor.sendToPlayer(player, adminPacket);
            PacketDistributor.sendToPlayer(player, new OpenAdminScreenPacket());

            return 1;
        } catch (Exception e) {
            source.sendFailure(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error", e.getMessage())));
            return 0;
        }
    }
}
