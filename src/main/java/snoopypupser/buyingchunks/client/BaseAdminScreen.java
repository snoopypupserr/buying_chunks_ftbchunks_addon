package snoopypupser.buyingchunks.client;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public abstract class BaseAdminScreen extends BaseScreen {

    protected static final int C_DARK   = 0xFF101010;
    protected static final int C_HL     = 0xFF4a5368;
    protected static final int C_BG     = 0xFF2e3440;
    protected static final int C_ACCENT = 0xFF3d4758;
    protected static final int C_BORDER = 0xFF495366;
    protected static final int C_HOVER  = 0x33FFFFFF;

    protected static final Color4I COL_HEADING = Color4I.rgba(0xFFfcfc54);
    protected static final Color4I COL_TEXT    = Color4I.rgba(0xFFd5dbe6);
    protected static final Color4I COL_HINT    = Color4I.rgba(0xFF888888);
    protected static final Color4I COL_GREEN_T = Color4I.rgba(0xFF81C784);
    protected static final Color4I COL_RED_T   = Color4I.rgba(0xFFEF9A9A);

    protected static final int PAD = 8;
    protected static final int BTN_H = 14;
    protected static final int FIELD_H = 14;
    protected static final int BACK_W = 50;
    protected static final int ROW_H = 16;

    protected static final int GAP_BACK_TITLE = 4;
    protected static final int GAP_TITLE_DIVIDER = 2;
    protected static final int GAP_DIVIDER_CONTENT = 6;
    protected static final int GAP_LABEL_FIELD = 4;
    protected static final int GAP_FIELD_NEXT = 6;
    protected static final int GAP_CONTENT_BTN = 8;

    protected int bx, by, bw, bh;
    protected int backX, backY;
    protected int titleY, contentY;

    protected void computeTopSection(int lh) {
        bx = (getWidth() - bw) / 2;
        by = Math.max(PAD, (getHeight() - bh) / 2);
        int baseX = bx + PAD + 2;
        int y = by + PAD;
        backX = baseX; backY = y;
        y += BTN_H + GAP_BACK_TITLE;
        titleY = y;
        y += lh + GAP_TITLE_DIVIDER + 1;
        contentY = y + GAP_DIVIDER_CONTENT;
    }

    protected void drawTopSection(GuiGraphics g, Theme theme) {
        int ix = bx + PAD + 2;
        drawBtn(g, theme, backX, backY, BACK_W, BTN_H, 0xFF1a1a2e, 0xFF495366, COL_TEXT,
                Component.translatable("uc7core.claimshop.admin.back").getString());
        String title = getTitleStr();
        theme.drawString(g, title, bx + (bw - theme.getFont().width(title)) / 2, titleY, COL_HEADING, 0);
        g.fill(ix, titleY + theme.getFont().lineHeight + GAP_TITLE_DIVIDER,
                bx + bw - PAD - 2, titleY + theme.getFont().lineHeight + GAP_TITLE_DIVIDER + 1, C_BORDER);
    }

    protected abstract String getTitleStr();

    @Override
    public Theme getTheme() { return NordTheme.THEME; }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    protected void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y - 1, x + w, y, C_DARK);
        g.fill(x, y + h, x + w, y + h + 1, C_DARK);
        g.fill(x - 1, y, x, y + h, C_DARK);
        g.fill(x + w, y, x + w + 1, y + h, C_DARK);
        g.fill(x, y, x + w - 1, y + 1, C_HL);
        g.fill(x, y, x + 1, y + h - 1, C_HL);
        g.fill(x + 1, y + h - 1, x + w, y + h, 0xFF1a1f2a);
        g.fill(x + w - 1, y + 1, x + w, y + h, 0xFF1a1f2a);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, C_BG);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, C_ACCENT);
    }

    protected void drawBtn(GuiGraphics g, Theme theme, int x, int y, int w, int h,
                           int bg, int border, Color4I textColor, String label) {
        g.fill(x, y - 1, x + w, y, C_DARK);
        g.fill(x, y + h, x + w, y + h + 1, C_DARK);
        g.fill(x - 1, y, x, y + h, C_DARK);
        g.fill(x + w, y, x + w + 1, y + h, C_DARK);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF101010);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF101010);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        if (hit(x, y, w, h))
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, C_HOVER);
        theme.drawString(g, label, x + (w - theme.getFont().width(label)) / 2,
                y + (h - theme.getFont().lineHeight) / 2 + 1, textColor, 0);
    }

    protected boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    protected boolean hit(int x, int y, int w, int h) {
        return hit((int) getMouseX(), (int) getMouseY(), x, y, w, h);
    }

    protected void drawTooltip(GuiGraphics g, Theme theme, Component text, int mx, int my) {
        var font = theme.getFont();
        int tw = font.width(text);
        int th = font.lineHeight;
        int px = 4, py = 2;
        int sw = getWidth();
        int tx = mx + 8;
        int ty = my - 14 - th - py * 2;
        int bw = tw + px * 2, bh = th + py * 2;

        if (tx + bw + 2 > sw) tx = mx - 8 - bw;
        if (ty < 2) ty = my + 14;

        g.fill(tx - 1, ty - 1, tx + bw + 1, ty + bh + 1, 0xFF000000);
        g.fill(tx, ty, tx + bw, ty + bh, 0xFF100010);
        g.fill(tx + 1, ty + 1, tx + bw - 1, ty + 2, 0xFF505010);
        g.fill(tx + 1, ty + 1, tx + 2, ty + bh - 1, 0xFF505010);

        theme.drawString(g, text, tx + px, ty + py, COL_TEXT, 0);
    }
}
