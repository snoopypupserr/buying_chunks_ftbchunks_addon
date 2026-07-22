package snoopypupser.buyingchunks;

import com.mojang.logging.LogUtils;
import dev.architectury.event.CompoundEventResult;
import dev.ftb.mods.ftbchunks.api.ClaimResult;
import dev.ftb.mods.ftbchunks.api.event.ClaimedChunkEvent;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;
import snoopypupser.buyingchunks.claimshop.ClaimShopEventHandler;
import snoopypupser.buyingchunks.config.BuyingChunksConfig;
import snoopypupser.buyingchunks.config.ServerConfig;
import snoopypupser.buyingchunks.event.ChunkEnterHandler;
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

    public static Component prefix(Component message) {
        return Component.translatable("buyingchunks.prefix.name")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x5DADE2)))
                .append(Component.translatable("buyingchunks.prefix.arrow")
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xA0A0A0))))
                .append(message.copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
    }


    // Shop-KÃ¤ufe markieren, damit AFTER_CLAIM (Base Cost) sie Ã¼berspringt
    private static final Set<ChunkPos> pendingShopClaims = ConcurrentHashMap.newKeySet();

    public static void markShopClaim(ChunkPos pos) {
        pendingShopClaims.add(pos);
    }

    public static boolean consumeShopClaim(ChunkPos pos) {
        return pendingShopClaims.remove(pos);
    }

    public static boolean isShopClaim(ChunkPos pos) {
        return pendingShopClaims.contains(pos);
    }

    // Accumulator: pro Spieler sammeln wir paid/failed Chunks Ã¼ber einen Tick
    private static final Map<UUID, ClaimAccumulator> pendingMessages = new ConcurrentHashMap<>();

    private static class ClaimAccumulator {
        final ServerPlayer player;
        final ItemStack cost;
        int paidChunks = 0;
        boolean soundScheduled = false;

        ClaimAccumulator(ServerPlayer player, ItemStack cost) {
            this.player = player;
            this.cost = cost.copy();
        }
    }

    public BuyingChunks(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, BuyingChunksConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new ChunkEnterHandler());
        new ClaimShopEventHandler().register();
        BuyingChunksNetwork.register(modEventBus);
        LOGGER.info("Buying Chunks is loading...");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // BEFORE_CLAIM: PrÃ¼ft ob der Spieler genug Items fÃ¼r Base Cost hat,
        // bevor der Chunk Ã¼berhaupt gclaimed wird. So vermeiden wir das
        // "hanging" der FTB Chunks GUI.
        ClaimedChunkEvent.BEFORE_CLAIM.register((source, chunk) -> {
            if (source == null) return CompoundEventResult.pass();

            ServerPlayer player;
            try {
                player = source.getPlayerOrException();
            } catch (Exception e) {
                return CompoundEventResult.pass();
            }

            ResourceKey<Level> chunkDimKey = chunk.getPos().dimension();
            ClaimShopSavedData savedData = ClaimShopSavedData.get(player.getServer().overworld());
            ResourceLocation chunkDim = chunkDimKey.location();

            if (!savedData.getData().hasBaseCost(chunkDim)) return CompoundEventResult.pass();

            // Shop-Käufe überspringen (werden im Shop-Code behandelt)
            if (isShopClaim(chunk.getPos().chunkPos())) return CompoundEventResult.pass();

            // Server-Teams überspringen
            Team claimingTeam = chunk.getTeamData().getTeam();
            if (claimingTeam.isServerTeam()) return CompoundEventResult.pass();

            ItemStack cost = savedData.getData().getBaseCost(chunkDim);
            if (!hasEnoughItems(player, cost)) {
                player.sendSystemMessage(prefix(Component.translatable(
                        "uc7core.claimshop.basecost.notenough",
                        cost.getCount(), cost.getItem().getDescription().getString()
                )));
                return CompoundEventResult.interruptTrue(ClaimResult.customProblem(" "));
            }

            return CompoundEventResult.pass();
        });

        // AFTER_CLAIM: Items abziehen + Feedback. Da BEFORE_CLAIM bereits
        // verhindert, dass Spieler ohne Items claimen, muss hier nicht mehr
        // geunclaimed werden.
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

            ResourceKey<Level> chunkDimKey = chunk.getPos().dimension();
            ClaimShopSavedData savedData = ClaimShopSavedData.get(player.getServer().overworld());
            ResourceLocation chunkDim = chunkDimKey.location();

            if (!savedData.getData().hasBaseCost(chunkDim)) {
                LOGGER.info("BaseCost: No base cost set, skipping.");
                return;
            }

            // Server-Team Claims Ã¼berspringen
            Team claimingTeam = chunk.getTeamData().getTeam();
            if (claimingTeam.isServerTeam()) {
                LOGGER.info("BaseCost: Chunk claimed by server team '{}', skipping.", claimingTeam.getName().getString());
                return;
            }

            if (consumeShopClaim(chunk.getPos().chunkPos())) {
                LOGGER.info("BaseCost: Chunk purchased through shop, skipping base cost.");
                return;
            }

            ItemStack cost = savedData.getData().getBaseCost(chunkDim);
            LOGGER.info("BaseCost: Required: {}x {}", cost.getCount(), cost.getItem().getDescriptionId());

            // Accumulator fÃ¼r diesen Spieler holen oder erstellen
            ClaimAccumulator acc = pendingMessages.computeIfAbsent(
                    player.getUUID(), k -> new ClaimAccumulator(player, cost)
            );

            // Entferne Items (sollte immer genug da sein, da BEFORE_CLAIM prÃ¼ft)
            if (hasEnoughItems(player, cost)) {
                removeItems(player, cost);
                acc.paidChunks++;
                LOGGER.info("BaseCost: Removed {}x {} from {}", cost.getCount(), cost.getItem().getDescriptionId(), player.getGameProfile().getName());
            } else {
                LOGGER.warn("BaseCost: Player {} unexpectedly lacks items for base cost (claim still allowed)", player.getGameProfile().getName());
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
                            player.sendSystemMessage(prefix(Component.translatable(
                                    "uc7core.claimshop.basecost.paid",
                                    finalAcc.cost.getCount(),
                                    finalAcc.cost.getItem().getDescription().getString()
                            )));
                        } else {
                            player.sendSystemMessage(prefix(Component.translatable(
                                    "uc7core.claimshop.basecost.paid.bulk",
                                    finalAcc.paidChunks,
                                    totalCost,
                                    finalAcc.cost.getItem().getDescription().getString()
                            )));
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
            player.sendSystemMessage(prefix(Component.translatable(
                    "uc7core.claimshop.income.pending",
                    totalItems
            )));

            savedData.getData().clearPendingIncome(player.getUUID());
            savedData.setDirty();

            LOGGER.info("ClaimShop: Gave {} pending income items to {}", pending.size(), player.getGameProfile().getName());
        }

        if (ServerConfig.WELCOME_ENABLED.get()) {
            var data = player.getPersistentData();
            String tag = "buyingchunks:joined_before";
            if (!data.getBoolean(tag)) {
                data.putBoolean(tag, true);
                String msg = ServerConfig.WELCOME_MESSAGE.get();
                for (String line : msg.split("\n")) {
                    player.sendSystemMessage(Component.literal(line.replace("\r", "")));
                }
                player.level().playSound(null, player.blockPosition(),
                        SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.3f, 1.0f);
            }
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
}
