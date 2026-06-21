package snoopypupser.buyingchunks.client;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.*;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
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

import java.util.*;

public class MyListingsScreen extends BaseScreen {

    private static final int PANEL_W = 320;
    private static final int ITEM_H = 20;
    private static final int PAD = 8;
    private static final int REMOVE_W = 54;
    private static final int COL_BORDER = 0xFF495366;

    private final BaseScreen previous;
    private final List<MyListingEntry> listings = new ArrayList<>();
    private int scrollOffset = 0;

    private int panelX, panelY, panelW, panelH;
    private int titleY;
    private int statsY;
    private int incomeY;
    private int listX, listY, listW, listH;

    private record MyListingEntry(ResourceLocation dim, ChunkPos pos, ClaimShopEntry entry) {}

    public MyListingsScreen(BaseScreen previous) { this.previous = previous; }

    @Override
    public boolean onInit() {
        setFullscreen();
        rebuild();
        return true;
    }

    private void rebuild() {
        listings.clear();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        UUID myId = player.getUUID();

        for (Map.Entry<ResourceLocation, Map<ChunkPos, ClaimShopEntry>> dim : ClientClaimShopData.getAll().entrySet()) {
            for (Map.Entry<ChunkPos, ClaimShopEntry> e : dim.getValue().entrySet()) {
                if (e.getValue().getSellerUUID().equals(myId)) {
                    listings.add(new MyListingEntry(dim.getKey(), e.getKey(), e.getValue()));
                }
            }
        }
        scrollOffset = 0;
    }

    private void computeLayout() {
        Minecraft mc = Minecraft.getInstance();
        int lh = mc.font.lineHeight;
        int sw = getWidth();
        int sh = getHeight();

        panelW = Math.min(PANEL_W, sw - 40);

        int topH = PAD + lh + 2 + 1 + 6; // title + divider
        int statsH = lh + 4;
        int incomeH = ClientClaimShopData.getPendingIncome().isEmpty() ? 0 : lh + 12;
        int maxListItems = Math.min(listings.size(), (sh - topH - statsH - incomeH - PAD - 16) / ITEM_H);
        int listAreaH = maxListItems * ITEM_H + 4;
        panelH = topH + statsH + incomeH + listAreaH + PAD;
        panelH = Math.min(panelH, sh - 16);

        panelX = (sw - panelW) / 2;
        panelY = Math.max(PAD, (sh - panelH) / 2);

        titleY = panelY + PAD + 1;
        statsY = panelY + PAD + lh + 2 + 1 + 6;
        incomeY = statsY + statsH + 2;
        listY = (incomeH > 0 ? incomeY + incomeH : statsY + statsH) + 4;
        listX = panelX + 4;
        listW = panelW - 8;
        listH = panelY + panelH - PAD - listY;
    }

    @Override
    public void addWidgets() {}

    @Override
    public void alignWidgets() {}

    @Override
    public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        g.fill(0, 0, getWidth(), getHeight(), 0x88000000);

        Minecraft mc = Minecraft.getInstance();
        int lh = mc.font.lineHeight;

        computeLayout();
        drawPanelBg(g, panelX, panelY, panelW, panelH);

        int totalListings = listings.size();

        String title = Component.translatable("uc7core.claimshop.mylistings.title").getString();
        theme.drawString(g, title, panelX + (panelW - theme.getFont().width(title)) / 2,
                titleY, Color4I.rgba(0xFFfcfc54), 0);

        g.fill(panelX + PAD, titleY + lh + 2, panelX + panelW - PAD, titleY + lh + 3, COL_BORDER);

        // Stats
        String statStr = Component.translatable("uc7core.claimshop.mylistings.stats",
                totalListings, 0, 0).getString();
        theme.drawString(g, statStr, listX, statsY, Color4I.rgba(0xFFd5dbe6), 0);

        if (totalListings == 0) {
            String empty = Component.translatable("uc7core.claimshop.mylistings.empty").getString();
            theme.drawString(g, empty, panelX + (panelW - theme.getFont().width(empty)) / 2,
                    listY + (listH - lh) / 2, Color4I.rgba(0xFF888888), 0);
        }

        // Pending Income
        List<ItemStack> income = ClientClaimShopData.getPendingIncome();
        if (!income.isEmpty()) {
            String incomeLabel = Component.translatable("uc7core.claimshop.mylistings.pending_income").getString();
            int tx = listX;
            theme.drawString(g, incomeLabel, tx, incomeY, Color4I.rgba(0xFF81C784), 0);
            tx += theme.getFont().width(incomeLabel) + 6;
            for (ItemStack stack : income) {
                g.renderItem(stack, tx, incomeY - 1);
                String cnt = Component.translatable("uc7core.claimshop.mylistings.count_prefix", stack.getCount()).getString();
                theme.drawString(g, cnt, tx + 18, incomeY + 1, Color4I.rgba(0xFFd5dbe6), 0);
                tx += 18 + theme.getFont().width(cnt) + 4;
                if (tx > panelX + panelW - 20) break;
            }
        }

        // Listings
        g.fill(listX - 1, listY - 1, listX + listW + 1, listY, COL_BORDER);
        g.fill(listX, listY, listX + listW, listY + listH, 0xFF2e3440);

        int maxVis = Math.max(0, listH / ITEM_H);
        int from = Math.min(listings.size(), Math.max(0, scrollOffset));
        int to = Math.min(listings.size(), from + maxVis);

        for (int i = from; i < to; i++) {
            MyListingEntry le = listings.get(i);
            int iy = listY + (i - from) * ITEM_H;
            boolean hover = isMouseOver() && getMouseY() >= iy && getMouseY() < iy + ITEM_H
                    && getMouseX() >= listX && getMouseX() < listX + listW;
            if (hover) g.fill(listX, iy, listX + listW, iy + ITEM_H, 0x33FFFFFF);

            String posStr = Component.translatable("uc7core.claimshop.position.brackets", le.pos().x, le.pos().z).getString();
            theme.drawString(g, posStr, listX + 4, iy + 3, Color4I.rgba(0xFFfcfc54), 0);

            int tx = listX + theme.getFont().width(posStr) + 12;
            String priceStr = le.entry().getPrice().getCount() + "x " + le.entry().getPrice().getItem().getDescription().getString();
            theme.drawString(g, priceStr, tx, iy + 3, Color4I.rgba(0xFF81C784), 0);

            int rmX = listX + listW - REMOVE_W - 2;
            int rmY = iy + 2;
            drawSmallBtn(g, rmX, rmY, REMOVE_W, ITEM_H - 4, 0xFF2a1010, 0xFFE53935,
                    Color4I.rgba(0xFFEF9A9A), Component.translatable("uc7core.claimshop.mylistings.remove").getString());
        }
    }

    @Override
    public boolean mousePressed(MouseButton button) {
        if (button.isLeft()) {
            int mx = (int) getMouseX(), my = (int) getMouseY();
            if (my >= listY && my < listY + listH && mx >= listX && mx < listX + listW) {
                int idx = (my - listY) / ITEM_H + scrollOffset;
                if (idx >= 0 && idx < listings.size()) {
                    MyListingEntry le = listings.get(idx);
                    int rmX = listX + listW - REMOVE_W - 2;
                    int rmY = listY + (idx - scrollOffset) * ITEM_H + 2;
                    if (mx >= rmX && mx < rmX + REMOVE_W && my >= rmY && my < rmY + ITEM_H - 4) {
                        doRemove(le);
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
            int maxVis = Math.max(0, listH / ITEM_H);
            int maxScroll = Math.max(0, listings.size() - maxVis);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.signum(scroll)));
            return true;
        }
        return super.mouseScrolled(scroll);
    }

    private void doRemove(MyListingEntry le) {
        PacketDistributor.sendToServer(new snoopypupser.buyingchunks.network.RemoveListingPacket(le.pos().x, le.pos().z));
        listings.remove(le);
        scrollOffset = 0;
    }

    private void drawPanelBg(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y - 1, x + w, y, 0xFF101010);
        g.fill(x, y + h, x + w, y + h + 1, 0xFF101010);
        g.fill(x - 1, y, x, y + h, 0xFF101010);
        g.fill(x + w, y, x + w + 1, y + h, 0xFF101010);
        g.fill(x, y, x + w - 1, y + 1, 0xFF4a5368);
        g.fill(x, y, x + 1, y + h - 1, 0xFF4a5368);
        g.fill(x + 1, y + h - 1, x + w, y + h, 0xFF1a1f2a);
        g.fill(x + w - 1, y + 1, x + w, y + h, 0xFF1a1f2a);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF2e3440);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF3d4758);
    }

    private void drawSmallBtn(GuiGraphics g, int x, int y, int w, int h,
                              int bg, int border, Color4I textCol, String label) {
        g.fill(x - 1, y - 1, x + w + 1, y, 0xFF101010);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, 0xFF101010);
        g.fill(x - 2, y, x - 1, y + h, 0xFF101010);
        g.fill(x + w + 1, y, x + w + 2, y + h, 0xFF101010);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF101010);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF101010);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        int mx = (int) getMouseX(), my = (int) getMouseY();
        if (mx >= x && mx < x + w && my >= y && my < y + h)
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x33FFFFFF);
        Minecraft mc = Minecraft.getInstance();
        int tw = mc.font.width(label);
        int tx = x + (w - tw) / 2;
        int ty = y + (h - mc.font.lineHeight) / 2 + 1;
        g.drawString(mc.font, label, tx, ty, textCol.rgba(), false);
    }

    @Override
    public Theme getTheme() { return NordTheme.THEME; }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
