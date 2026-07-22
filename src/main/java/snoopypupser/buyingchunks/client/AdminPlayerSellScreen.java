package snoopypupser.buyingchunks.client;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.*;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import snoopypupser.buyingchunks.network.AdminActionPacket;


public class AdminPlayerSellScreen extends BaseAdminScreen {

    private static final int BTN_W = 80;

    private final BaseScreen previous;
    private int toggleX, toggleY;
    private int statusY, desc1Y, desc2Y;

    public AdminPlayerSellScreen(BaseScreen previous) { this.previous = previous; }

    @Override
    public boolean onInit() {
        setFullscreen();
        Minecraft mc = Minecraft.getInstance();
        int lh = mc.font.lineHeight;
        String title = Component.translatable("uc7core.claimshop.admin.playersell").getString();
        int titleW = mc.font.width(title);
        int statusW = mc.font.width(Component.translatable("uc7core.claimshop.admin.playersell.enabled").getString());
        String desc1 = Component.translatable("uc7core.claimshop.admin.playersell.desc1").getString();
        String desc2 = Component.translatable("uc7core.claimshop.admin.playersell.desc2").getString();
        int desc1W = mc.font.width(desc1);
        int desc2W = mc.font.width(desc2);
        bw = Math.max(Math.max(Math.max(titleW, statusW), Math.max(desc1W, desc2W)), BTN_W) + PAD * 2 + 20;
        bw = Math.max(bw, 200);
        bh = PAD + BTN_H + GAP_BACK_TITLE + lh + GAP_TITLE_DIVIDER + 1 + GAP_DIVIDER_CONTENT
                + lh + 8 + lh + 6 + lh + 8 + BTN_H + PAD;
        computeTopSection(lh);

        int y = contentY;
        statusY = y;
        y += lh + 8; desc1Y = y;
        y += lh + 6; desc2Y = y;
        y += lh + 8;
        toggleX = bx + bw / 2 - BTN_W / 2; toggleY = y;
        return true;
    }

    @Override public void addWidgets() {}
    @Override public void alignWidgets() {}

    @Override
    public void drawForeground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        int mx = (int) getMouseX(), my = (int) getMouseY();
        if (hit(backX, backY, BACK_W, BTN_H))
            drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.back.tip"), mx, my);
        if (hit(toggleX, toggleY, BTN_W, BTN_H))
            drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.toggle.tip"), mx, my);
    }

    @Override
    public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        g.fill(0, 0, getWidth(), getHeight(), 0x88000000);
        drawPanel(g, bx, by, bw, bh);
        drawTopSection(g, theme);

        int ix = bx + PAD + 2;

        boolean enabled = ClientAdminData.hasData() && ClientAdminData.isPlayerSellEnabled();
        String status = Component.translatable(enabled
                ? "uc7core.claimshop.admin.playersell.enabled"
                : "uc7core.claimshop.admin.playersell.disabled").getString();
        theme.drawString(g, status, bx + (bw - theme.getFont().width(status)) / 2, statusY,
                enabled ? COL_GREEN_T : COL_RED_T, 0);
        theme.drawString(g, Component.translatable("uc7core.claimshop.admin.playersell.desc1").getString(), ix, desc1Y, COL_TEXT, 0);
        theme.drawString(g, Component.translatable("uc7core.claimshop.admin.playersell.desc2").getString(), ix, desc2Y, COL_HINT, 0);

        String btnLabel = enabled
                ? Component.translatable("uc7core.claimshop.admin.playersell.disable_btn").getString()
                : Component.translatable("uc7core.claimshop.admin.playersell.enable_btn").getString();
        drawBtn(g, theme, toggleX, toggleY, BTN_W, BTN_H,
                enabled ? 0xFF3d1616 : 0xFF1a3d16,
                enabled ? 0xFFE53935 : 0xFF4CAF50,
                enabled ? COL_RED_T : COL_GREEN_T,
                btnLabel);
    }

    @Override
    public boolean mousePressed(MouseButton button) {
        if (button.isLeft()) {
            if (hit(backX, backY, BACK_W, BTN_H)) { previous.openGui(); return true; }
            if (hit(toggleX, toggleY, BTN_W, BTN_H)) {
                boolean cur = ClientAdminData.hasData() && ClientAdminData.isPlayerSellEnabled();
                PacketDistributor.sendToServer(new AdminActionPacket(
                        AdminActionPacket.ACTION_SET_PLAYER_SELL, AdminActionPacket.NO_TEAM, "", 0, !cur, AdminActionPacket.NO_DIMENSION));
                previous.openGui();
                return true;
            }
        }
        return super.mousePressed(button);
    }

    @Override
    protected String getTitleStr() {
        return Component.translatable("uc7core.claimshop.admin.playersell").getString();
    }
}
