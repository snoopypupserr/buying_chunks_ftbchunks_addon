package snoopypupser.buyingchunks.client;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.*;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import snoopypupser.buyingchunks.claimshop.ClaimShopEntry;
import snoopypupser.buyingchunks.claimshop.ClientClaimShopData;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MainDashboardScreen extends BaseAdminScreen {

    private static final int PANEL_W = 240;
    private static final int NUM_ROWS = 4;
    private static final Color4I COL_GOLD = Color4I.rgb(0xFFD700);

    private final SectionRow[] rows = new SectionRow[NUM_ROWS];
    private CloseButton closeBtn;
    private int panelY, totalH, contentStartY, closeBtnY;

    @Override
    public boolean onInit() {
        setFullscreen();
        int lh = Minecraft.getInstance().font.lineHeight;
        totalH = PAD + lh + GAP_TITLE_DIVIDER + 1 + GAP_DIVIDER_CONTENT
                + NUM_ROWS * ROW_H + GAP_CONTENT_BTN + BTN_H + PAD;
        int cx = getWidth() / 2;
        int cy = getHeight() / 2;
        panelY = cy - totalH / 2;
        contentStartY = panelY + PAD + lh + GAP_TITLE_DIVIDER + 1 + GAP_DIVIDER_CONTENT;
        closeBtnY = contentStartY + NUM_ROWS * ROW_H + GAP_CONTENT_BTN;
        return true;
    }

    @Override
    public void addWidgets() {
        for (int i = 0; i < NUM_ROWS; i++) {
            rows[i] = new SectionRow(this, i);
            add(rows[i]);
        }
        closeBtn = new CloseButton(this);
        add(closeBtn);
    }

    @Override
    public void alignWidgets() {
        int cx = getX() + getWidth() / 2;
        for (int i = 0; i < NUM_ROWS; i++) {
            rows[i].setPosAndSize(cx - PANEL_W / 2 + 8, contentStartY + i * ROW_H, PANEL_W - 16, ROW_H);
        }
        closeBtn.setPosAndSize(cx - 30, closeBtnY, 60, BTN_H);
    }

    @Override
    public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        g.fill(0, 0, getWidth(), getHeight(), 0x88000000);
        int cx = getWidth() / 2;
        int px = cx - PANEL_W / 2;
        int py = panelY;
        drawPanel(g, px, py, PANEL_W, totalH);
        String title = Component.translatable("uc7core.claimshop.dashboard.title").getString();
        theme.drawString(g, title, cx - theme.getFont().width(title) / 2, py + PAD, COL_HEADING, 0);
        g.fill(px + 8, py + PAD + theme.getFont().lineHeight + GAP_TITLE_DIVIDER,
                px + PANEL_W - 8, py + PAD + theme.getFont().lineHeight + GAP_TITLE_DIVIDER + 1, C_BORDER);
    }

    @Override
    protected String getTitleStr() {
        return Component.translatable("uc7core.claimshop.dashboard.title").getString();
    }

    private class SectionRow extends Widget {
        final int idx;
        SectionRow(Panel parent, int idx) {
            super(parent); this.idx = idx;
        }
        @Override
        public void addMouseOverText(TooltipList list) {}
        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            boolean hover = isMouseOver();
            if (hover) g.fill(x, y, x + w, y + h, C_HOVER);
            Color4I lc = hover ? COL_GOLD : COL_HEADING;
            String label = getLabel();
            theme.drawString(g, "\u25b8 " + label, x + 4, y + 3, lc, 0);
            String summary = getSummary();
            if (summary != null) {
                theme.drawString(g, summary, x + w - 4 - theme.getFont().width(summary), y + 3, COL_TEXT, 0);
            }
        }
        @Override
        public boolean mousePressed(MouseButton button) {
            if (button.isLeft() && isMouseOver()) { onSectionClick(idx); return true; }
            return false;
        }

        String getLabel() {
            return switch (idx) {
                case 0 -> Component.translatable("uc7core.claimshop.dashboard.marketplace").getString();
                case 1 -> Component.translatable("uc7core.claimshop.dashboard.sell").getString();
                case 2 -> Component.translatable("uc7core.claimshop.dashboard.mylistings").getString();
                case 3 -> Component.translatable("uc7core.claimshop.dashboard.quickbuy").getString();
                default -> "";
            };
        }

        String getSummary() {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return null;
            ResourceLocation dim = player.level().dimension().location();
            int total = ClientClaimShopData.getAllForDimension(dim).size();

            return switch (idx) {
                case 0 -> Component.translatable("uc7core.claimshop.dashboard.marketplace.summary", total).getString();
                case 1 -> "\u25b6 " + getSellHint(player, dim);
                case 2 -> {
                    int c = countMyListings(player);
                    String s = Component.translatable("uc7core.claimshop.dashboard.mylistings.active_count", c).getString();
                    List<ItemStack> income = ClientClaimShopData.getPendingIncome();
                    if (!income.isEmpty()) {
                        int totalCount = income.stream().mapToInt(ItemStack::getCount).sum();
                        ItemStack first = income.get(0);
                    s += "  |  " + totalCount + "x " + first.getItem().getDescription().getString();
                    }
                    yield s;
                }
                case 3 -> ClientClaimShopData.isQuickbuyEnabled()
                        ? Component.translatable("uc7core.claimshop.admin.enabled_status").getString()
                        : Component.translatable("uc7core.claimshop.admin.disabled_status").getString();
                default -> null;
            };
        }
    }

    private String getSellHint(LocalPlayer player, ResourceLocation dim) {
        ChunkPos pos = new ChunkPos(player.blockPosition());
        ClaimShopEntry entry = ClientClaimShopData.getEntry(dim, pos);
        if (entry != null) return entry.getPrice().getCount() + "x " + entry.getPrice().getItem().getDescription().getString();
        return Component.translatable("uc7core.claimshop.dashboard.sell.prompt").getString();
    }

    private int countMyListings(LocalPlayer player) {
        UUID myId = player.getUUID();
        int count = 0;
        for (Map.Entry<ResourceLocation, Map<ChunkPos, ClaimShopEntry>> dim : ClientClaimShopData.getAll().entrySet()) {
            for (Map.Entry<ChunkPos, ClaimShopEntry> e : dim.getValue().entrySet()) {
                if (e.getValue().getSellerUUID().equals(myId)) count++;
            }
        }
        return count;
    }

    private void onSectionClick(int idx) {
        switch (idx) {
            case 0 -> new MarketplaceScreen(this).openGui();
            case 1 -> new SellChunkScreen(this).openGui();
            case 2 -> new MyListingsScreen(this).openGui();
            case 3 -> {
                closeGui(false);
                PacketDistributor.sendToServer(new snoopypupser.buyingchunks.network.ToggleQuickbuyPacket());
            }
        }
    }

    private class CloseButton extends Button {
        CloseButton(Panel parent) {
            super(parent, Component.translatable("uc7core.claimshop.dashboard.close"), Icons.CANCEL);
        }
        @Override
        public void onClicked(MouseButton button) { closeGui(false); }
        @Override
        public void addMouseOverText(TooltipList list) {
            if (isMouseOver())
                list.add(Component.translatable("uc7core.claimshop.dashboard.close.tip"));
        }
        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            boolean hover = isMouseOver();
            drawBtn(g, theme, x, y, w, h, hover ? 0xFF3d1616 : 0xFF2a1010,
                    0xFFE53935, Color4I.rgba(0xFFEF9A9A), getTitle().getString());
        }
        @Override
        public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {}
    }
}
