package snoopypupser.buyingchunks.client;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftblibrary.config.ItemStackConfig;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.*;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import snoopypupser.buyingchunks.claimshop.ClientClaimShopData;

public class BulkSellScreen extends BaseAdminScreen {

    private static final int PANEL_W = 340;
    private static final int CHUNK_CELL_H = 16;
    private static final int CHUNK_CELL_W = 44;
    private static final int PICKER_W = 18;
    private static final int BTN_W = 80;

    private final BaseScreen previous;
    private final List<ChunkPos> claimedChunks = new ArrayList<>();
    private final Set<ChunkPos> selectedChunks = new LinkedHashSet<>();
    private final Set<ChunkPos> alreadyForSale = new HashSet<>();

    private int panelX, panelY, panelW, panelH;
    private int titleY, countY;
    private int gridX, gridY, gridW, gridH;
    private int controlsY;
    private int itemLabelY, itemFieldY, amtLabelY, amtFieldY;
    private int pickerX, pickerY;
    private int sellBtnX, sellBtnY;
    private int scrollOffset = 0;

    private TextBox itemField, amountField;
    private String pendingItemId = "";
    private String pendingAmount = "1";

    public BulkSellScreen(BaseScreen previous) {
        this.previous = previous;
    }

    @Override
    public boolean onInit() {
        setFullscreen();
        Minecraft mc = Minecraft.getInstance();
        int lh = mc.font.lineHeight;

        loadClaimedChunks();

        panelW = Math.min(PANEL_W, getWidth() - 40);
        int topH = PAD + lh + 2 + 1 + 6 + lh + 4;
        int gridAreaH = Math.max(4 * CHUNK_CELL_H + 4, 60);
        int controlsH = lh + GAP_LABEL_FIELD + FIELD_H + GAP_FIELD_NEXT
                + lh + GAP_LABEL_FIELD + FIELD_H + GAP_CONTENT_BTN + BTN_H + 4;
        panelH = topH + gridAreaH + 8 + controlsH + PAD;
        panelH = Math.min(panelH, getHeight() - 16);

        panelX = (getWidth() - panelW) / 2;
        panelY = Math.max(PAD, (getHeight() - panelH) / 2);

        titleY = panelY + PAD + 1;
        countY = panelY + PAD + lh + 2 + 1 + 6;
        gridY = countY + lh + 4;
        gridX = panelX + 4;
        gridW = panelW - 8;

        int maxVisibleRows = Math.max(1, (panelH - topH - controlsH - PAD) / CHUNK_CELL_H);
        gridH = maxVisibleRows * CHUNK_CELL_H + 4;

        controlsY = gridY + gridH + 8;

        int contentX = panelX + PAD + 2;
        int fw = panelW - PAD * 2 - 4;
        itemLabelY = controlsY;
        itemFieldY = controlsY + lh + GAP_LABEL_FIELD;
        amtLabelY = itemFieldY + FIELD_H + GAP_FIELD_NEXT;
        amtFieldY = amtLabelY + lh + GAP_LABEL_FIELD;
        int cx = panelX + panelW / 2;
        sellBtnX = cx - BTN_W / 2;
        sellBtnY = amtFieldY + FIELD_H + GAP_CONTENT_BTN;

        return true;
    }

    private void loadClaimedChunks() {
        claimedChunks.clear();
        selectedChunks.clear();
        alreadyForSale.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ResourceLocation dim = mc.level.dimension().location();

        try {
            UUID pid = mc.player.getUUID();
            Team myTeam = null;
            for (Team t : FTBTeamsAPI.api().getClientManager().getTeams()) {
                Collection<UUID> members = t.getMembers();
                if (members != null && members.contains(pid)) {
                    myTeam = t;
                    break;
                }
            }
            if (myTeam == null) return;

            ClaimedChunkManager manager = FTBChunksAPI.api().getManager();
            for (ClaimedChunk chunk : manager.getOrCreateData(myTeam).getClaimedChunks()) {
                ChunkPos pos = chunk.getPos().chunkPos();
                claimedChunks.add(pos);
                if (ClientClaimShopData.isForSale(dim, pos)) {
                    alreadyForSale.add(pos);
                }
            }
        } catch (Exception e) {
            snoopypupser.buyingchunks.BuyingChunks.LOGGER.warn("BulkSell: Failed to load claimed chunks", e);
        }

        claimedChunks.sort(Comparator.comparingInt((ChunkPos p) -> p.x).thenComparingInt(p -> p.z));
    }

    @Override
    public void addWidgets() {
        int contentX = panelX + PAD + 2;
        int fw = panelW - PAD * 2 - 4;
        int itemW = fw - PICKER_W - 2;

        itemField = new TextBox(this);
        itemField.setPosAndSize(contentX, itemFieldY, itemW, FIELD_H);
        itemField.setMaxLength(64);
        itemField.textColor = COL_TEXT;
        itemField.setText(pendingItemId);
        add(itemField);

        pickerX = contentX + itemW + 2;
        pickerY = itemFieldY;

        amountField = new TextBox(this);
        amountField.setPosAndSize(contentX, amtFieldY, fw / 3, FIELD_H);
        amountField.setMaxLength(3);
        amountField.setText(pendingAmount);
        amountField.setFilter(s -> s.matches("\\d*"));
        amountField.textColor = COL_TEXT;
        add(amountField);
    }

    @Override
    public void alignWidgets() {}

    @Override
    public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        g.fill(0, 0, getWidth(), getHeight(), 0x88000000);
        drawPanel(g, panelX, panelY, panelW, panelH);
        drawTopSection(g, theme);

        Minecraft mc = Minecraft.getInstance();
        int lh = mc.font.lineHeight;
        int contentX = panelX + PAD + 2;

        int selectedCount = selectedChunks.size();
        int totalCount = claimedChunks.size();
        int forSaleCount = alreadyForSale.size();
        String countStr = Component.translatable("uc7core.claimshop.bulksell.selected_count",
                selectedCount, totalCount).getString();
        if (forSaleCount > 0) {
            countStr += "  |  " + Component.translatable("uc7core.claimshop.bulksell.already_listed", forSaleCount).getString();
        }
        theme.drawString(g, countStr, contentX, countY, COL_TEXT, 0);

        g.fill(gridX, gridY - 1, gridX + gridW, gridY, C_BORDER);
        g.fill(gridX, gridY, gridX + gridW, gridY + gridH, 0xFF2e3440);

        drawChunkGrid(g, theme, mc, lh);

        theme.drawString(g, Component.translatable("uc7core.claimshop.admin.item").getString(),
                contentX, itemLabelY, COL_TEXT, 0);
        theme.drawString(g, Component.translatable("uc7core.claimshop.admin.amount").getString(),
                contentX, amtLabelY, COL_TEXT, 0);

        drawBtn(g, theme, sellBtnX, sellBtnY, BTN_W, BTN_H,
                selectedCount > 0 ? 0xFF1a3d16 : 0xFF2a2a2a,
                selectedCount > 0 ? 0xFF4CAF50 : 0xFF555555,
                selectedCount > 0 ? COL_GREEN_T : COL_HINT,
                Component.translatable("uc7core.claimshop.bulksell.sell_btn").getString());

        boolean overPick = hit((int)getMouseX(), (int)getMouseY(), pickerX, pickerY, PICKER_W, FIELD_H);
        int pickBg = overPick ? 0xFF3a5a6a : 0xFF2a3d4a;
        drawBtn(g, theme, pickerX, pickerY, PICKER_W, FIELD_H, pickBg, pickBg, COL_TEXT, "\uD83D\uDD0D");
    }

    private void drawChunkGrid(GuiGraphics g, Theme theme, Minecraft mc, int lh) {
        if (claimedChunks.isEmpty()) {
            String empty = Component.translatable("uc7core.claimshop.bulksell.no_chunks").getString();
            theme.drawString(g, empty,
                    gridX + (gridW - theme.getFont().width(empty)) / 2,
                    gridY + (gridH - lh) / 2, COL_HINT, 0);
            return;
        }

        int cols = Math.max(1, gridW / CHUNK_CELL_W);
        int maxVisible = cols * Math.max(1, gridH / CHUNK_CELL_H);
        int from = Math.min(claimedChunks.size(), Math.max(0, scrollOffset));
        int to = Math.min(claimedChunks.size(), from + maxVisible);

        ResourceLocation dim = mc.level != null ? mc.level.dimension().location() : null;

        for (int i = from; i < to; i++) {
            int col = (i - from) % cols;
            int row = (i - from) / cols;
            int cx = gridX + col * CHUNK_CELL_W + 1;
            int cy = gridY + row * CHUNK_CELL_H + 1;

            ChunkPos pos = claimedChunks.get(i);
            boolean selected = selectedChunks.contains(pos);
            boolean listed = dim != null && alreadyForSale.contains(pos);
            boolean hover = hit((int) getMouseX(), (int) getMouseY(), cx, cy, CHUNK_CELL_W - 2, CHUNK_CELL_H - 2);

            int bg;
            if (listed) {
                bg = 0xFF2a4a2a;
            } else if (selected) {
                bg = 0xFF1a3d5a;
            } else if (hover) {
                bg = 0xFF3a3a4a;
            } else {
                bg = 0xFF1e222a;
            }
            g.fill(cx, cy, cx + CHUNK_CELL_W - 2, cy + CHUNK_CELL_H - 2, bg);

            if (selected) {
                g.fill(cx, cy, cx + CHUNK_CELL_W - 2, cy + 1, 0xFF4CAF50);
                g.fill(cx, cy, cx + 1, cy + CHUNK_CELL_H - 2, 0xFF4CAF50);
                g.fill(cx + CHUNK_CELL_W - 3, cy, cx + CHUNK_CELL_W - 2, cy + CHUNK_CELL_H - 2, 0xFF4CAF50);
                g.fill(cx, cy + CHUNK_CELL_H - 3, cx + CHUNK_CELL_W - 2, cy + CHUNK_CELL_H - 2, 0xFF4CAF50);
            }

            String posStr = pos.x + "," + pos.z;
            int textW = mc.font.width(posStr);
            int tx = cx + (CHUNK_CELL_W - 2 - textW) / 2;
            int ty = cy + (CHUNK_CELL_H - 2 - lh) / 2 + 1;
            int textColor = listed ? 0xFF81C784 : (selected ? 0xFF81C784 : 0xFFd5dbe6);
            g.drawString(mc.font, posStr, tx, ty, textColor, false);
        }
    }

    @Override
    public void drawForeground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        int mx = (int) getMouseX(), my = (int) getMouseY();
        if (hit(backX, backY, BACK_W, BTN_H))
            drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.back.tip"), mx, my);
        if (hit(sellBtnX, sellBtnY, BTN_W, BTN_H))
            drawTooltip(g, theme, Component.translatable("uc7core.claimshop.bulksell.sell_btn.tip"), mx, my);
    }

    @Override
    public boolean mousePressed(MouseButton button) {
        if (button.isLeft()) {
            int mx = (int) getMouseX(), my = (int) getMouseY();
            if (hit(backX, backY, BACK_W, BTN_H)) { previous.openGui(); return true; }
            if (hit(sellBtnX, sellBtnY, BTN_W, BTN_H)) { doSell(); return true; }
            if (hit(pickerX, pickerY, PICKER_W, FIELD_H)) { openItemPicker(); return true; }

            if (my >= gridY && my < gridY + gridH && mx >= gridX && mx < gridX + gridW) {
                int cols = Math.max(1, gridW / CHUNK_CELL_W);
                int cellW = CHUNK_CELL_W - 2;
                int cellH = CHUNK_CELL_H - 2;
                int relX = mx - gridX - 1;
                int relY = my - gridY - 1;
                if (relX >= 0 && relY >= 0) {
                    int col = relX / CHUNK_CELL_W;
                    int row = relY / CHUNK_CELL_H;
                    int idx = scrollOffset + row * cols + col;
                    if (idx >= 0 && idx < claimedChunks.size()) {
                        ChunkPos pos = claimedChunks.get(idx);
                        if (!alreadyForSale.contains(pos)) {
                            if (!selectedChunks.remove(pos)) {
                                selectedChunks.add(pos);
                            }
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player != null) {
                                mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3f, 1.0f);
                            }
                        }
                        return true;
                    }
                }
            }
        }
        return super.mousePressed(button);
    }

    @Override
    public boolean mouseScrolled(double scroll) {
        if (scroll != 0) {
            int cols = Math.max(1, gridW / CHUNK_CELL_W);
            int maxVisibleRows = Math.max(1, gridH / CHUNK_CELL_H);
            int maxVisible = cols * maxVisibleRows;
            int maxScroll = Math.max(0, claimedChunks.size() - maxVisible);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.signum(scroll) * cols));
            return true;
        }
        return super.mouseScrolled(scroll);
    }

    private void doSell() {
        if (selectedChunks.isEmpty()) return;
        String id = itemField.getText().trim();
        if (id.isEmpty()) return;
        int a;
        try { a = Integer.parseInt(amountField.getText().trim()); if (a < 1) a = 1; if (a > 64) a = 64; }
        catch (NumberFormatException e) { a = 1; }

        List<ChunkPos> positions = new ArrayList<>(selectedChunks);
        PacketDistributor.sendToServer(new snoopypupser.buyingchunks.network.BulkSellChunksPacket(id, a, positions));

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 0.5f, 1.0f);
        }
        previous.openGui();
    }

    private void openItemPicker() {
        ItemStackConfig cfg = new ItemStackConfig(true, true);
        pendingItemId = itemField.getText().trim();
        pendingAmount = amountField.getText().trim();
        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(pendingItemId));
            if (!pendingItemId.isEmpty() && item != Items.AIR) {
                int cnt = 1;
                try { cnt = Math.max(1, Math.min(64, Integer.parseInt(pendingAmount))); } catch (Exception ignored) {}
                cfg.setValue(new ItemStack(item, cnt));
            }
        } catch (Exception ignored) {}
        cfg.onClicked(this, MouseButton.LEFT, saved -> {
            if (saved) {
                ItemStack stack = cfg.getValue();
                if (stack != null && !stack.isEmpty()) {
                    pendingItemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    pendingAmount = String.valueOf(stack.getCount());
                }
            }
            Minecraft.getInstance().tell(this::openGui);
        });
    }

    @Override
    protected String getTitleStr() {
        return Component.translatable("uc7core.claimshop.bulksell.title").getString();
    }
}
