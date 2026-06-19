package snoopypupser.buyingchunks.claimshop;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;

import java.util.*;

public class ClaimShopData {

    private final Map<ChunkPos, ClaimShopEntry> forSaleChunks = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> teamPrices = new HashMap<>();
    private final Map<UUID, Integer> teamChunkLimits = new HashMap<>();
    private final Map<UUID, Map<UUID, Integer>> teamBoughtCounts = new HashMap<>();

    private boolean playerSellEnabled = false;
    private final Set<UUID> playerIncomeDisabled = new HashSet<>();
    private final Map<UUID, List<ItemStack>> pendingIncome = new HashMap<>();

    private ItemStack baseCost = ItemStack.EMPTY;

    private final Set<UUID> autoReclaimTeams = new HashSet<>();
    private final Map<ChunkPos, UUID> chunkOriginalTeam = new HashMap<>();

    // --- Auto-Reclaim ---

    public void setAutoReclaim(UUID teamId, boolean enabled) {
        if (enabled) {
            autoReclaimTeams.add(teamId);
        } else {
            autoReclaimTeams.remove(teamId);
        }
    }

    public boolean isAutoReclaimEnabled(UUID teamId) {
        return autoReclaimTeams.contains(teamId);
    }

    public void setChunkOriginalTeam(ChunkPos pos, UUID teamId) {
        chunkOriginalTeam.put(pos, teamId);
    }

    public UUID getChunkOriginalTeam(ChunkPos pos) {
        return chunkOriginalTeam.get(pos);
    }

    public void removeChunkOriginalTeam(ChunkPos pos) {
        chunkOriginalTeam.remove(pos);
    }

    public boolean hasChunkOriginalTeam(ChunkPos pos) {
        return chunkOriginalTeam.containsKey(pos);
    }

    // --- Base Cost ---

    public boolean hasBaseCost() {
        return !baseCost.isEmpty();
    }

    public ItemStack getBaseCost() {
        return baseCost.copy();
    }

    public void setBaseCost(Item item, int amount) {
        this.baseCost = new ItemStack(item, amount);
    }

    public void removeBaseCost() {
        this.baseCost = ItemStack.EMPTY;
    }

    // --- Player Sell Setting ---

    public boolean isPlayerSellEnabled() {
        return playerSellEnabled;
    }

    public void setPlayerSellEnabled(boolean enabled) {
        this.playerSellEnabled = enabled;
    }

    // --- Player Income per Team ---

    public boolean isPlayerIncomeEnabled(UUID teamId) {
        return !playerIncomeDisabled.contains(teamId);
    }

    public void setPlayerIncomeEnabled(UUID teamId, boolean enabled) {
        if (enabled) {
            playerIncomeDisabled.remove(teamId);
        } else {
            playerIncomeDisabled.add(teamId);
        }
    }

    // --- Pending Income ---

    public void addPendingIncome(UUID sellerUUID, ItemStack stack) {
        pendingIncome.computeIfAbsent(sellerUUID, k -> new ArrayList<>()).add(stack.copy());
    }

    public List<ItemStack> getPendingIncome(UUID sellerUUID) {
        return pendingIncome.getOrDefault(sellerUUID, Collections.emptyList());
    }

    public void clearPendingIncome(UUID sellerUUID) {
        pendingIncome.remove(sellerUUID);
    }

    public boolean hasPendingIncome(UUID sellerUUID) {
        List<ItemStack> list = pendingIncome.get(sellerUUID);
        return list != null && !list.isEmpty();
    }

    // --- Chunk Shop ---

    public void setForSale(ChunkPos pos, ItemStack price, String shopTeamName, UUID sellerUUID) {
        forSaleChunks.put(pos, new ClaimShopEntry(price.copy(), shopTeamName, sellerUUID));
    }

    public void removeFromSale(ChunkPos pos) {
        forSaleChunks.remove(pos);
    }

    public boolean isForSale(ChunkPos pos) {
        return forSaleChunks.containsKey(pos);
    }

    public ClaimShopEntry getEntry(ChunkPos pos) {
        return forSaleChunks.get(pos);
    }

    public Map<ChunkPos, ClaimShopEntry> getAllForSaleMap() {
        return Collections.unmodifiableMap(forSaleChunks);
    }

    // --- Team Prices ---

    public void setTeamPrice(UUID teamId, ItemStack price) {
        teamPrices.put(teamId, price.copy());
    }

    public void removeTeamPrice(UUID teamId) {
        teamPrices.remove(teamId);
    }

    public boolean hasTeamPrice(UUID teamId) {
        return teamPrices.containsKey(teamId);
    }

    public ItemStack getTeamPrice(UUID teamId) {
        return teamPrices.getOrDefault(teamId, ItemStack.EMPTY);
    }

    public Map<UUID, ItemStack> getAllTeamPrices() {
        return Collections.unmodifiableMap(teamPrices);
    }

    public Map<UUID, Integer> getAllTeamChunkLimits() {
        return Collections.unmodifiableMap(teamChunkLimits);
    }

    public Set<UUID> getPlayerIncomeDisabledSet() {
        return Collections.unmodifiableSet(playerIncomeDisabled);
    }

    public Set<UUID> getAutoReclaimTeamsSet() {
        return Collections.unmodifiableSet(autoReclaimTeams);
    }

    // --- Team Chunk Limits ---

    public void setTeamChunkLimit(UUID shopTeamId, int limit) {
        teamChunkLimits.put(shopTeamId, limit);
    }

    public void removeTeamChunkLimit(UUID shopTeamId) {
        teamChunkLimits.remove(shopTeamId);
    }

    public int getTeamChunkLimit(UUID shopTeamId) {
        return teamChunkLimits.getOrDefault(shopTeamId, -1);
    }

    public boolean hasTeamChunkLimit(UUID shopTeamId) {
        return teamChunkLimits.containsKey(shopTeamId);
    }

    public int getBoughtCount(UUID shopTeamId, UUID buyerTeamId) {
        Map<UUID, Integer> counts = teamBoughtCounts.get(shopTeamId);
        if (counts == null) return 0;
        return counts.getOrDefault(buyerTeamId, 0);
    }

    public void incrementBoughtCount(UUID shopTeamId, UUID buyerTeamId) {
        teamBoughtCounts
                .computeIfAbsent(shopTeamId, k -> new HashMap<>())
                .merge(buyerTeamId, 1, Integer::sum);
    }

    public boolean canBuy(UUID shopTeamId, UUID buyerTeamId) {
        int limit = getTeamChunkLimit(shopTeamId);
        if (limit < 0) return true;
        return getBoughtCount(shopTeamId, buyerTeamId) < limit;
    }

    // --- NBT Save/Load ---

    private static CompoundTag saveItemStack(ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        tag.putInt("count", stack.getCount());
        return tag;
    }

    private static ItemStack loadItemStack(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.parse(tag.getString("id"));
        Item item = BuiltInRegistries.ITEM.get(id);
        int count = tag.getInt("count");
        return new ItemStack(item, count);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        ListTag chunkList = new ListTag();
        for (Map.Entry<ChunkPos, ClaimShopEntry> entry : forSaleChunks.entrySet()) {
            CompoundTag chunkTag = new CompoundTag();
            chunkTag.putInt("x", entry.getKey().x);
            chunkTag.putInt("z", entry.getKey().z);
            chunkTag.put("price", saveItemStack(entry.getValue().getPrice()));
            chunkTag.putString("shopTeamName", entry.getValue().getShopTeamName());
            chunkTag.putUUID("sellerUUID", entry.getValue().getSellerUUID());
            chunkList.add(chunkTag);
        }
        tag.put("chunks", chunkList);

        ListTag teamList = new ListTag();
        for (Map.Entry<UUID, ItemStack> entry : teamPrices.entrySet()) {
            CompoundTag teamTag = new CompoundTag();
            teamTag.putUUID("teamId", entry.getKey());
            teamTag.put("price", saveItemStack(entry.getValue()));
            teamList.add(teamTag);
        }
        tag.put("teamPrices", teamList);

        ListTag limitList = new ListTag();
        for (Map.Entry<UUID, Integer> entry : teamChunkLimits.entrySet()) {
            CompoundTag limitTag = new CompoundTag();
            limitTag.putUUID("teamId", entry.getKey());
            limitTag.putInt("limit", entry.getValue());
            limitList.add(limitTag);
        }
        tag.put("teamChunkLimits", limitList);

        ListTag countList = new ListTag();
        for (Map.Entry<UUID, Map<UUID, Integer>> shopEntry : teamBoughtCounts.entrySet()) {
            for (Map.Entry<UUID, Integer> buyerEntry : shopEntry.getValue().entrySet()) {
                CompoundTag countTag = new CompoundTag();
                countTag.putUUID("shopTeamId", shopEntry.getKey());
                countTag.putUUID("buyerTeamId", buyerEntry.getKey());
                countTag.putInt("count", buyerEntry.getValue());
                countList.add(countTag);
            }
        }
        tag.put("teamBoughtCounts", countList);

        tag.putBoolean("playerSellEnabled", playerSellEnabled);

        ListTag incomeDisabledList = new ListTag();
        for (UUID id : playerIncomeDisabled) {
            CompoundTag t = new CompoundTag();
            t.putUUID("teamId", id);
            incomeDisabledList.add(t);
        }
        tag.put("playerIncomeDisabled", incomeDisabledList);

        ListTag pendingList = new ListTag();
        for (Map.Entry<UUID, List<ItemStack>> entry : pendingIncome.entrySet()) {
            for (ItemStack stack : entry.getValue()) {
                CompoundTag pt = new CompoundTag();
                pt.putUUID("sellerUUID", entry.getKey());
                pt.put("item", saveItemStack(stack));
                pendingList.add(pt);
            }
        }
        tag.put("pendingIncome", pendingList);

        if (!baseCost.isEmpty()) {
            tag.put("baseCost", saveItemStack(baseCost));
        }

        ListTag autoReclaimList = new ListTag();
        for (UUID id : autoReclaimTeams) {
            CompoundTag t = new CompoundTag();
            t.putUUID("teamId", id);
            autoReclaimList.add(t);
        }
        tag.put("autoReclaimTeams", autoReclaimList);

        ListTag originalTeamList = new ListTag();
        for (Map.Entry<ChunkPos, UUID> entry : chunkOriginalTeam.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putInt("x", entry.getKey().x);
            t.putInt("z", entry.getKey().z);
            t.putUUID("teamId", entry.getValue());
            originalTeamList.add(t);
        }
        tag.put("chunkOriginalTeam", originalTeamList);

        return tag;
    }

    public void load(CompoundTag tag) {
        forSaleChunks.clear();
        teamPrices.clear();
        teamChunkLimits.clear();
        teamBoughtCounts.clear();
        playerIncomeDisabled.clear();
        pendingIncome.clear();
        baseCost = ItemStack.EMPTY;
        autoReclaimTeams.clear();
        chunkOriginalTeam.clear();

        ListTag chunkList = tag.getList("chunks", Tag.TAG_COMPOUND);
        for (int i = 0; i < chunkList.size(); i++) {
            CompoundTag chunkTag = chunkList.getCompound(i);
            int x = chunkTag.getInt("x");
            int z = chunkTag.getInt("z");
            ItemStack price = loadItemStack(chunkTag.getCompound("price"));
            String shopTeamName = chunkTag.getString("shopTeamName");
            UUID sellerUUID = chunkTag.getUUID("sellerUUID");
            forSaleChunks.put(new ChunkPos(x, z), new ClaimShopEntry(price, shopTeamName, sellerUUID));
        }

        ListTag teamList = tag.getList("teamPrices", Tag.TAG_COMPOUND);
        for (int i = 0; i < teamList.size(); i++) {
            CompoundTag teamTag = teamList.getCompound(i);
            UUID teamId = teamTag.getUUID("teamId");
            ItemStack price = loadItemStack(teamTag.getCompound("price"));
            teamPrices.put(teamId, price);
        }

        ListTag limitList = tag.getList("teamChunkLimits", Tag.TAG_COMPOUND);
        for (int i = 0; i < limitList.size(); i++) {
            CompoundTag limitTag = limitList.getCompound(i);
            UUID teamId = limitTag.getUUID("teamId");
            int limit = limitTag.getInt("limit");
            teamChunkLimits.put(teamId, limit);
        }

        ListTag countList = tag.getList("teamBoughtCounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < countList.size(); i++) {
            CompoundTag countTag = countList.getCompound(i);
            UUID shopTeamId = countTag.getUUID("shopTeamId");
            UUID buyerTeamId = countTag.getUUID("buyerTeamId");
            int count = countTag.getInt("count");
            teamBoughtCounts.computeIfAbsent(shopTeamId, k -> new HashMap<>()).put(buyerTeamId, count);
        }

        if (tag.contains("playerSellEnabled")) {
            playerSellEnabled = tag.getBoolean("playerSellEnabled");
        }

        ListTag incomeDisabledList = tag.getList("playerIncomeDisabled", Tag.TAG_COMPOUND);
        for (int i = 0; i < incomeDisabledList.size(); i++) {
            playerIncomeDisabled.add(incomeDisabledList.getCompound(i).getUUID("teamId"));
        }

        ListTag pendingList = tag.getList("pendingIncome", Tag.TAG_COMPOUND);
        for (int i = 0; i < pendingList.size(); i++) {
            CompoundTag pt = pendingList.getCompound(i);
            UUID sellerUUID = pt.getUUID("sellerUUID");
            ItemStack stack = loadItemStack(pt.getCompound("item"));
            pendingIncome.computeIfAbsent(sellerUUID, k -> new ArrayList<>()).add(stack);
        }

        if (tag.contains("baseCost")) {
            baseCost = loadItemStack(tag.getCompound("baseCost"));
        }

        ListTag autoReclaimList = tag.getList("autoReclaimTeams", Tag.TAG_COMPOUND);
        for (int i = 0; i < autoReclaimList.size(); i++) {
            autoReclaimTeams.add(autoReclaimList.getCompound(i).getUUID("teamId"));
        }

        ListTag originalTeamList = tag.getList("chunkOriginalTeam", Tag.TAG_COMPOUND);
        for (int i = 0; i < originalTeamList.size(); i++) {
            CompoundTag t = originalTeamList.getCompound(i);
            ChunkPos pos = new ChunkPos(t.getInt("x"), t.getInt("z"));
            UUID teamId = t.getUUID("teamId");
            chunkOriginalTeam.put(pos, teamId);
        }
    }
}