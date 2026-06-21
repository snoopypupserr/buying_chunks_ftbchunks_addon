package snoopypupser.buyingchunks.client;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.*;
import dev.ftb.mods.ftblibrary.ui.input.Key;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import snoopypupser.buyingchunks.claimshop.ClaimShopEntry;
import snoopypupser.buyingchunks.claimshop.ClientClaimShopData;
import snoopypupser.buyingchunks.network.BuyChunkPacket;

import java.util.*;

public class MarketplaceScreen extends BaseScreen {

    private static final int PANEL_W = 340;
    private static final int ITEM_H = 20;
    private static final int PAD = 8;
    private static final int CONTROL_H = 14;
    private static final int BTN_SORT_W = 32;
    private static final int STAR_W = 12;
    private static final int BTN_BUY_W = 38;
    private static final int COL_BORDER = 0xFF495366;

    private final BaseScreen previous;
    private final List<ChunkEntry> allEntries = new ArrayList<>();
    private final List<ChunkEntry> filtered = new ArrayList<>();

    private String searchText = "";
    private boolean showFavoritesOnly = false;
    private SortMode sortMode = SortMode.PRICE_ASC;
    private int scrollOffset = 0;

    private TextBox searchField;

    private int panelX, panelY, panelW, panelH;
    private int titleY;
    private int tabY;
    private int controlY, controlRightEdge;
    private int listX, listY, listW, listH;

    private enum SortMode {
        PRICE_ASC, PRICE_DESC, DISTANCE
    }

    private static final String[] SORT_LABELS = {
            Component.translatable("uc7core.claimshop.marketplace.sort.price_asc").getString(),
            Component.translatable("uc7core.claimshop.marketplace.sort.price_desc").getString(),
            Component.translatable("uc7core.claimshop.marketplace.sort.distance").getString()
    };
    private static final String[] SORT_TOOLTIPS = {
            Component.translatable("uc7core.claimshop.marketplace.sort.price_asc_tip").getString(),
            Component.translatable("uc7core.claimshop.marketplace.sort.price_desc_tip").getString(),
            Component.translatable("uc7core.claimshop.marketplace.sort.distance_tip").getString()
    };

    private record ChunkEntry(ResourceLocation dim, ChunkPos pos, ClaimShopEntry entry, double dist) {}

    public MarketplaceScreen(BaseScreen previous) { this.previous = previous; }

    @Override
    public boolean onInit() {
        setFullscreen();
        rebuildEntries();
        return true;
    }

    private void rebuildEntries() {
        allEntries.clear();
        Minecraft mc = Minecraft.getInstance();
        BlockPos playerPos = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;

        for (Map.Entry<ResourceLocation, Map<ChunkPos, ClaimShopEntry>> dim : ClientClaimShopData.getAll().entrySet()) {
            for (Map.Entry<ChunkPos, ClaimShopEntry> e : dim.getValue().entrySet()) {
                double dist = Math.sqrt(Math.pow(e.getKey().x * 16 + 8 - playerPos.getX(), 2)
                        + Math.pow(e.getKey().z * 16 + 8 - playerPos.getZ(), 2));
                allEntries.add(new ChunkEntry(dim.getKey(), e.getKey(), e.getValue(), dist));
            }
        }
        applyFilters();
    }

    private void applyFilters() {
        filtered.clear();
        for (ChunkEntry ce : allEntries) {
            if (showFavoritesOnly && !ClientClaimShopData.isFavorite(ce.dim(), ce.pos())) continue;
            if (!searchText.isEmpty() && !ce.entry().getShopTeamName().toLowerCase().contains(searchText.toLowerCase())) continue;
            filtered.add(ce);
        }
        sortEntries();
    }

    private void sortEntries() {
        switch (sortMode) {
            case PRICE_ASC -> filtered.sort(Comparator.comparingInt(a -> a.entry().getPrice().getCount()));
            case PRICE_DESC -> filtered.sort((a, b) -> Integer.compare(b.entry().getPrice().getCount(), a.entry().getPrice().getCount()));
            case DISTANCE -> filtered.sort(Comparator.comparingDouble(a -> a.dist()));
        }
        scrollOffset = 0;
    }

    private int getVisibleCount() {
        return filtered.size();
    }

    private void computeLayout() {
        Minecraft mc = Minecraft.getInstance();
        int lh = mc.font.lineHeight;
        int sw = getWidth();
        int sh = getHeight();

        panelW = Math.min(PANEL_W, sw - 40);
        int topH = PAD + lh + 2 + 1 + 6 + CONTROL_H + 4;
        int maxListItems = Math.min(getVisibleCount(), (sh - topH - PAD - 16) / ITEM_H);
        int listAreaH = Math.max(maxListItems * ITEM_H + 4, lh + 12);
        panelH = topH + listAreaH + PAD;
        panelH = Math.min(panelH, sh - 16);

        panelX = (sw - panelW) / 2;
        panelY = Math.max(PAD, (sh - panelH) / 2);
        panelY = Math.min(panelY, sh - panelH - PAD);

        titleY = panelY + PAD + 1;
        controlY = panelY + PAD + lh + 2 + 1 + 6;
        controlRightEdge = panelX + panelW - PAD;

        listX = panelX + 4;
        listY = controlY + CONTROL_H + 4;
        listW = panelW - 8;
        listH = panelY + panelH - PAD - listY;
    }

    @Override
    public void addWidgets() {
        searchField = new TextBox(this) {
            @Override
            public boolean keyPressed(Key key) {
                boolean result = super.keyPressed(key);
                searchText = getText();
                applyFilters();
                return result;
            }
        };
        searchField.setMaxLength(32);
        searchField.textColor = Color4I.rgba(0xFFd5dbe6);
        add(searchField);
    }

    @Override
    public void alignWidgets() {
        computeLayout();
        int searchW = (controlRightEdge - 12) - (panelX + PAD) - (BTN_SORT_W * 3 + 6 + STAR_W + 8);
        searchW = Math.max(60, searchW);
        searchField.setPosAndSize(panelX + PAD + 2, controlY, searchW, CONTROL_H);
    }
    @Override
    public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        g.fill(0, 0, getWidth(), getHeight(), 0x88000000);
        drawPanelBg(g, panelX, panelY, panelW, panelH);

        Minecraft mc = Minecraft.getInstance();
        int lh = mc.font.lineHeight;

        String title = Component.translatable("uc7core.claimshop.marketplace.title").getString();
        theme.drawString(g, title, panelX + (panelW - theme.getFont().width(title)) / 2, titleY,
                Color4I.rgba(0xFFfcfc54), 0);

        g.fill(panelX + PAD, titleY + lh + 2, panelX + panelW - PAD, titleY + lh + 3, COL_BORDER);

        drawBuyView(g, theme, mc, lh);
    }

    private void drawBuyView(GuiGraphics g, Theme theme, Minecraft mc, int lh) {
        if (searchText.isEmpty() && !searchField.isFocused()) {
            String hint = Component.translatable("uc7core.claimshop.marketplace.search_hint").getString();
            theme.drawString(g, hint, searchField.getX() + 2, controlY + 2, Color4I.rgba(0xFF666666), 0);
        }

        int cx = controlRightEdge;
        boolean starHover = hit(panelX, panelY, panelW, panelH) && getMouseX() >= cx - STAR_W - 2
                && getMouseX() < cx - 2 && getMouseY() >= controlY && getMouseY() < controlY + CONTROL_H;
        Color4I starCol = showFavoritesOnly ? Color4I.rgb(0xFFFFD700) : Color4I.rgba(0xFF555555);
        if (starHover) starCol = showFavoritesOnly ? Color4I.rgb(0xFFFFF0A0) : Color4I.rgba(0xFF888888);
        theme.drawString(g, "\u2605", cx - STAR_W - 2, controlY + 1, starCol, 0);
        cx -= STAR_W + 6;

        for (int i = 2; i >= 0; i--) {
            cx -= BTN_SORT_W;
            boolean active = sortMode.ordinal() == i;
            int bg = active ? 0xFF3a5a6a : 0xFF2a3d4a;
            int border = active ? 0xFF5a8aaa : 0xFF3d4758;
            drawSmallBtn(g, cx, controlY, BTN_SORT_W, CONTROL_H, bg, border,
                    active ? Color4I.rgba(0xFFfcfc54) : Color4I.rgba(0xFFd5dbe6), SORT_LABELS[i]);
            cx -= 2;
        }

        g.fill(listX - 1, listY - 1, listX + listW + 1, listY, COL_BORDER);
        g.fill(listX, listY, listX + listW, listY + listH, 0xFF2e3440);

        int maxVis = Math.max(0, listH / ITEM_H);
        int from = Math.min(filtered.size(), Math.max(0, scrollOffset));
        int to = Math.min(filtered.size(), from + maxVis);

        for (int i = from; i < to; i++) {
            ChunkEntry ce = filtered.get(i);
            int iy = listY + (i - from) * ITEM_H;
            boolean hover = isMouseOver() && getMouseY() >= iy && getMouseY() < iy + ITEM_H
                    && getMouseX() >= listX && getMouseX() < listX + listW;
            if (hover) g.fill(listX, iy, listX + listW, iy + ITEM_H, 0x33FFFFFF);

            boolean isFav = ClientClaimShopData.isFavorite(ce.dim(), ce.pos());
            Color4I favCol = isFav ? Color4I.rgb(0xFFFFD700) : Color4I.rgba(0xFF555555);

            theme.drawString(g, "\u2605", listX + 2, iy + 3, favCol, 0);

            String posStr = Component.translatable("uc7core.claimshop.position.brackets", ce.pos().x, ce.pos().z).getString();
            int posW = mc.font.width(posStr);

            String shopNameFull = ce.entry().getShopTeamName();
            int shopNameW = mc.font.width(shopNameFull);

            String priceStr = ce.entry().getPrice().getCount() + "x " + ce.entry().getPrice().getItem().getDescription().getString();
            int priceW = mc.font.width(priceStr);

            String distStr = Component.translatable("uc7core.claimshop.marketplace.distance", String.format("%.0f", ce.dist())).getString();
            int distW = mc.font.width(distStr);

            int buyBtnLeft = listX + listW - BTN_BUY_W - 2;
            int rightEdge = buyBtnLeft - 6;

            int leftEdge = listX + STAR_W + 6;

            int rightGroupW = priceW + 6 + distW;
            int rightGroupX = rightEdge - rightGroupW;

            int available = rightGroupX - 6 - leftEdge;
            int posAndGap = posW + 6;
            int nameW = Math.max(10, available - posAndGap);

            String shopName = shopNameFull;
            if (shopNameW > nameW) {
                shopName = mc.font.plainSubstrByWidth(shopNameFull, Math.max(0, nameW - 2)) + "\u2026";
            }

            theme.drawString(g, posStr, leftEdge, iy + 3, Color4I.rgba(0xFFfcfc54), 0);
            theme.drawString(g, shopName, leftEdge + posAndGap, iy + 3, Color4I.rgb(ce.entry().getTeamColor()), 0);
            theme.drawString(g, priceStr, rightGroupX, iy + 3, Color4I.rgba(0xFF81C784), 0);
            theme.drawString(g, distStr, rightGroupX + priceW + 6, iy + 3, Color4I.rgba(0xFF888888), 0);

            int by = iy + 2;
            drawSmallBtn(g, buyBtnLeft, by, BTN_BUY_W, ITEM_H - 4, 0xFF1a3d16, 0xFF4CAF50,
                    Color4I.rgba(0xFF81C784), Component.translatable("uc7core.claimshop.marketplace.buy").getString());
        }

        if (filtered.isEmpty()) {
            String empty = Component.translatable("uc7core.claimshop.list.none").getString();
            theme.drawString(g, empty, listX + (listW - theme.getFont().width(empty)) / 2,
                    listY + (listH - lh) / 2, Color4I.rgba(0xFF888888), 0);
        }
    }

    @Override
    public void drawForeground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        int mx = (int) getMouseX(), my = (int) getMouseY();

        if (searchField != null && mx >= searchField.getX() && mx < searchField.getX() + searchField.getWidth()
                && my >= controlY && my < controlY + CONTROL_H) {
            drawTooltip(g, theme, Component.translatable("uc7core.claimshop.marketplace.search_tip"), mx, my);
        }

        { // buy view
            int cx = controlRightEdge;
            if (my >= controlY && my < controlY + CONTROL_H) {
                if (mx >= cx - STAR_W - 2 && mx < cx - 2) {
                    String favTip = showFavoritesOnly
                            ? Component.translatable("uc7core.claimshop.marketplace.fav_on").getString()
                            : Component.translatable("uc7core.claimshop.marketplace.fav_off").getString();
                    drawTooltip(g, theme, Component.literal(favTip), mx, my);
                }
                cx -= STAR_W + 6;
                for (int i = 2; i >= 0; i--) {
                    cx -= BTN_SORT_W;
                    if (mx >= cx && mx < cx + BTN_SORT_W) {
                        drawTooltip(g, theme, Component.literal(SORT_TOOLTIPS[i]), mx, my);
                        return;
                    }
                    cx -= 2;
                }
            }
        }
    }

    @Override
    public boolean mousePressed(MouseButton button) {
        if (button.isLeft()) {
            int mx = (int) getMouseX(), my = (int) getMouseY();

            { // buy view
                int cx = controlRightEdge;
                if (my >= controlY && my < controlY + CONTROL_H && mx >= cx - STAR_W - 2 && mx < cx - 2) {
                    showFavoritesOnly = !showFavoritesOnly;
                    applyFilters();
                    return true;
                }
                cx -= STAR_W + 6;

                for (int i = 2; i >= 0; i--) {
                    cx -= BTN_SORT_W;
                    if (my >= controlY && my < controlY + CONTROL_H && mx >= cx && mx < cx + BTN_SORT_W) {
                        sortMode = SortMode.values()[i];
                        applyFilters();
                        return true;
                    }
                    cx -= 2;
                }

                if (my >= listY && my < listY + listH && mx >= listX && mx < listX + listW) {
                    int idx = (my - listY) / ITEM_H + scrollOffset;
                    if (idx >= 0 && idx < filtered.size()) {
                        ChunkEntry ce = filtered.get(idx);
                        int buyX = listX + listW - BTN_BUY_W - 2;
                        int by = listY + (idx - scrollOffset) * ITEM_H + 2;
                        if (mx >= buyX && mx < buyX + BTN_BUY_W && my >= by && my < by + ITEM_H - 4) {
                            doBuy(ce);
                            return true;
                        }
                        int starX = listX + 2;
                        if (mx >= starX && mx < starX + STAR_W + 4
                                && my >= listY + (idx - scrollOffset) * ITEM_H
                                && my < listY + (idx - scrollOffset) * ITEM_H + ITEM_H) {
                            ClientClaimShopData.toggleFavorite(ce.dim(), ce.pos());
                            if (showFavoritesOnly) applyFilters();
                            return true;
                        }
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
            int maxScroll = Math.max(0, getVisibleCount() - maxVis);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.signum(scroll)));
            return true;
        }
        return super.mouseScrolled(scroll);
    }

    private void doBuy(ChunkEntry ce) {
        Minecraft mc = Minecraft.getInstance();
        if (ClientClaimShopData.isQuickbuyEnabled()) {
            PacketDistributor.sendToServer(new BuyChunkPacket(ce.pos().x, ce.pos().z));
            if (mc.player != null) mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 0.5f, 1.0f);
        } else {
            mc.setScreen(new BuyChunkConfirmScreen(ce.pos().x, ce.pos().z, ce.entry(), mc.screen));
        }
    }

    private boolean hit(int x, int y, int w, int h) {
        int mx = (int) getMouseX(), my = (int) getMouseY();
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void drawTooltip(GuiGraphics g, Theme theme, Component text, int mx, int my) {
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

        theme.drawString(g, text, tx + px, ty + py, Color4I.rgba(0xFFd5dbe6), 0);
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
