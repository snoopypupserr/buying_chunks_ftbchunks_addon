package snoopypupser.buyingchunks.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import snoopypupser.buyingchunks.BuyingChunks;
import snoopypupser.buyingchunks.claimshop.ClaimShopData;
import snoopypupser.buyingchunks.claimshop.ClaimShopSavedData;
import snoopypupser.buyingchunks.network.OpenAdminScreenPacket;
import snoopypupser.buyingchunks.network.OpenMainDashboardScreenPacket;
import snoopypupser.buyingchunks.network.SyncAdminDataPacket;

import java.util.List;

public class ClaimShopCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
                Commands.literal("ftbshop")
                        .then(Commands.literal("gui")
                                .executes(ctx -> openDashboard(ctx.getSource()))
                        )
                        .then(Commands.literal("admin")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> openAdminDashboard(ctx.getSource()))
                        )
        );
    }

    private static int openDashboard(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = player.getServer().overworld();
            ClaimShopSavedData savedData = ClaimShopSavedData.get(level);
            ClaimShopData data = savedData.getData();
            List<ItemStack> pending = data.getPendingIncome(player.getUUID());
            boolean quickbuy = data.isQuickbuyEnabled(player.getUUID());
            PacketDistributor.sendToPlayer(player, new OpenMainDashboardScreenPacket(pending, quickbuy));
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
