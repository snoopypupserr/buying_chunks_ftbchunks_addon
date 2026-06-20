package snoopypupser.buyingchunks.client;

import dev.ftb.mods.ftbchunks.api.client.event.MapIconEvent;
import dev.ftb.mods.ftbchunks.api.client.icon.MapIcon;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import snoopypupser.buyingchunks.claimshop.ClientClaimShopData;
import snoopypupser.buyingchunks.claimshop.ClaimShopEntry;
import snoopypupser.buyingchunks.network.BuyChunkPacket;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class ClaimShopRenderer {

    private static final dev.ftb.mods.ftblibrary.icon.Icon SHOP_ICON =
            Icons.MONEY.withColor(Color4I.rgb(0xFFD700));

    private static final int COLOR_GOLD   = 0xFFD700;
    private static final int COLOR_GREEN  = 0x55FF55;
    private static final int COLOR_GRAY   = 0xAAAAAA;
    private static final int COLOR_WHITE  = 0xFFFFFF;
    private static final int COLOR_YELLOW = 0xFFFF55;

    // Echte FTB-Farben
    private static final int COLOR_BG           = 0xFF2e3440;  // Hintergrund
    private static final int COLOR_PANEL        = 0xFF2e3440;  // Panel
    private static final int COLOR_HEADING      = 0xFFfcfc54;  // Headings (Gelb)
    private static final int COLOR_TEXT         = 0xFFd5dbe6;  // Normale Texte (Hellgrau)
    private static final int COLOR_OUTLINE_GRAY = 0xFF495366;  // 3px grauer Outline
    private static final int COLOR_OUTLINE_BLK  = 0xFF101010;  // Außerste Schwarze Outline
    private static final int COLOR_ARROW_GREEN  = 0xFF66BB6A;  // Grüner Pfeil

    public void register() {
        MapIconEvent.LARGE_MAP.register(this::onLargeMapIcons);
        NeoForge.EVENT_BUS.addListener(this::onRenderGui);
    }

    private void onRenderGui(ScreenEvent.Render.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) return;

        if (!(mc.screen instanceof dev.ftb.mods.ftblibrary.ui.ScreenWrapper wrapper)) return;
        if (!(wrapper.getGui() instanceof dev.ftb.mods.ftbchunks.client.gui.ChunkScreen chunkScreen)) return;

        ItemStack baseCost = ClientClaimShopData.getBaseCost();
        if (baseCost == null || baseCost.isEmpty()) return;

        GuiGraphics graphics = event.getGuiGraphics();

        String line1 = Component.translatable("uc7core.claimshop.basecost.display.title").getString();
        String line2 = baseCost.getCount() + "x " + baseCost.getItem().getDescription().getString();
        String line3 = Component.translatable("uc7core.claimshop.basecost.display.per_chunk").getString();

        int lineHeight = mc.font.lineHeight;
        int textWidth = Math.max(mc.font.width(line1), Math.max(mc.font.width(line2), mc.font.width(line3)));

        int padding = 6;
        int arrowWidth = 6;
        int arrowPadding = 4;

        int totalWidth  = arrowWidth + arrowPadding + textWidth + padding * 2;
        int totalHeight = lineHeight * 3 + padding * 2 + 4;

        int guiX = chunkScreen.getX();
        int guiY = chunkScreen.getY();
        int guiH = chunkScreen.height;

        int boxX = guiX - totalWidth - 10;
        int boxY = guiY + (guiH / 2) - (totalHeight / 2);

        if (boxX < 4) return;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 10000);

        int BLACK     = 0xFF000000;
        int HIGHLIGHT = 0xFF4a5368;
        int SHADOW    = 0xFF101010;
        int ACCENT    = 0xFF3d4758;

        // === Schwarze Outline mit abgerundeten Ecken (je 1px Ecke weggelassen) ===
        // top: von x+1 bis x+w-1 (Ecken frei)
        graphics.fill(boxX - 1 + 1, boxY - 1,     boxX + totalWidth,     boxY,              BLACK);
        // bottom
        graphics.fill(boxX - 1 + 1, boxY + totalHeight, boxX + totalWidth, boxY + totalHeight + 1, BLACK);
        // left
        graphics.fill(boxX - 1,     boxY - 1 + 1, boxX,                  boxY + totalHeight,      BLACK);
        // right
        graphics.fill(boxX + totalWidth, boxY - 1 + 1, boxX + totalWidth + 1, boxY + totalHeight,  BLACK);
        // Ecken bleiben leer → wirkt abgerundet

        // === Highlight oben/links (inner border, 1px) ===
        graphics.fill(boxX, boxY,     boxX + totalWidth, boxY + 1,        HIGHLIGHT); // top
        graphics.fill(boxX, boxY,     boxX + 1,          boxY + totalHeight, HIGHLIGHT); // left

        // === Shadow unten/rechts (inner border, 1px) ===
        graphics.fill(boxX,                  boxY + totalHeight - 1, boxX + totalWidth, boxY + totalHeight, SHADOW); // bottom
        graphics.fill(boxX + totalWidth - 1, boxY,                   boxX + totalWidth, boxY + totalHeight, SHADOW); // right

        // === Background fill ===
        graphics.fill(boxX + 1, boxY + 1, boxX + totalWidth - 1, boxY + totalHeight - 1, COLOR_PANEL);

        // === Subtle top accent line ===
        graphics.fill(boxX + 1, boxY + 1, boxX + totalWidth - 1, boxY + 2, ACCENT);

        // === Arrows ===
        int arrowX      = boxX + padding;
        int arrowStartY = boxY + padding;
        drawSmallArrow(graphics, arrowX, arrowStartY,                      COLOR_ARROW_GREEN);
        drawSmallArrow(graphics, arrowX, arrowStartY + lineHeight + 2,     COLOR_ARROW_GREEN);
        drawSmallArrow(graphics, arrowX, arrowStartY + lineHeight * 2 + 4, COLOR_ARROW_GREEN);

        // === Text ===
        int textStartX = boxX + arrowWidth + arrowPadding + padding;
        int textStartY = boxY + padding;

        graphics.drawString(mc.font, line1, textStartX, textStartY,                       COLOR_HEADING, false);
        graphics.drawString(mc.font, line2, textStartX, textStartY + lineHeight + 2,      COLOR_TEXT,    false);
        graphics.drawString(mc.font, line3, textStartX, textStartY + lineHeight * 2 + 4,  COLOR_TEXT,    false);

        graphics.pose().popPose();
    }
    
    private void drawSmallArrow(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y + 1, x + 3, y + 2, color);
        graphics.fill(x + 1, y + 2, x + 4, y + 3, color);
        graphics.fill(x + 2, y + 3, x + 5, y + 4, color);
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

    private void onLargeMapIcons(MapIconEvent event) {
        ResourceKey<Level> dimKey = event.getDimension();
        for (Map.Entry<ChunkPos, ClaimShopEntry> entry : ClientClaimShopData.getAllForDimension(dimKey.location()).entrySet()) {
            ChunkPos pos = entry.getKey();
            ClaimShopEntry shopEntry = entry.getValue();

            Vec3 iconPos = new Vec3((pos.x + 0.5) * 16.0, 64, (pos.z + 0.5) * 16.0);

            final int chunkX = pos.x;
            final int chunkZ = pos.z;
            final ResourceLocation dim = dimKey.location();

            event.add(new MapIcon.SimpleMapIcon(iconPos, SHOP_ICON) {
                @Override
                public void addTooltip(TooltipList list) {
                    Minecraft mc = Minecraft.getInstance();

                    list.add(Component.literal("⭐ ")
                            .withStyle(Style.EMPTY.withColor(COLOR_GOLD))
                            .append(Component.translatable("uc7core.claimshop.tooltip.title")
                                    .withStyle(Style.EMPTY.withColor(COLOR_GOLD).withBold(true))));

                    list.add(Component.literal("   ")
                            .append(Component.literal(shopEntry.getShopTeamName())
                                    .withStyle(Style.EMPTY.withColor(shopEntry.getTeamColor() & 0xFFFFFF))));

                    list.add(Component.translatable("uc7core.claimshop.tooltip.divider")
                            .withStyle(Style.EMPTY.withColor(COLOR_GRAY)));

                    list.add(Component.literal("📍 ")
                            .withStyle(Style.EMPTY.withColor(COLOR_GRAY))
                            .append(Component.translatable("uc7core.claimshop.tooltip.position",
                                    Component.literal(chunkX + ", " + chunkZ)
                                            .withStyle(Style.EMPTY.withColor(COLOR_GRAY)))));

                    list.add(Component.literal("💰 ")
                            .withStyle(Style.EMPTY.withColor(COLOR_GREEN))
                            .append(Component.translatable("uc7core.claimshop.tooltip.price",
                                    Component.literal(shopEntry.getPrice().getCount() + "x ")
                                            .withStyle(Style.EMPTY.withColor(COLOR_GREEN).withBold(true)),
                                    Component.literal(shopEntry.getPrice().getItem().getDescription().getString())
                                            .withStyle(Style.EMPTY.withColor(COLOR_GREEN)))));

                    if (mc.player != null) {
                        int count = countItems(mc.player, shopEntry.getPrice());
                        int needed = shopEntry.getPrice().getCount();
                        int invColor = count >= needed ? COLOR_GREEN : 0xFFE53935;
                        list.add(Component.literal("📦 ")
                                .withStyle(Style.EMPTY.withColor(COLOR_GRAY))
                                .append(Component.translatable("uc7core.claimshop.tooltip.inventory",
                                        Component.literal(String.valueOf(count))
                                                .withStyle(Style.EMPTY.withColor(invColor).withBold(true)),
                                        Component.literal(shopEntry.getPrice().getItem().getDescription().getString())
                                                .withStyle(Style.EMPTY.withColor(invColor)))));
                    }

                    list.add(Component.translatable("uc7core.claimshop.tooltip.divider")
                            .withStyle(Style.EMPTY.withColor(COLOR_GRAY)));

                    list.add(Component.literal("[ ")
                            .withStyle(Style.EMPTY.withColor(COLOR_GRAY))
                            .append(Component.translatable("uc7core.claimshop.tooltip.click")
                                    .withStyle(Style.EMPTY.withColor(COLOR_YELLOW).withBold(true)))
                            .append(Component.literal(" ]")
                                    .withStyle(Style.EMPTY.withColor(COLOR_GRAY))));
                }

                @Override
                public boolean onMousePressed(BaseScreen screen, MouseButton button) {
                    if (button.isLeft()) {
                        Minecraft mc = Minecraft.getInstance();
                        ClaimShopEntry entry = ClientClaimShopData.getEntry(dim, new ChunkPos(chunkX, chunkZ));
                        if (entry != null) {
                            mc.setScreen(new BuyChunkConfirmScreen(chunkX, chunkZ, entry, mc.screen));
                        }
                        return true;
                    }
                    return false;
                }

                @Override
                public int getPriority() {
                    return 100;
                }
            });
        }
    }
}