package snoopypupser.buyingchunks;

import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbchunks.api.event.ClaimedChunkEvent;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;
import snoopypupser.buyingchunks.claimshop.ClaimShopEventHandler;
import snoopypupser.buyingchunks.claimshop.ClaimShopSavedData;
import snoopypupser.buyingchunks.claimshop.ClaimShopSync;
import snoopypupser.buyingchunks.command.ClaimShopCommand;
import snoopypupser.buyingchunks.network.BuyingChunksNetwork;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod(BuyingChunks.MOD_ID)
public class BuyingChunks {

    public static final String MOD_ID = "buyingchunks";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Accumulator: pro Spieler sammeln wir paid/failed Chunks über einen Tick
    private static final Map<UUID, ClaimAccumulator> pendingMessages = new ConcurrentHashMap<>();

    private static class ClaimAccumulator {
        final ServerPlayer player;
        final ItemStack cost;
        int paidChunks = 0;
        int failedChunks = 0;
        boolean soundScheduled = false;

        ClaimAccumulator(ServerPlayer player, ItemStack cost) {
            this.player = player;
            this.cost = cost.copy();
        }
    }

    public BuyingChunks(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);
        new ClaimShopEventHandler().register();
        BuyingChunksNetwork.register(modEventBus);
        LOGGER.info("Buying Chunks is loading...");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        ClaimedChunkEvent.AFTER_CLAIM.register((source, chunk) -> {
            LOGGER.info("BaseCost: AFTER_CLAIM fired. Source: {}, Chunk: {}", source, chunk.getPos());

            if (source == null) {
                LOGGER.info("BaseCost: source is null, skipping.");
                return;
            }

            ServerPlayer player;
            try {
                player = source.getPlayerOrException();
                LOGGER.info("BaseCost: Player is {}", player.getGameProfile().getName());
            } catch (Exception e) {
                LOGGER.info("BaseCost: No player in source ({}), skipping.", e.getMessage());
                return;
            }

            ServerLevel level = (ServerLevel) player.level();
            ClaimShopSavedData savedData = ClaimShopSavedData.get(level);

            if (!savedData.getData().hasBaseCost()) {
                LOGGER.info("BaseCost: No base cost set, skipping.");
                return;
            }

            // Prüfe ob das claimende Team ein Server-Team ist
            Team claimingTeam = chunk.getTeamData().getTeam();
            if (claimingTeam.isServerTeam()) {
                LOGGER.info("BaseCost: Chunk claimed by server team '{}', skipping.", claimingTeam.getName().getString());
                return;
            }

            ItemStack cost = savedData.getData().getBaseCost();
            LOGGER.info("BaseCost: Required: {}x {}", cost.getCount(), cost.getItem().getDescriptionId());

            int playerCount = countItems(player, cost);
            LOGGER.info("BaseCost: Player has {}x {}", playerCount, cost.getItem().getDescriptionId());

            // Accumulator für diesen Spieler holen oder erstellen
            ClaimAccumulator acc = pendingMessages.computeIfAbsent(
                    player.getUUID(), k -> new ClaimAccumulator(player, cost)
            );

            if (!hasEnoughItems(player, cost)) {
                LOGGER.info("BaseCost: Not enough items! Attempting unclaim...");
                acc.failedChunks++;

                player.getServer().execute(() -> {
                    try {
                        var manager = dev.ftb.mods.ftbchunks.api.FTBChunksAPI.api().getManager();
                        var claimedChunk = manager.getChunk(chunk.getPos());
                        if (claimedChunk != null) {
                            var serverSource = player.getServer().createCommandSourceStack();
                            claimedChunk.getTeamData().unclaim(serverSource, chunk.getPos(), false, false);
                        }
                    } catch (Exception e) {
                        LOGGER.error("BaseCost: Unclaim error: {}", e.getMessage(), e);
                    }
                });
            } else {
                removeItems(player, cost);
                acc.paidChunks++;
                LOGGER.info("BaseCost: Removed {}x {} from {}", cost.getCount(), cost.getItem().getDescriptionId(), player.getGameProfile().getName());
            }

            // Einmalig pro Spieler einen delayed Task schedulen der die Zusammenfassung schickt
            if (!acc.soundScheduled) {
                acc.soundScheduled = true;
                player.getServer().execute(() -> {
                    ClaimAccumulator finalAcc = pendingMessages.remove(player.getUUID());
                    if (finalAcc == null) return;

                    int totalCost = finalAcc.paidChunks * finalAcc.cost.getCount();

                    if (finalAcc.paidChunks > 0) {
                        player.level().playSound(null, player.blockPosition(),
                                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.0f);

                        if (finalAcc.paidChunks == 1) {
                            // Einzelner Chunk: normale Nachricht
                            player.sendSystemMessage(Component.translatable(
                                    "uc7core.claimshop.basecost.paid",
                                    finalAcc.cost.getCount(),
                                    finalAcc.cost.getItem().getDescription().getString()
                            ));
                        } else {
                            // Mehrere Chunks: zusammengefasst
                            player.sendSystemMessage(Component.translatable(
                                    "uc7core.claimshop.basecost.paid.bulk",
                                    finalAcc.paidChunks,
                                    totalCost,
                                    finalAcc.cost.getItem().getDescription().getString()
                            ));
                        }
                    }

                    if (finalAcc.failedChunks > 0) {
                        player.level().playSound(null, player.blockPosition(),
                                SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 1.5f);

                        if (finalAcc.failedChunks == 1) {
                            player.sendSystemMessage(Component.translatable(
                                    "uc7core.claimshop.basecost.notenough",
                                    finalAcc.cost.getCount(),
                                    finalAcc.cost.getItem().getDescription().getString()
                            ));
                        } else {
                            player.sendSystemMessage(Component.translatable(
                                    "uc7core.claimshop.basecost.notenough.bulk",
                                    finalAcc.failedChunks,
                                    finalAcc.failedChunks * finalAcc.cost.getCount(),
                                    finalAcc.cost.getItem().getDescription().getString()
                            ));
                        }
                    }
                });
            }
        });

        LOGGER.info("Buying Chunks successfully initialized!");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ClaimShopCommand.register(event.getDispatcher(), event.getBuildContext());
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ClaimShopSync.syncToPlayer(player);

        ServerLevel level = player.getServer().overworld();
        ClaimShopSavedData savedData = ClaimShopSavedData.get(level);

        if (savedData.getData().hasPendingIncome(player.getUUID())) {
            List<ItemStack> pending = savedData.getData().getPendingIncome(player.getUUID());

            for (ItemStack stack : pending) {
                if (!player.getInventory().add(stack.copy())) {
                    player.drop(stack.copy(), false);
                }
            }

            int totalItems = pending.stream().mapToInt(ItemStack::getCount).sum();
            player.sendSystemMessage(Component.translatable(
                    "uc7core.claimshop.income.pending",
                    totalItems
            ));

            savedData.getData().clearPendingIncome(player.getUUID());
            savedData.setDirty();

            LOGGER.info("ClaimShop: Gave {} pending income items to {}", pending.size(), player.getGameProfile().getName());
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

    private static int countItems(ServerPlayer player, ItemStack required) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (ItemStack.isSameItem(stack, required)) count += stack.getCount();
        }
        return count;
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
}