package snoopypupser.buyingchunks.client;

import dev.ftb.mods.ftblibrary.ui.*;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import snoopypupser.buyingchunks.claimshop.ClaimShopEntry;
import snoopypupser.buyingchunks.claimshop.ClientClaimShopData;

import java.util.*;

public class ListingsScreen extends BaseAdminScreen {

    public enum Mode { ALL, MINE }

    private static final int SMALL_BTN_W = 20;
    private static final int MAX_VIS = 8;

    private final BaseScreen previous;
    private final Mode mode;

    private int listTop;
    private int scroll = 0;

    private final List<ListingEntry> entries = new ArrayList<>();

    private record ListingEntry(ChunkPos pos, ClaimShopEntry entry) {}

    public ListingsScreen(BaseScreen previous, Mode mode) {
        this.previous = previous;
        this.mode = mode;
    }

    @Override
    public boolean onInit() {
        setFullscreen();
        buildEntries();

        Minecraft mc = Minecraft.getInstance();
        int lh = mc.font.lineHeight;
        int cw = Math.max(mc.font.width(getTitleStr()), 200);
        bw = cw + PAD * 2 + 10;
        int listRows = Math.min(entries.size(), MAX_VIS);
        int listH = Math.max(listRows * ROW_H, ROW_H);
        bh = PAD + BTN_H + GAP_BACK_TITLE + lh + GAP_TITLE_DIVIDER + 1 + GAP_DIVIDER_CONTENT
                + listH + 4 + GAP_CONTENT_BTN + BTN_H + PAD;
        computeTopSection(lh);

        int y = contentY;
        listTop = y;
        return true;
    }

    @Override
    public void addWidgets() {}

    @Override
    public void alignWidgets() {}

    @Override
    public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        int mx = (int) getMouseX(), my = (int) getMouseY();
        g.fill(0, 0, getWidth(), getHeight(), 0x88000000);
        drawPanel(g, bx, by, bw, bh);
        drawTopSection(g, theme);

        if (entries.isEmpty()) {
            String empty = Component.translatable("uc7core.claimshop.list.none").getString();
            theme.drawString(g, empty, bx + (bw - theme.getFont().width(empty)) / 2, listTop + 2, COL_HINT, 0);
        } else {
            int vis = Math.min(entries.size(), MAX_VIS);
            for (int i = scroll; i < entries.size() && i < scroll + vis; i++) {
                int ry = listTop + (i - scroll) * ROW_H;
                if (hit(mx, my, bx + PAD, ry, bw - PAD * 2, ROW_H))
                    g.fill(bx + PAD, ry, bx + bw - PAD, ry + ROW_H, C_HOVER);
                ListingEntry le = entries.get(i);
                String line = String.format("[%d, %d]  %dx %s  (%s)",
                        le.pos().x, le.pos().z,
                        le.entry().getPrice().getCount(),
                        le.entry().getPrice().getItem().getDescription().getString(),
                        le.entry().getShopTeamName());
                int textLeft = bx + PAD + 6;
                int textW = bw - PAD * 2 - 12;
                var f = theme.getFont();
                if (f.width(line) > textW) {
                    line = f.plainSubstrByWidth(line, textW - f.width("...")) + "...";
                }
                theme.drawString(g, line, textLeft, ry + (ROW_H - f.lineHeight) / 2 + 1, COL_TEXT, 0);

                if (mode == Mode.MINE) {
                    int rxRemove = bx + bw - PAD - SMALL_BTN_W - 2;
                    drawBtn(g, theme, rxRemove, ry + 1, SMALL_BTN_W, BTN_H, 0xFF3d1616, 0xFFE53935, COL_RED_T, "\u2715");
                }
            }
        }
    }

    @Override
    public void drawForeground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        int mx = (int) getMouseX(), my = (int) getMouseY();
        if (hit(backX, backY, BACK_W, BTN_H))
            drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.back.tip"), mx, my);

        if (mode == Mode.MINE && !entries.isEmpty()) {
            int vis = Math.min(entries.size(), MAX_VIS);
            for (int i = scroll; i < entries.size() && i < scroll + vis; i++) {
                int ry = listTop + (i - scroll) * ROW_H;
                int rxRemove = bx + bw - PAD - SMALL_BTN_W - 2;
                if (hit(rxRemove, ry + 1, SMALL_BTN_W, BTN_H))
                    drawTooltip(g, theme, Component.translatable("uc7core.claimshop.listings.remove.tip"), mx, my);
            }
        }

        if (entries.size() > MAX_VIS) {
            int vis = Math.min(entries.size(), MAX_VIS);
            String scrollHint = "(" + (scroll + 1) + "-" + Math.min(scroll + vis, entries.size()) + "/" + entries.size() + "  \u2191\u2193)";
            var f = theme.getFont();
            theme.drawString(g, scrollHint, bx + bw - PAD - 2 - f.width(scrollHint), listTop + Math.min(vis, entries.size()) * ROW_H + 4, COL_HINT, 0);
        }
    }

    @Override
    public boolean mousePressed(MouseButton button) {
        if (button.isLeft()) {
            if (hit(backX, backY, BACK_W, BTN_H)) { previous.openGui(); return true; }

            if (mode == Mode.MINE && !entries.isEmpty()) {
                int vis = Math.min(entries.size(), MAX_VIS);
                for (int i = scroll; i < entries.size() && i < scroll + vis; i++) {
                    int ry = listTop + (i - scroll) * ROW_H;
                    int rxRemove = bx + bw - PAD - SMALL_BTN_W - 2;
                    if (hit(rxRemove, ry + 1, SMALL_BTN_W, BTN_H)) {
                        ListingEntry le = entries.get(i);
                        Minecraft.getInstance().player.connection.sendCommand(
                                "ftbshop remove " + le.pos().x + " " + le.pos().z);
                        previous.openGui();
                        return true;
                    }
                }
            }
        }
        return super.mousePressed(button);
    }

    @Override
    public boolean mouseScrolled(double amount) {
        int maxOff = Math.max(0, entries.size() - MAX_VIS);
        scroll = (int) Math.max(0, Math.min(maxOff, scroll - amount));
        return true;
    }

    @Override
    protected String getTitleStr() {
        return mode == Mode.ALL
                ? Component.translatable("uc7core.claimshop.listings.all.title").getString()
                : Component.translatable("uc7core.claimshop.listings.mine.title").getString();
    }

    private void buildEntries() {
        entries.clear();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        UUID myId = player.getUUID();

        for (Map.Entry<ResourceLocation, Map<ChunkPos, ClaimShopEntry>> dimEntry : ClientClaimShopData.getAll().entrySet()) {
            for (Map.Entry<ChunkPos, ClaimShopEntry> e : dimEntry.getValue().entrySet()) {
                if (mode == Mode.ALL) {
                    entries.add(new ListingEntry(e.getKey(), e.getValue()));
                } else if (e.getValue().getSellerUUID().equals(myId)) {
                    entries.add(new ListingEntry(e.getKey(), e.getValue()));
                }
            }
        }
    }
}
