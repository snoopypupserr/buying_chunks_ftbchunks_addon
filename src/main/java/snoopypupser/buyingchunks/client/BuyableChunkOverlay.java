package snoopypupser.buyingchunks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.lwjgl.glfw.GLFW;
import snoopypupser.buyingchunks.claimshop.ClaimShopEntry;
import snoopypupser.buyingchunks.claimshop.ClientClaimShopData;
import snoopypupser.buyingchunks.network.BuyChunkPacket;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public class BuyableChunkOverlay {

    private static final int PANEL_W = 130;
    private static final int PAD = 6;
    private static final int BTN_H = 12;
    private static final int CLOSE_SIZE = 10;

    private static final int COL_PANEL_BG = 0xCC2e3440;
    private static final int COL_OUTLINE_BLK = 0xFF101010;
    private static final int COL_HIGHLIGHT = 0xFF4a5368;
    private static final int COL_SHADOW = 0xFF1a1f2a;
    private static final int COL_ACCENT = 0xFF3d4758;
    private static final int COL_TITLE = 0xFFfcfc54;
    private static final int COL_PRICE = 0xFF81C784;
    private static final int COL_BTN_BG = 0xFF1a3d16;
    private static final int COL_BTN_HOVER = 0xFF245220;
    private static final int COL_BTN_BORDER = 0xFF4CAF50;
    private static final int COL_BTN_TEXT = 0xFF81C784;
    private static final int COL_BTN_DISABLED_BG = 0xFF3a3a3a;
    private static final int COL_BTN_DISABLED_HOVER = 0xFF4a4a4a;
    private static final int COL_BTN_DISABLED_BORDER = 0xFF555555;
    private static final int COL_BTN_DISABLED_TEXT = 0xFF888888;
    private static final int COL_HINT = 0xFF888888;
    private static final int COL_ERROR = 0xFFE53935;
    private static final int COL_CLOSE = 0xFF888888;

    private static ChunkPos currentChunk;
    private static ClaimShopEntry currentEntry;

    private static int panelX, panelY;
    private static int btnX, btnY, btnW;
    private static int closeBtnX, closeBtnY;

    private static long lastClickTime = 0;
    private static final long CLICK_COOLDOWN_MS = 500;

    private static final Set<ChunkPos> dismissedChunks = new HashSet<>();

    public void register() {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) return;

        ChunkPos chunkPos = new ChunkPos(player.blockPosition());
        ResourceLocation dimension = player.level().dimension().location();

        if (chunkPos.equals(currentChunk)) {
            if (currentChunk != null) {
                if (!ClientClaimShopData.isForSale(dimension, currentChunk)) {
                    currentChunk = null;
                    currentEntry = null;
                }
            }
            return;
        }

        if (dismissedChunks.contains(chunkPos)) {
            if (currentChunk != null) {
                currentChunk = null;
                currentEntry = null;
            }
            return;
        }

        if (ClientClaimShopData.isForSale(dimension, chunkPos)) {
            dismissedChunks.clear();
            currentChunk = chunkPos;
            currentEntry = ClientClaimShopData.getEntry(dimension, chunkPos);
        } else {
            dismissedChunks.clear();
            currentChunk = null;
            currentEntry = null;
        }
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        if (!isActive()) return;
        if (ModKeyMappings.BUY_KEY.consumeClick()) {
            if (System.currentTimeMillis() - lastClickTime < CLICK_COOLDOWN_MS) return;
            lastClickTime = System.currentTimeMillis();
            Minecraft mc = Minecraft.getInstance();
            if (ClientClaimShopData.isQuickbuyEnabled()) {
                PacketDistributor.sendToServer(new BuyChunkPacket(currentChunk.x, currentChunk.z));
                currentChunk = null;
                currentEntry = null;
            } else {
                mc.setScreen(new BuyChunkConfirmScreen(currentChunk.x, currentChunk.z, currentEntry, mc.screen));
            }
        }
    }

    @SubscribeEvent
    public void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_1) return;
        if (!isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();

        if (handleClick(mx, my)) {
            event.setCanceled(true);
        }
    }

    public static boolean isActive() {
        return currentChunk != null && currentEntry != null;
    }

    private static final int ICON_W = 18;

    public static void render(GuiGraphics g) {
        if (!isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        ItemStack price = currentEntry.getPrice();

        boolean canAfford = true;
        if (mc.player != null) {
            canAfford = countItems(mc.player, price) >= price.getCount();
        }

        String title = "Chunk [" + currentChunk.x + ", " + currentChunk.z + "]";
        String priceStr = price.getCount() + "x " + price.getItem().getDescription().getString();
        String shopName = currentEntry.getShopTeamName();
        String hint = ModKeyMappings.BUY_KEY.getTranslatedKeyMessage().getString() + " "
                + Component.translatable("uc7core.claimshop.overlay.hint").getString();

        int lh = mc.font.lineHeight;
        int iconRowH = Math.max(16, lh);

        int panelH = PAD + lh + 3 + 1 + 3 + iconRowH + 2 + lh + 4 + BTN_H + 2 + lh + PAD;

        int priceLineWidth = ICON_W + mc.font.width(priceStr);
        int maxTextW = Math.max(mc.font.width(title),
                Math.max(priceLineWidth,
                        Math.max(mc.font.width(shopName),
                                mc.font.width(hint))));
        int panelW = Math.max(PANEL_W, maxTextW + PAD * 2);

        panelX = sw - panelW - PAD;
        panelY = sh - panelH - PAD;
        btnW = panelW - PAD * 2;
        closeBtnX = panelX + panelW - PAD - mc.font.width("\u2715") + 2;
        closeBtnY = panelY + PAD;

        drawFtbPanel(g, panelX, panelY, panelW, panelH);

        int x = panelX + PAD;
        int y = panelY + PAD;

        g.drawString(mc.font, title, x, y, COL_TITLE, false);
        g.drawString(mc.font, "\u2715", closeBtnX, closeBtnY, COL_CLOSE, false);
        y += lh + 3;

        g.fill(x, y, panelX + panelW - PAD, y + 1, COL_HIGHLIGHT);
        y += 4;

        int iconColor = canAfford ? COL_PRICE : COL_HINT;
        g.renderItem(price, x, y);
        g.drawString(mc.font, priceStr, x + ICON_W, y + (iconRowH - lh) / 2, iconColor, false);
        y += iconRowH + 2;

        int teamColor = currentEntry.getTeamColor();
        g.drawString(mc.font, shopName, x, y, teamColor, false);
        y += lh + 4;

        btnX = x;
        btnY = y;
        int mx = (int) (mc.mouseHandler.xpos() * sw / mc.getWindow().getScreenWidth());
        int my = (int) (mc.mouseHandler.ypos() * sh / mc.getWindow().getScreenHeight());
        boolean hover = mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + BTN_H;

        String buyLabel = canAfford
                ? Component.translatable("uc7core.claimshop.overlay.buy").getString()
                : Component.translatable("uc7core.claimshop.overlay.cantafford").getString();

        int btnBg, btnBorder, btnTextCol;
        if (canAfford) {
            btnBg = hover ? COL_BTN_HOVER : COL_BTN_BG;
            btnBorder = COL_BTN_BORDER;
            btnTextCol = COL_BTN_TEXT;
        } else {
            btnBg = hover ? COL_BTN_DISABLED_HOVER : COL_BTN_DISABLED_BG;
            btnBorder = COL_BTN_DISABLED_BORDER;
            btnTextCol = COL_BTN_DISABLED_TEXT;
        }

        drawFtbButton(g, btnX, btnY, btnW, BTN_H, btnBg, btnBorder, btnTextCol, buyLabel);
        y += BTN_H + 2;

        g.drawString(mc.font, hint, x, y, COL_HINT, false);
    }

    public static boolean handleClick(double mouseX, double mouseY) {
        if (!isActive()) return false;

        if (mouseX >= closeBtnX - 2 && mouseX < closeBtnX + CLOSE_SIZE
                && mouseY >= closeBtnY - 2 && mouseY < closeBtnY + CLOSE_SIZE) {
            if (currentChunk != null) dismissedChunks.add(currentChunk);
            currentChunk = null;
            currentEntry = null;
            return true;
        }

        if (mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + BTN_H) {
            if (System.currentTimeMillis() - lastClickTime < CLICK_COOLDOWN_MS) return true;
            lastClickTime = System.currentTimeMillis();
            Minecraft mc = Minecraft.getInstance();
            if (ClientClaimShopData.isQuickbuyEnabled()) {
                PacketDistributor.sendToServer(new BuyChunkPacket(currentChunk.x, currentChunk.z));
                currentChunk = null;
                currentEntry = null;
            } else {
                mc.setScreen(new BuyChunkConfirmScreen(currentChunk.x, currentChunk.z, currentEntry, mc.screen));
            }
            return true;
        }
        return false;
    }

    private static int countItems(net.minecraft.world.entity.player.Player player, ItemStack required) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (ItemStack.isSameItem(stack, required)) {
                count += stack.getCount();
                if (count >= required.getCount()) break;
            }
        }
        return count;
    }

    private static void drawFtbPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x,         y - 1,     x + w,     y,         COL_OUTLINE_BLK);
        g.fill(x,         y + h,     x + w,     y + h + 1, COL_OUTLINE_BLK);
        g.fill(x - 1,     y,         x,         y + h,     COL_OUTLINE_BLK);
        g.fill(x + w,     y,         x + w + 1, y + h,     COL_OUTLINE_BLK);

        g.fill(x - 1, y - 1, x,     y,         COL_OUTLINE_BLK);
        g.fill(x + w, y - 1, x + w + 1, y,     COL_OUTLINE_BLK);
        g.fill(x - 1, y + h, x,     y + h + 1, COL_OUTLINE_BLK);
        g.fill(x + w, y + h, x + w + 1, y + h + 1, COL_OUTLINE_BLK);

        g.fill(x,         y,         x + w,     y + 1,     COL_HIGHLIGHT);
        g.fill(x,         y,         x + 1,     y + h,     COL_HIGHLIGHT);

        g.fill(x + 1,     y + h - 1, x + w,     y + h,     COL_SHADOW);
        g.fill(x + w - 1, y + 1,     x + w,     y + h,     COL_SHADOW);

        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, COL_PANEL_BG);
        g.fill(x + 1, y + 1, x + w - 1, y + 3,     COL_ACCENT);
    }

    private static void drawFtbButton(GuiGraphics g, int x, int y, int w, int h,
                                      int bg, int border, int textColor, String label) {
        g.fill(x - 1, y - 1, x,         y,         COL_OUTLINE_BLK);
        g.fill(x + w, y - 1, x + w + 1, y,         COL_OUTLINE_BLK);
        g.fill(x - 1, y + h, x,         y + h + 1, COL_OUTLINE_BLK);
        g.fill(x + w, y + h, x + w + 1, y + h + 1, COL_OUTLINE_BLK);

        g.fill(x,         y - 1,     x + w,     y,         COL_OUTLINE_BLK);
        g.fill(x,         y + h,     x + w,     y + h + 1, COL_OUTLINE_BLK);
        g.fill(x - 1,     y,         x,         y + h,     COL_OUTLINE_BLK);
        g.fill(x + w,     y,         x + w + 1, y + h,     COL_OUTLINE_BLK);

        g.fill(x,         y,         x + w,     y + 1,     border);
        g.fill(x,         y,         x + 1,     y + h,     border);
        g.fill(x,         y + h - 1, x + w,     y + h,     COL_OUTLINE_BLK);
        g.fill(x + w - 1, y,         x + w,     y + h,     COL_OUTLINE_BLK);

        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);

        Minecraft mc = Minecraft.getInstance();
        int tw = mc.font.width(label);
        int tx = x + (w - tw) / 2;
        int ty = y + (h - mc.font.lineHeight) / 2;
        g.drawString(mc.font, label, tx, ty, textColor, false);
    }
}
