package snoopypupser.buyingchunks.client;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.*;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import snoopypupser.buyingchunks.claimshop.ClientClaimShopData;

public class AdminDashboardScreen extends BaseAdminScreen {

    private static final int PANEL_W = 240;

    private static final int C_YELLOW  = 0xFFFFD700;
    private static final Color4I COL_YELLOW = Color4I.rgb(0xFFD700);

    private final SectionRow[] rows = new SectionRow[7];
    private CloseButton closeBtn;
    private int panelY, totalH, contentStartY, closeBtnY;

    @Override
    public boolean onInit() {
        setFullscreen();
        int lh = Minecraft.getInstance().font.lineHeight;
        totalH = PAD + lh + GAP_TITLE_DIVIDER + 1 + GAP_DIVIDER_CONTENT
                + 7 * ROW_H + GAP_CONTENT_BTN + BTN_H + PAD;
        int cx = getWidth() / 2;
        int cy = getHeight() / 2;
        panelY = cy - totalH / 2;
        contentStartY = panelY + PAD + lh + GAP_TITLE_DIVIDER + 1 + GAP_DIVIDER_CONTENT;
        closeBtnY = contentStartY + 7 * ROW_H + GAP_CONTENT_BTN;
        return true;
    }

    @Override
    public void addWidgets() {
        for (int i = 0; i < 7; i++) {
            rows[i] = new SectionRow(this, i);
            add(rows[i]);
        }
        closeBtn = new CloseButton(this);
        add(closeBtn);
    }

    @Override
    public void alignWidgets() {
        int cx = getX() + getWidth() / 2;
        for (int i = 0; i < 7; i++) {
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
        String title = Component.translatable("uc7core.claimshop.admin.title").getString();
        theme.drawString(g, title, cx - theme.getFont().width(title) / 2, py + PAD, COL_HEADING, 0);
        g.fill(px + 8, py + PAD + theme.getFont().lineHeight + GAP_TITLE_DIVIDER,
                px + PANEL_W - 8, py + PAD + theme.getFont().lineHeight + GAP_TITLE_DIVIDER + 1, C_BORDER);
    }

    @Override
    protected String getTitleStr() {
        return Component.translatable("uc7core.claimshop.admin.title").getString();
    }

    private class SectionRow extends Widget {
        final int idx;
        final String labelKey;
        SectionRow(Panel parent, int idx) {
            super(parent); this.idx = idx;
            labelKey = switch (idx) {
                case 0 -> "uc7core.claimshop.admin.basecost";
                case 1 -> "uc7core.claimshop.admin.playersell";
                case 2 -> "uc7core.claimshop.admin.teamprices";
                case 3 -> "uc7core.claimshop.admin.buylimits";
                case 4 -> "uc7core.claimshop.admin.playerincome";
                case 5 -> "uc7core.claimshop.admin.autoreclaim";
                case 6 -> "uc7core.claimshop.admin.serverteam.mgmt";
                default -> "";
            };
        }
        @Override
        public void addMouseOverText(TooltipList list) {
            if (isMouseOver())
                list.add(Component.translatable(labelKey + ".tip"));
        }
        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            boolean hover = isMouseOver();
            if (hover) g.fill(x, y, x + w, y + h, C_HOVER);
            Color4I lc = hover ? COL_YELLOW : COL_HEADING;
            String label = Component.translatable(labelKey).getString();
            theme.drawString(g, "\u25b8 " + label, x + 4, y + 3, lc, 0);
            String summary = getSummary();
            theme.drawString(g, summary, x + w - 4 - theme.getFont().width(summary), y + 3, COL_TEXT, 0);
        }
        @Override
        public boolean mousePressed(MouseButton button) {
            if (button.isLeft() && isMouseOver()) { onSectionClick(idx); return true; }
            return false;
        }
        String getSummary() {
            return switch (idx) {
                case 0 -> {
                    ItemStack bc = ClientClaimShopData.getBaseCost();
                    if (bc == null || bc.isEmpty())
                        yield Component.translatable("uc7core.claimshop.admin.basecost.notset").getString();
                    yield Component.translatable("uc7core.claimshop.admin.basecost.summary",
                            bc.getCount(), bc.getItem().getDescription().getString()).getString();
                }
                case 1 -> {
                    boolean e = ClientAdminData.hasData() && ClientAdminData.isPlayerSellEnabled();
                    String s = Component.translatable(e
                            ? "uc7core.claimshop.admin.playersell.enabled"
                            : "uc7core.claimshop.admin.playersell.disabled").getString();
                    yield Component.translatable("uc7core.claimshop.admin.playersell.summary", s).getString();
                }
                case 2 -> {
                    int c = ClientAdminData.hasData() ? ClientAdminData.getTeamPrices().size() : 0;
                    yield Component.translatable("uc7core.claimshop.admin.teamprices.summary", c).getString();
                }
                case 3 -> {
                    int c = ClientAdminData.hasData() ? ClientAdminData.getTeamChunkLimits().size() : 0;
                    yield Component.translatable("uc7core.claimshop.admin.buylimits.summary", c).getString();
                }
                case 4 -> {
                    int c = ClientAdminData.hasData() ? ClientAdminData.getPlayerIncomeDisabled().size() : 0;
                    yield Component.translatable("uc7core.claimshop.admin.playerincome.summary", c).getString();
                }
                case 5 -> {
                    int c = ClientAdminData.hasData() ? ClientAdminData.getAutoReclaimTeams().size() : 0;
                    yield Component.translatable("uc7core.claimshop.admin.autoreclaim.summary", c).getString();
                }
                case 6 -> "";
                default -> "";
            };
        }
    }

    private class CloseButton extends Button {
        CloseButton(Panel parent) {
            super(parent, Component.translatable("uc7core.claimshop.admin.close"), Icons.CANCEL);
        }
        @Override
        public void onClicked(MouseButton button) { closeGui(false); }
        @Override
        public void addMouseOverText(TooltipList list) {
            if (isMouseOver())
                list.add(Component.translatable("uc7core.claimshop.admin.close.tip"));
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

    private void onSectionClick(int idx) {
        switch (idx) {
            case 0 -> new AdminBaseCostScreen(this).openGui();
            case 1 -> new AdminPlayerSellScreen(this).openGui();
            case 2 -> new AdminTeamSettingsScreen(this, AdminTeamSettingsScreen.Mode.TEAM_PRICE).openGui();
            case 3 -> new AdminTeamSettingsScreen(this, AdminTeamSettingsScreen.Mode.BUY_LIMIT).openGui();
            case 4 -> new AdminTeamSettingsScreen(this, AdminTeamSettingsScreen.Mode.PLAYER_INCOME).openGui();
            case 5 -> new AdminTeamSettingsScreen(this, AdminTeamSettingsScreen.Mode.AUTO_RECLAIM).openGui();
            case 6 -> new AdminServerTeamMgmtScreen(this).openGui();
        }
    }
}
