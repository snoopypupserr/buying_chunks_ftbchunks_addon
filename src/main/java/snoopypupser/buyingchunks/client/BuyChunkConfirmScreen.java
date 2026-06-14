package snoopypupser.buyingchunks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import snoopypupser.buyingchunks.claimshop.ClientClaimShopData;
import snoopypupser.buyingchunks.claimshop.ClaimShopEntry;
import snoopypupser.buyingchunks.network.BuyChunkPacket;

public class BuyChunkConfirmScreen extends Screen {

    private static final int COLOR_PANEL         = 0xFF2e3440;
    private static final int COLOR_HEADING       = 0xFFfcfc54;
    private static final int COLOR_TEXT          = 0xFFd5dbe6;
    private static final int COLOR_OUTLINE_GRAY  = 0xFF495366;
    private static final int COLOR_OUTLINE_BLK   = 0xFF101010;
    private static final int COLOR_HIGHLIGHT     = 0xFF4a5368;
    private static final int COLOR_ACCENT        = 0xFF3d4758;

    private static final int COLOR_BTN_YES_BG     = 0xFF1a3d16;
    private static final int COLOR_BTN_YES_HOVER  = 0xFF245220;
    private static final int COLOR_BTN_YES_BORDER = 0xFF4CAF50;
    private static final int COLOR_BTN_YES_TEXT   = 0xFF81C784;
    private static final int COLOR_BTN_NO_BG      = 0xFF3d1616;
    private static final int COLOR_BTN_NO_HOVER   = 0xFF521f1f;
    private static final int COLOR_BTN_NO_BORDER  = 0xFFE53935;
    private static final int COLOR_BTN_NO_TEXT    = 0xFFEF9A9A;

    private final int chunkX;
    private final int chunkZ;
    private final ClaimShopEntry entry;
    private final Screen previousScreen;

    private int boxX, boxY, boxW, boxH;
    private int btnYesX, btnYesY, btnNoX, btnNoY;
    private int BTN_W;
    private static final int BTN_H = 14;
    private static final int BTN_PADDING_X = 10;



    public BuyChunkConfirmScreen(int chunkX, int chunkZ, ClaimShopEntry entry, Screen previousScreen) {
        super(Component.empty());
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.entry = entry;
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        int lineHeight = mc.font.lineHeight;

        String title   = Component.translatable("uc7core.claimshop.confirm.title").getString();
        String line1   = Component.translatable("uc7core.claimshop.confirm.chunk", chunkX, chunkZ).getString();
        String line2   = Component.translatable("uc7core.claimshop.confirm.price",
                entry.getPrice().getCount(),
                entry.getPrice().getItem().getDescription().getString()).getString();
        String line3   = Component.translatable("uc7core.claimshop.confirm.question").getString();
        String btnYes  = Component.translatable("uc7core.claimshop.confirm.buy").getString();
        String btnNo   = Component.translatable("uc7core.claimshop.confirm.cancel").getString();

        // Button-Breite = längster Button-Text + Padding
        BTN_W = Math.max(mc.font.width(btnYes), mc.font.width(btnNo)) + BTN_PADDING_X * 2;

        int textWidth = Math.max(mc.font.width(title),
                Math.max(mc.font.width(line1),
                        Math.max(mc.font.width(line2),
                                mc.font.width(line3))));
        // Panel muss mindestens beide Buttons + Gap enthalten
        textWidth = Math.max(textWidth, BTN_W * 2 + 10);

        int padding = 8;
        boxW = textWidth + padding * 2;
        boxH = lineHeight * 4 + padding * 2 + 6 + BTN_H + 6;

        boxX = (this.width  - boxW) / 2;
        boxY = (this.height - boxH) / 2;

        int btnY   = boxY + boxH - BTN_H - padding;
        int center = boxX + boxW / 2;
        btnYesX = center - BTN_W - 5;
        btnNoX  = center + 5;
        btnYesY = btnY;
        btnNoY  = btnY;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0x88000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);

        Minecraft mc = Minecraft.getInstance();
        int lh  = mc.font.lineHeight;
        int pad = 8;

        drawFtbPanel(g, boxX, boxY, boxW, boxH);

        int textX = boxX + pad;
        int textY = boxY + pad;

        g.drawString(mc.font,
                Component.translatable("uc7core.claimshop.confirm.title").getString(),
                textX, textY, COLOR_HEADING, false);

        g.fill(boxX + pad, textY + lh + 2, boxX + boxW - pad, textY + lh + 3, COLOR_OUTLINE_GRAY);

        int infoY = textY + lh + 6;
        g.drawString(mc.font,
                Component.translatable("uc7core.claimshop.confirm.chunk", chunkX, chunkZ).getString(),
                textX, infoY, COLOR_TEXT, false);
        g.drawString(mc.font,
                Component.translatable("uc7core.claimshop.confirm.price",
                        entry.getPrice().getCount(),
                        entry.getPrice().getItem().getDescription().getString()).getString(),
                textX, infoY + lh + 2, COLOR_TEXT, false);
        g.drawString(mc.font,
                Component.translatable("uc7core.claimshop.confirm.question").getString(),
                textX, infoY + lh * 2 + 4, COLOR_HEADING, false);

        boolean hoverYes = isHovering(mouseX, mouseY, btnYesX, btnYesY, BTN_W, BTN_H);
        boolean hoverNo  = isHovering(mouseX, mouseY, btnNoX,  btnNoY,  BTN_W, BTN_H);

        drawFtbButton(g, btnYesX, btnYesY, BTN_W, BTN_H,
                hoverYes ? COLOR_BTN_YES_HOVER : COLOR_BTN_YES_BG,
                COLOR_BTN_YES_BORDER, COLOR_BTN_YES_TEXT,
                Component.translatable("uc7core.claimshop.confirm.buy").getString());

        drawFtbButton(g, btnNoX, btnNoY, BTN_W, BTN_H,
                hoverNo ? COLOR_BTN_NO_HOVER : COLOR_BTN_NO_BG,
                COLOR_BTN_NO_BORDER, COLOR_BTN_NO_TEXT,
                Component.translatable("uc7core.claimshop.confirm.cancel").getString());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (isHovering((int) mouseX, (int) mouseY, btnYesX, btnYesY, BTN_W, BTN_H)) {
                PacketDistributor.sendToServer(new BuyChunkPacket(chunkX, chunkZ));
                Minecraft mc = Minecraft.getInstance();
                Screen mapScreen = previousScreen;
                ClientClaimShopData.setOnUpdateCallback(() ->
                        mc.execute(() -> {
                            mc.setScreen(null);
                            mc.tell(() -> mc.setScreen(mapScreen));
                        })
                );
                mc.setScreen(previousScreen);
                return true;
            }
            if (isHovering((int) mouseX, (int) mouseY, btnNoX, btnNoY, BTN_W, BTN_H)) {
                Minecraft.getInstance().setScreen(previousScreen);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            Minecraft.getInstance().setScreen(previousScreen);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean isHovering(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void drawFtbPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x,         y - 1,     x + w,     y,         COLOR_OUTLINE_BLK);
        g.fill(x,         y + h,     x + w,     y + h + 1, COLOR_OUTLINE_BLK);
        g.fill(x - 1,     y,         x,         y + h,     COLOR_OUTLINE_BLK);
        g.fill(x + w,     y,         x + w + 1, y + h,     COLOR_OUTLINE_BLK);

        g.fill(x,         y,         x + w - 1, y + 1,     COLOR_HIGHLIGHT);
        g.fill(x,         y,         x + 1,     y + h - 1, COLOR_HIGHLIGHT);

        g.fill(x + 1,     y + h - 1, x + w,     y + h,     0xFF1a1f2a);
        g.fill(x + w - 1, y + 1,     x + w,     y + h,     0xFF1a1f2a);

        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, COLOR_PANEL);
        g.fill(x + 1, y + 1, x + w - 1, y + 2,     COLOR_ACCENT);
    }

    private void drawFtbButton(GuiGraphics g, int x, int y, int w, int h,
                               int bgColor, int borderColor, int textColor, String label) {
        g.fill(x,         y - 1,     x + w,     y,         COLOR_OUTLINE_BLK);
        g.fill(x,         y + h,     x + w,     y + h + 1, COLOR_OUTLINE_BLK);
        g.fill(x - 1,     y,         x,         y + h,     COLOR_OUTLINE_BLK);
        g.fill(x + w,     y,         x + w + 1, y + h,     COLOR_OUTLINE_BLK);

        g.fill(x,         y,         x + w,     y + 1,     borderColor);
        g.fill(x,         y,         x + 1,     y + h,     borderColor);
        g.fill(x,         y + h - 1, x + w,     y + h,     0xFF101010);
        g.fill(x + w - 1, y,         x + w,     y + h,     0xFF101010);

        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bgColor);

        Minecraft mc = Minecraft.getInstance();
        int textW = mc.font.width(label);
        int textX = x + (w - textW) / 2;
        int textY = y + (h - mc.font.lineHeight) / 2 + 1;
        g.drawString(mc.font, label, textX, textY, textColor, false);
    }
}