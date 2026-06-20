package snoopypupser.buyingchunks.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.TeamProperties;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.registries.BuiltInRegistries;
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
import snoopypupser.buyingchunks.network.ListingToastPacket;
import snoopypupser.buyingchunks.network.OpenAdminScreenPacket;
import snoopypupser.buyingchunks.network.OpenMainDashboardScreenPacket;
import snoopypupser.buyingchunks.network.QuickbuySyncPacket;
import snoopypupser.buyingchunks.network.SyncAdminDataPacket;

import java.util.*;
import java.util.stream.Collectors;

public class ClaimShopCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
                Commands.literal("ftbshop")
                        .executes(ctx -> openDashboard(ctx.getSource()))

                        .then(Commands.literal("help")
                                .executes(ctx -> openDashboard(ctx.getSource()))
                        )

                        .then(Commands.literal("sell")
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
                                .executes(ctx -> removeFromSale(ctx.getSource(), null))
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> removeFromSale(ctx.getSource(),
                                                        new ChunkPos(
                                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                                IntegerArgumentType.getInteger(ctx, "z")
                                                        )))
                                        )
                                )
                        )

                        .then(Commands.literal("info")
                                .executes(ctx -> getInfo(ctx.getSource()))
                        )

                        .then(Commands.literal("list")
                                .executes(ctx -> listListings(ctx.getSource(), null))
                                .then(Commands.argument("item", ResourceArgument.resource(context, Registries.ITEM))
                                        .executes(ctx -> listListings(
                                                ctx.getSource(),
                                                ResourceArgument.getResource(ctx, "item", Registries.ITEM).value()
                                        ))
                                )
                        )

                        .then(Commands.literal("mylistings")
                                .executes(ctx -> myListings(ctx.getSource()))
                        )

                        .then(Commands.literal("quickbuy")
                                .executes(ctx -> toggleQuickbuy(ctx.getSource()))
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
            int teamColor = team.map(t -> t.getProperty(TeamProperties.COLOR).rgb() & 0xFFFFFF)
                    .orElse(0xFFFFFF);

            if (savedData.getData().isForSale(chunkPos)) {
                source.sendFailure(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error.alreadyforsale")));
                return 0;
            }

            ItemStack price = new ItemStack(item, amount);
            savedData.getData().setForSale(chunkPos, price, shopTeamName, player.getUUID(), teamColor);
            savedData.setDirty();
            ClaimShopSync.syncToAll(player.getServer());

            PacketDistributor.sendToPlayer(player, new ListingToastPacket(
                    chunkPos.x, chunkPos.z,
                    BuiltInRegistries.ITEM.getKey(item),
                    amount
            ));

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

    private static int removeFromSale(CommandSourceStack source, ChunkPos explicitPos) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = (ServerLevel) player.level();
            ChunkPos chunkPos = explicitPos != null ? explicitPos : new ChunkPos(player.blockPosition());

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

            ClaimedChunkManager manager = FTBChunksAPI.api().getManager();
            ClaimedChunk claimed = manager.getChunk(new dev.ftb.mods.ftblibrary.math.ChunkDimPos(level.dimension(), chunkPos));
            if (claimed != null) {
                Team chunkTeam = claimed.getTeamData().getTeam();
                UUID teamId = chunkTeam.getId();

                Optional<Team> playerTeam = FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
                if (playerTeam.isPresent()) {
                    ItemStack teamPrice = savedData.getData().getTeamPrice(playerTeam.get().getId());
                    if (!teamPrice.isEmpty()) {
                        source.sendSuccess(() -> BuyingChunks.prefix(Component.translatable(
                                "uc7core.claimshop.info.teamprice",
                                teamPrice.getCount(),
                                teamPrice.getItem().getDescription().getString()
                        )), false);
                    }
                }

                if (savedData.getData().hasTeamChunkLimit(teamId)) {
                    int limit = savedData.getData().getTeamChunkLimit(teamId);
                    int bought = playerTeam.map(t -> savedData.getData().getBoughtCount(teamId, t.getId())).orElse(0);
                    source.sendSuccess(() -> BuyingChunks.prefix(Component.translatable(
                            "uc7core.claimshop.info.shoplimit",
                            bought, limit
                    )), false);
                }
            }

            if (savedData.getData().hasBaseCost()) {
                ItemStack baseCost = savedData.getData().getBaseCost();
                source.sendSuccess(() -> BuyingChunks.prefix(Component.translatable(
                        "uc7core.claimshop.info.basecost",
                        baseCost.getCount(),
                        baseCost.getItem().getDescription().getString()
                )), false);
            }

            return 1;
        } catch (Exception e) {
            source.sendFailure(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error", e.getMessage())));
            return 0;
        }
    }

    private static int openDashboard(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            PacketDistributor.sendToPlayer(player, new OpenMainDashboardScreenPacket());
            return 1;
        } catch (Exception e) {
            source.sendFailure(BuyingChunks.prefix(Component.translatable("uc7core.claimshop.error", e.getMessage())));
            return 0;
        }
    }

    private static int listListings(CommandSourceStack source, Item filterItem) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = (ServerLevel) player.level();
            ClaimShopSavedData savedData = ClaimShopSavedData.get(level);
            Map<ChunkPos, ClaimShopEntry> all = savedData.getData().getAllForSaleMap();

            List<Map.Entry<ChunkPos, ClaimShopEntry>> allEntries = new ArrayList<>(all.entrySet());
            if (filterItem != null) {
                allEntries = allEntries.stream()
                        .filter(e -> ItemStack.isSameItem(e.getValue().getPrice(), new ItemStack(filterItem, 1)))
                        .collect(Collectors.toList());
            }

            final List<Map.Entry<ChunkPos, ClaimShopEntry>> entries = allEntries;
            final int totalSize = entries.size();

            if (totalSize == 0) {
                source.sendSuccess(() -> BuyingChunks.prefix(Component.translatable(
                        "uc7core.claimshop.list.none"
                )), false);
                return 1;
            }

            int limit = 20;
            int showing = Math.min(limit, totalSize);
            source.sendSuccess(() -> BuyingChunks.prefix(Component.translatable(
                    "uc7core.claimshop.list.header", showing, totalSize
            )), false);

            for (int i = 0; i < showing; i++) {
                Map.Entry<ChunkPos, ClaimShopEntry> e = entries.get(i);
                ChunkPos pos = e.getKey();
                ClaimShopEntry entry = e.getValue();
                source.sendSuccess(() -> Component.literal(String.format("  [%d, %d]  %dx %s  (%s)",
                        pos.x, pos.z,
                        entry.getPrice().getCount(),
                        entry.getPrice().getItem().getDescription().getString(),
                        entry.getShopTeamName()
                )), false);
            }

            if (totalSize > limit) {
                source.sendSuccess(() -> Component.translatable(
                        "uc7core.claimshop.list.more", totalSize - limit
                ).withStyle(net.minecraft.ChatFormatting.GRAY), false);
            }
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int myListings(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = (ServerLevel) player.level();

            ClaimShopSavedData overworldData = ClaimShopSavedData.get(player.getServer().overworld());
            ClaimShopData data = overworldData.getData();

            List<String> ownChunks = new ArrayList<>();
            for (Map.Entry<ChunkPos, ClaimShopEntry> e : data.getAllForSaleMap().entrySet()) {
                if (e.getValue().getSellerUUID().equals(player.getUUID())) {
                    ClaimShopEntry entry = e.getValue();
                    ownChunks.add(String.format("  [%d, %d]  %dx %s",
                            e.getKey().x, e.getKey().z,
                            entry.getPrice().getCount(),
                            entry.getPrice().getItem().getDescription().getString()));
                }
            }

            source.sendSuccess(() -> BuyingChunks.prefix(Component.translatable(
                    "uc7core.claimshop.mylistings.title"
            )), false);

            if (ownChunks.isEmpty()) {
                source.sendSuccess(() -> Component.translatable(
                        "uc7core.claimshop.mylistings.none"
                ).withStyle(net.minecraft.ChatFormatting.GRAY), false);
            } else {
                for (String line : ownChunks) {
                    source.sendSuccess(() -> Component.literal(line), false);
                }
            }

            List<ItemStack> pending = data.getPendingIncome(player.getUUID());
            if (!pending.isEmpty()) {
                int totalCount = pending.stream().mapToInt(ItemStack::getCount).sum();
                ItemStack first = pending.get(0);
                source.sendSuccess(() -> BuyingChunks.prefix(Component.translatable(
                        "uc7core.claimshop.mylistings.pending",
                        totalCount,
                        first.getItem().getDescription().getString()
                )), false);
            }
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int toggleQuickbuy(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = source.getLevel();
            ClaimShopSavedData savedData = ClaimShopSavedData.get(level);
            boolean nowEnabled = savedData.getData().toggleQuickbuy(player.getUUID());
            savedData.setDirty();

            PacketDistributor.sendToPlayer(player, new QuickbuySyncPacket(nowEnabled));

            if (nowEnabled) {
                source.sendSuccess(() -> BuyingChunks.prefix(Component.translatable(
                        "uc7core.claimshop.quickbuy.enabled"
                )), true);
            } else {
                source.sendSuccess(() -> BuyingChunks.prefix(Component.translatable(
                        "uc7core.claimshop.quickbuy.disabled"
                )), true);
            }
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
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
