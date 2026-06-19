package snoopypupser.buyingchunks.client;

import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import snoopypupser.buyingchunks.network.SyncAdminDataPacket;

import java.util.*;

@OnlyIn(Dist.CLIENT)
public class ClientAdminData {

    private static Map<UUID, ItemStack> teamPrices = new HashMap<>();
    private static Map<UUID, Integer> teamChunkLimits = new HashMap<>();
    private static boolean playerSellEnabled = false;
    private static Set<UUID> playerIncomeDisabled = new HashSet<>();
    private static Set<UUID> autoReclaimTeams = new HashSet<>();

    private static boolean hasData = false;

    public static void update(SyncAdminDataPacket packet) {
        teamPrices = new HashMap<>(packet.teamPrices());
        teamChunkLimits = new HashMap<>(packet.teamChunkLimits());
        playerSellEnabled = packet.playerSellEnabled();
        playerIncomeDisabled = new HashSet<>(packet.playerIncomeDisabled());
        autoReclaimTeams = new HashSet<>(packet.autoReclaimTeams());
        hasData = true;
    }

    public static boolean hasData() {
        return hasData;
    }

    public static Map<UUID, ItemStack> getTeamPrices() {
        return Collections.unmodifiableMap(teamPrices);
    }

    public static Map<UUID, Integer> getTeamChunkLimits() {
        return Collections.unmodifiableMap(teamChunkLimits);
    }

    public static boolean isPlayerSellEnabled() {
        return playerSellEnabled;
    }

    public static Set<UUID> getPlayerIncomeDisabled() {
        return Collections.unmodifiableSet(playerIncomeDisabled);
    }

    public static boolean isPlayerIncomeEnabled(UUID teamId) {
        return !playerIncomeDisabled.contains(teamId);
    }

    public static Set<UUID> getAutoReclaimTeams() {
        return Collections.unmodifiableSet(autoReclaimTeams);
    }

    public static boolean isAutoReclaimEnabled(UUID teamId) {
        return autoReclaimTeams.contains(teamId);
    }
}
