package snoopypupser.buyingchunks.client;

import dev.ftb.mods.ftbchunks.client.gui.ChunkScreen;
import dev.ftb.mods.ftblibrary.config.ItemStackConfig;
import dev.ftb.mods.ftblibrary.ui.*;
import dev.ftb.mods.ftblibrary.ui.input.Key;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import snoopypupser.buyingchunks.BuyingChunks;
import snoopypupser.buyingchunks.network.AdminActionPacket;

import java.util.*;
import java.util.stream.Collectors;

public class AdminTeamSettingsScreen extends BaseAdminScreen {

    public enum Mode { TEAM_PRICE, BUY_LIMIT, PLAYER_INCOME, AUTO_RECLAIM }

    private static final int SMALL_BTN_W = 30, CLM_BTN_W = 20, ADD_BTN_W = 60, MAX_VIS = 5, PICKER_W = 18;

    private final BaseScreen previous;
    private final Mode mode;

    private TextBox teamField, itemField, amountField;
    private int listTop, listBottom;
    private int teamLabelY, teamFieldY, itemLabelY, itemFieldY, amtLabelY, amtFieldY, addX, addY;
    private int pickerX, pickerY;
    private String pendingItemId = "";
    private String pendingAmount = "1";
    private UUID pendingTeamId = null;
    private String pendingTeamName = "";

    private int scroll = 0;
    private final Map<UUID, String> teamCache = new HashMap<>();
    private UUID selectedTeamId = null;
    private String selectedTeamName = "";
    private List<UUID> searchResults = new ArrayList<>();

    private boolean showDropdown = false;

    private boolean needsAmount() {
        return mode == Mode.TEAM_PRICE || mode == Mode.BUY_LIMIT;
    }
    private boolean needsItem() {
        return mode == Mode.TEAM_PRICE;
    }

    public AdminTeamSettingsScreen(BaseScreen previous, Mode mode) {
        this.previous = previous;
        this.mode = mode;
    }

    @Override
    public boolean onInit() {
        setFullscreen();
        buildCache();
        Minecraft mc = Minecraft.getInstance();
        int lh = mc.font.lineHeight;
        int cw = Math.max(mc.font.width(getTitleStr()), 180);
        cw = Math.max(cw, ADD_BTN_W);
        bw = cw + PAD * 2 + 10;

        int listRows = Math.min(getTeams().size(), MAX_VIS);
        int listH = Math.max(listRows * ROW_H, ROW_H);
        int itemExtra = needsItem() ? (lh + GAP_LABEL_FIELD + FIELD_H + GAP_FIELD_NEXT) : 0;
        int amtExtra = needsAmount() ? (lh + GAP_LABEL_FIELD + FIELD_H + GAP_FIELD_NEXT) : 0;
        bh = PAD + BTN_H + GAP_BACK_TITLE + lh + GAP_TITLE_DIVIDER + 1 + GAP_DIVIDER_CONTENT
                + listH + 4 + lh + GAP_LABEL_FIELD + FIELD_H + GAP_FIELD_NEXT
                + itemExtra + amtExtra + GAP_CONTENT_BTN + BTN_H + PAD;
        computeTopSection(lh);

        int y = contentY;
        listTop = y; listBottom = listTop + listRows * ROW_H;
        y = listBottom + 16; teamLabelY = y;
        y += lh + GAP_LABEL_FIELD; teamFieldY = y;
        y += FIELD_H + GAP_FIELD_NEXT;
        if (needsItem()) { itemLabelY = y; y += lh + GAP_LABEL_FIELD; itemFieldY = y; y += FIELD_H + GAP_FIELD_NEXT; }
        if (needsAmount()) { amtLabelY = y; y += lh + GAP_LABEL_FIELD; amtFieldY = y; y += FIELD_H + GAP_CONTENT_BTN; }
        else { y += GAP_CONTENT_BTN; }
        addX = bx + bw / 2 - ADD_BTN_W / 2; addY = y;
        return true;
    }

    @Override
    public void addWidgets() {
        int x = bx + PAD + 2, fw = bw - PAD * 2 - 4;

        teamField = new TextBox(this) {
            @Override public void onTextChanged() { onTeamSearch(getText()); }
            @Override
            public boolean mousePressed(MouseButton button) {
                if (button.isLeft()) {
                    showDropdown = true;
                    onTeamSearch(getText());
                }
                return super.mousePressed(button);
            }
            @Override
            public boolean keyPressed(Key key) {
                if (key.keyCode == 258 && !searchResults.isEmpty()) {
                    doAutocomplete();
                    return true;
                }
                return super.keyPressed(key);
            }
        };
        teamField.setPosAndSize(x, teamFieldY, fw, FIELD_H);
        teamField.setMaxLength(64);
        teamField.textColor = COL_TEXT;
        add(teamField);

        if (needsItem()) {
            int itemW = fw - PICKER_W - 2;
            itemField = new TextBox(this);
            itemField.setPosAndSize(x, itemFieldY, itemW, FIELD_H);
            itemField.setMaxLength(64);
            itemField.textColor = COL_TEXT;
            itemField.setText(pendingItemId);
            add(itemField);
            pickerX = x + itemW + 2; pickerY = itemFieldY;
        }

        if (needsAmount()) {
            amountField = new TextBox(this);
            amountField.setPosAndSize(x, amtFieldY, needsItem() ? fw / 2 : fw / 3, FIELD_H);
            amountField.setMaxLength(4);
            amountField.setText(pendingAmount);
            amountField.setFilter(s -> s.matches("\\d*"));
            amountField.textColor = COL_TEXT;
            add(amountField);
        }
        restorePendingTeam();
    }

    @Override public void alignWidgets() {}

    private void restorePendingTeam() {
        BuyingChunks.LOGGER.info("restorePendingTeam: pendingTeamId={}, pendingTeamName='{}'", pendingTeamId, pendingTeamName);
        if (pendingTeamId != null) {
            teamField.setText(pendingTeamName);
            selectedTeamId = pendingTeamId;
            selectedTeamName = pendingTeamName;
            searchResults = List.of();
            showDropdown = false;
            BuyingChunks.LOGGER.info("restorePendingTeam: done, selectedTeamId={}, teamField.text='{}'", selectedTeamId, teamField.getText());
        }
    }

    @Override
    public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        int mx = (int) getMouseX(), my = (int) getMouseY();
        g.fill(0, 0, getWidth(), getHeight(), 0x88000000);
        drawPanel(g, bx, by, bw, bh);
        drawTopSection(g, theme);

        int ix = bx + PAD + 2, lh = theme.getFont().lineHeight;

        List<UUID> teams = getTeams();
        int vis = Math.min(teams.size(), MAX_VIS);
        if (teams.isEmpty()) {
            String empty = Component.translatable("uc7core.claimshop.admin.no_teams_configured").getString();
            theme.drawString(g, empty, bx + (bw - theme.getFont().width(empty)) / 2, listTop + 2, COL_HINT, 0);
        } else {
            for (int i = scroll; i < teams.size() && i < scroll + vis; i++) {
                UUID id = teams.get(i);
                int ry = listTop + (i - scroll) * ROW_H;
                if (hit(mx, my, bx + PAD, ry, bw - PAD * 2, ROW_H))
                    g.fill(bx + PAD, ry, bx + bw - PAD, ry + ROW_H, C_HOVER);
                String name = teamName(id);
                String val = getVal(id);
                int rx_remove = bx + bw - PAD - SMALL_BTN_W - 2;
                int rx_claim = rx_remove - CLM_BTN_W - 2;
                int textLeft = bx + PAD + 6;
                int maxTextW = rx_claim - 4 - textLeft;
                var f = theme.getFont();
                int nameW = f.width(name);
                int valW = f.width(val);
                int totalW = nameW + 8 + valW;
                if (totalW > maxTextW) {
                    int ellipsisW = f.width("...");
                    int maxValW = maxTextW - nameW - 8 - ellipsisW;
                    if (maxValW <= 0) {
                        name = f.plainSubstrByWidth(name, maxTextW - ellipsisW) + "...";
                        val = "";
                    } else {
                        val = f.plainSubstrByWidth(val, maxValW) + "...";
                    }
                }
                theme.drawString(g, name + "  " + val, textLeft, ry + (ROW_H - f.lineHeight) / 2 + 1, COL_TEXT, 0);
                drawBtn(g, theme, rx_claim, ry + 1, CLM_BTN_W, BTN_H, 0xFF1a3d6a, 0xFF4a6d9a, COL_TEXT, "\u2302");
                drawBtn(g, theme, rx_remove, ry + 1, SMALL_BTN_W, BTN_H, 0xFF3d1616, 0xFFE53935, COL_RED_T, "\u2715");
            }
        }

        theme.drawString(g, Component.translatable("uc7core.claimshop.admin.teamname").getString(), ix, teamLabelY, COL_TEXT, 0);
        if (needsItem() && itemField != null) {
            theme.drawString(g, Component.translatable("uc7core.claimshop.admin.item").getString(), ix, itemLabelY, COL_TEXT, 0);
            boolean overPick = hit(mx, my, pickerX, pickerY, PICKER_W, FIELD_H);
            int pickBg = overPick ? 0xFF3a5a6a : 0xFF2a3d4a;
            drawBtn(g, theme, pickerX, pickerY, PICKER_W, FIELD_H, pickBg, pickBg, COL_TEXT, "\uD83D\uDD0D");
        }
        if (needsAmount())
            theme.drawString(g, Component.translatable(
                    mode == Mode.BUY_LIMIT ? "uc7core.claimshop.admin.limit" : "uc7core.claimshop.admin.amount").getString(),
                    ix, amtLabelY, COL_TEXT, 0);

        String addLabel = (mode == Mode.PLAYER_INCOME || mode == Mode.AUTO_RECLAIM)
                ? Component.translatable("uc7core.claimshop.admin.toggle").getString()
                : (selectedTeamId != null && teams.contains(selectedTeamId)
                ? Component.translatable("uc7core.claimshop.admin.update").getString()
                : Component.translatable("uc7core.claimshop.admin.add").getString());
        drawBtn(g, theme, addX, addY, ADD_BTN_W, BTN_H, 0xFF1a3d16, 0xFF4CAF50, COL_GREEN_T, addLabel);
    }

    @Override
    public void drawForeground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        int mx = (int) getMouseX(), my = (int) getMouseY();

        if (hit(backX, backY, BACK_W, BTN_H))
            drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.back.tip"), mx, my);

        List<UUID> teams = getTeams();
        int vis = Math.min(teams.size(), MAX_VIS);
        for (int i = scroll; i < teams.size() && i < scroll + vis; i++) {
            int ry = listTop + (i - scroll) * ROW_H;
            int rx_remove = bx + bw - PAD - SMALL_BTN_W - 2;
            int rx_claim = rx_remove - CLM_BTN_W - 2;
            if (hit(rx_claim, ry + 1, CLM_BTN_W, BTN_H))
                drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.open_chunkmap.tip"), mx, my);
            if (hit(rx_remove, ry + 1, SMALL_BTN_W, BTN_H))
                drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.remove_entry.tip"), mx, my);
        }

        if (teamField != null && hit(teamField.getX(), teamFieldY, teamField.getWidth(), FIELD_H))
            drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.team_field.tip"), mx, my);
        if (needsItem()) {
            if (itemField != null && hit(itemField.getX(), itemFieldY, itemField.getWidth(), FIELD_H))
                drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.item_field.tip"), mx, my);
            if (hit(pickerX, pickerY, PICKER_W, FIELD_H))
                drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.picker.tip"), mx, my);
        }
        if (needsAmount() && amountField != null && hit(amountField.getX(), amtFieldY, amountField.getWidth(), FIELD_H))
            drawTooltip(g, theme, Component.translatable(
                    mode == Mode.BUY_LIMIT ? "uc7core.claimshop.admin.limit_field.tip" : "uc7core.claimshop.admin.amount_field.tip"
            ), mx, my);
        if (hit(addX, addY, ADD_BTN_W, BTN_H))
            drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.add.tip"), mx, my);

        if (showDropdown && !searchResults.isEmpty()) {
            int dy = teamFieldY + FIELD_H + 1, lh = theme.getFont().lineHeight;
            for (int i = 0; i < searchResults.size(); i++) {
                UUID id = searchResults.get(i);
                String n = teamCache.getOrDefault(id, "?");
                int sy = dy + i * (lh + 2);
                g.fill(teamField.getX(), sy, teamField.getX() + teamField.getWidth(), sy + lh + 2, 0xFF252a36);
                if (hit(mx, my, teamField.getX(), sy, teamField.getWidth(), lh + 2))
                    g.fill(teamField.getX(), sy, teamField.getX() + teamField.getWidth(), sy + lh + 2, 0xFF2a3d4a);
                theme.drawString(g, n, teamField.getX() + 1, sy + 1, COL_TEXT, 0);
            }
        }
    }

    @Override
    public boolean mousePressed(MouseButton button) {
        if (button.isLeft()) {
            // Handle dropdown first — close on click outside
            if (showDropdown) {
                boolean hitItem = false;
                if (!searchResults.isEmpty()) {
                    int dy = teamFieldY + FIELD_H + 1, lh = Minecraft.getInstance().font.lineHeight;
                    for (int i = 0; i < searchResults.size(); i++) {
                        UUID id = searchResults.get(i);
                        int sy = dy + i * (lh + 2);
                        if (hit(teamField.getX(), sy, teamField.getWidth(), lh + 2)) {
                            selectedTeamName = teamCache.get(id);
                            teamField.setText(selectedTeamName);
                            selectedTeamId = id;
                            hitItem = true;
                            break;
                        }
                    }
                }
                if (hitItem) {
                    searchResults = List.of();
                    showDropdown = false;
                    return true;
                }
                if (!hit(teamField.getX(), teamFieldY, teamField.getWidth(), FIELD_H)) {
                    showDropdown = false;
                    searchResults = List.of();
                }
            }

            if (hit(backX, backY, BACK_W, BTN_H)) { previous.openGui(); return true; }

            List<UUID> teams = getTeams();
            int vis = Math.min(teams.size(), MAX_VIS);
            for (int i = scroll; i < teams.size() && i < scroll + vis; i++) {
                int ry = listTop + (i - scroll) * ROW_H;
                int rx_remove = bx + bw - PAD - SMALL_BTN_W - 2;
                int rx_claim = rx_remove - CLM_BTN_W - 2;
                if (hit(rx_claim, ry + 1, CLM_BTN_W, BTN_H)) {
                    UUID clickedId = teams.get(i);
                    try {
                        FTBTeamsAPI.api().getClientManager().getTeams().stream()
                                .filter(t -> t.getId().equals(clickedId))
                                .findFirst().ifPresent(ChunkScreen::openChunkScreen);
                    } catch (Exception e) {
                        BuyingChunks.LOGGER.warn("AdminTeamSettingsScreen: failed to open chunk screen", e);
                    }
                    return true;
                }
                if (hit(rx_remove, ry + 1, SMALL_BTN_W, BTN_H)) {
                    doRemove(teams.get(i)); return true;
                }
            }

            if (needsItem() && hit(pickerX, pickerY, PICKER_W, FIELD_H)) { openItemPicker(); return true; }
            if (hit(addX, addY, ADD_BTN_W, BTN_H)) { doAdd(); return true; }
        }
        return super.mousePressed(button);
    }

    @Override
    public boolean mouseScrolled(double amount) {
        List<UUID> teams = getTeams();
        int maxOff = Math.max(0, teams.size() - MAX_VIS);
        scroll = (int) Math.max(0, Math.min(maxOff, scroll - amount));
        return true;
    }

    @Override
    protected String getTitleStr() {
        return switch (mode) {
            case TEAM_PRICE -> Component.translatable("uc7core.claimshop.admin.teamprices").getString();
            case BUY_LIMIT -> Component.translatable("uc7core.claimshop.admin.buylimits").getString();
            case PLAYER_INCOME -> Component.translatable("uc7core.claimshop.admin.playerincome").getString();
            case AUTO_RECLAIM -> Component.translatable("uc7core.claimshop.admin.autoreclaim").getString();
        };
    }

    private void buildCache() {
        teamCache.clear();
        try { var teams = FTBTeamsAPI.api().getClientManager().getTeams(); for (Team t : teams) teamCache.put(t.getId(), t.getName().getString()); }
        catch (Exception e) { BuyingChunks.LOGGER.warn("AdminTeamSettingsScreen: failed to build team cache", e); }
    }

    private List<UUID> getTeams() {
        return switch (mode) {
            case TEAM_PRICE -> new ArrayList<>(ClientAdminData.getTeamPrices().keySet());
            case BUY_LIMIT -> new ArrayList<>(ClientAdminData.getTeamChunkLimits().keySet());
            case PLAYER_INCOME -> new ArrayList<>(ClientAdminData.getPlayerIncomeDisabled());
            case AUTO_RECLAIM -> new ArrayList<>(ClientAdminData.getAutoReclaimTeams());
        };
    }

    private String teamName(UUID id) { return teamCache.getOrDefault(id, id.toString().substring(0, 8) + "..."); }

    private String getVal(UUID id) {
        return switch (mode) {
            case TEAM_PRICE -> {
                ItemStack p = ClientAdminData.getTeamPrices().get(id);
                if (p == null || p.isEmpty()) yield "";
                yield p.getCount() + "x " + p.getItem().getDescription().getString();
            }
            case BUY_LIMIT -> {
                Integer lim = ClientAdminData.getTeamChunkLimits().get(id);
                yield lim != null ? lim.toString() : "";
            }
            case PLAYER_INCOME -> ClientAdminData.isPlayerIncomeEnabled(id)
                    ? Component.translatable("uc7core.claimshop.admin.enabled_status").getString()
                    : Component.translatable("uc7core.claimshop.admin.disabled_status").getString();
            case AUTO_RECLAIM -> ClientAdminData.isAutoReclaimEnabled(id)
                    ? Component.translatable("uc7core.claimshop.admin.enabled_status").getString()
                    : Component.translatable("uc7core.claimshop.admin.disabled_status").getString();
        };
    }

    private void onTeamSearch(String text) {
        if (text.isEmpty() && !showDropdown) { searchResults = List.of(); selectedTeamId = null; return; }
        String lower = text.toLowerCase(Locale.ROOT);
        searchResults = teamCache.entrySet().stream()
                .filter(e -> e.getValue().toLowerCase(Locale.ROOT).contains(lower))
                .map(Map.Entry::getKey).limit(8).collect(Collectors.toList());
        if (searchResults.size() == 1) { selectedTeamId = searchResults.get(0); selectedTeamName = teamCache.get(selectedTeamId); }
        else selectedTeamId = null;
    }

    private void doAutocomplete() {
        if (searchResults.isEmpty()) return;
        UUID id = searchResults.get(0);
        selectedTeamName = teamCache.get(id);
        teamField.setText(selectedTeamName);
        selectedTeamId = id;
        searchResults = List.of();
        showDropdown = false;
    }

    private void doRemove(UUID id) {
        if (id == null) return;
        switch (mode) {
            case TEAM_PRICE -> PacketDistributor.sendToServer(new AdminActionPacket(AdminActionPacket.ACTION_REMOVE_TEAM_PRICE, id, "", 0, false));
            case BUY_LIMIT -> PacketDistributor.sendToServer(new AdminActionPacket(AdminActionPacket.ACTION_REMOVE_CHUNK_LIMIT, id, "", 0, false));
            case PLAYER_INCOME -> PacketDistributor.sendToServer(new AdminActionPacket(AdminActionPacket.ACTION_SET_PLAYER_INCOME, id, "", 0, !ClientAdminData.isPlayerIncomeEnabled(id)));
            case AUTO_RECLAIM -> PacketDistributor.sendToServer(new AdminActionPacket(AdminActionPacket.ACTION_SET_AUTO_RECLAIM, id, "", 0, !ClientAdminData.isAutoReclaimEnabled(id)));
        }
        previous.openGui();
    }

    private void openItemPicker() {
        ItemStackConfig cfg = new ItemStackConfig(true, true);
        pendingItemId = itemField.getText().trim();
        pendingAmount = amountField.getText().trim();
        pendingTeamId = selectedTeamId;
        pendingTeamName = selectedTeamName;
        BuyingChunks.LOGGER.info("openItemPicker: selectedTeamId={}, pendingItemId={}, pendingAmount={}", selectedTeamId, pendingItemId, pendingAmount);
        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(pendingItemId));
            if (!pendingItemId.isEmpty() && item != Items.AIR) {
                int cnt = 1;
                try { cnt = Math.max(1, Math.min(64, Integer.parseInt(pendingAmount))); } catch (Exception ignored) {}
                cfg.setValue(new ItemStack(item, cnt));
            }
        } catch (Exception ignored) {}
        cfg.onClicked(this, MouseButton.LEFT, saved -> {
            BuyingChunks.LOGGER.info("callback: saved={}", saved);
            if (saved) {
                ItemStack stack = cfg.getValue();
                if (stack != null && !stack.isEmpty()) {
                    pendingItemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    pendingAmount = String.valueOf(stack.getCount());
                    BuyingChunks.LOGGER.info("callback: updated pendingItemId={}, pendingAmount={}", pendingItemId, pendingAmount);
                }
            }
            Minecraft.getInstance().tell(this::openGui);
        });
    }

    private void doAdd() {
        BuyingChunks.LOGGER.info("doAdd: selectedTeamId={}", selectedTeamId);
        if (selectedTeamId == null) {
            Objects.requireNonNull(Minecraft.getInstance().player).sendSystemMessage(
                    BuyingChunks.prefix(Component.translatable("uc7core.claimshop.admin.need_select_team")));
            return;
        }
        int amount = 1;
        if (needsAmount()) {
            try { amount = Integer.parseInt(amountField.getText().trim()); if (amount < 1) amount = 1; if (amount > 9999) amount = 9999; }
            catch (NumberFormatException e) { amount = 1; }
        }
        switch (mode) {
            case TEAM_PRICE -> {
                String itemId = itemField != null ? itemField.getText().trim() : "";
                if (itemId.isEmpty()) return;
                PacketDistributor.sendToServer(new AdminActionPacket(AdminActionPacket.ACTION_SET_TEAM_PRICE, selectedTeamId, itemId, amount, false));
            }
            case BUY_LIMIT -> PacketDistributor.sendToServer(new AdminActionPacket(AdminActionPacket.ACTION_SET_CHUNK_LIMIT, selectedTeamId, "", amount, false));
            case PLAYER_INCOME -> {
                boolean cur = ClientAdminData.isPlayerIncomeEnabled(selectedTeamId);
                PacketDistributor.sendToServer(new AdminActionPacket(AdminActionPacket.ACTION_SET_PLAYER_INCOME, selectedTeamId, "", 0, !cur));
            }
            case AUTO_RECLAIM -> {
                boolean cur = ClientAdminData.isAutoReclaimEnabled(selectedTeamId);
                PacketDistributor.sendToServer(new AdminActionPacket(AdminActionPacket.ACTION_SET_AUTO_RECLAIM, selectedTeamId, "", 0, !cur));
            }
        }
        previous.openGui();
    }
}
