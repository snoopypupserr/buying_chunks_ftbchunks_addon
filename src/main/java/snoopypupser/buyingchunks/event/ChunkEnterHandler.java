package snoopypupser.buyingchunks.event;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkEnterHandler {

    private static final Map<UUID, String> lastStates = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ChunkPos current = new ChunkPos(player.blockPosition());
        ClaimedChunkManager manager = FTBChunksAPI.api().getManager();
        ClaimedChunk claimed = manager.getChunk(new ChunkDimPos(player.serverLevel().dimension(), current));

        String state = (claimed == null) ? "wilderness" : "team:" + claimed.getTeamData().getTeam().getId();
        String prev = lastStates.get(player.getUUID());

        if (state.equals(prev)) return;

        lastStates.put(player.getUUID(), state);

        Component message;
        if (claimed == null) {
            message = Component.literal("~ Wilderness ~")
                    .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC);
        } else {
            Team team = claimed.getTeamData().getTeam();
            message = Component.literal("~ ")
                    .append(team.getColoredName())
                    .append(Component.literal(" ~"));
        }

        player.displayClientMessage(message, true);
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        lastStates.remove(event.getEntity().getUUID());
    }
}
