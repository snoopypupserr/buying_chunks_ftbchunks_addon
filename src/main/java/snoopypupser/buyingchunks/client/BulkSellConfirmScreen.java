package snoopypupser.buyingchunks.client;

import dev.ftb.mods.ftblibrary.config.ItemStackConfig;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.*;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class BulkSellConfirmScreen extends BaseAdminScreen {

    private static final int BTN_W = 80, PICKER_W = 18;

    private final List<ChunkPos> chunksToSell;
    private final Runnable onClose;

    private int sellX, sellY, cancelX, cancelY;
    private int itemLabelY, itemFieldY, amtLabelY, amtFieldY;
    private int pickerX, pickerY;

    private TextBox itemField, amountField;
    private String pendingItemId = "";
    private String pendingAmount = "1";

    public BulkSellConfirmScreen(List<ChunkPos> chunks, Runnable onClose) {
        this.chunksToSell = chunks;
        this.onClose = onClose;
    }

    @Override
    public boolean onInit() {
        setFullscreen();
        Minecraft mc = Minecraft.getInstance();
        int lh = mc.font.lineHeight;

        String headerStr = Component.translatable("uc7core.claimshop.bulksell.confirm_header", chunksToSell.size()).getString();
        int cw = Math.max(mc.font.width(headerStr),
                mc.font.width(Component.translatable("uc7core.claimshop.admin.item").getString()));
        cw = Math.max(cw, BTN_W * 2 + 20);
        bw = cw + PAD * 2 + 10;
        bh = PAD + BTN_H + GAP_BACK_TITLE + lh + GAP_TITLE_DIVIDER + 1 + GAP_DIVIDER_CONTENT
                + lh + 4
                + GAP_LABEL_FIELD + FIELD_H + GAP_FIELD_NEXT
                + lh + GAP_LABEL_FIELD + FIELD_H + GAP_CONTENT_BTN + BTN_H + PAD;
        computeTopSection(lh);

        int y = contentY;
        y += lh + 4; itemLabelY = y;
        y += lh + GAP_LABEL_FIELD; itemFieldY = y;
        y += FIELD_H + GAP_FIELD_NEXT; amtLabelY = y;
        y += lh + GAP_LABEL_FIELD; amtFieldY = y;
        y += FIELD_H + GAP_CONTENT_BTN;
        int cx = bx + bw / 2;
        sellX = cx - BTN_W - 10; sellY = y;
        cancelX = cx + 10; cancelY = y;
        return true;
    }

    @Override
    public void addWidgets() {
        int x = bx + PAD + 2, fw = bw - PAD * 2 - 4;
        int itemW = fw - PICKER_W - 2;

        itemField = new TextBox(this);
        itemField.setPosAndSize(x, itemFieldY, itemW, FIELD_H);
        itemField.setMaxLength(64);
        itemField.textColor = COL_TEXT;
        itemField.setText(pendingItemId);
        add(itemField);
        pickerX = x + itemW + 2; pickerY = itemFieldY;

        amountField = new TextBox(this);
        amountField.setPosAndSize(x, amtFieldY, fw / 3, FIELD_H);
        amountField.setMaxLength(3);
        amountField.setText(pendingAmount);
        amountField.setFilter(s -> s.matches("\\d*"));
        amountField.textColor = COL_TEXT;
        add(amountField);
    }

    @Override
    public void alignWidgets() {}

    @Override
    public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        g.fill(0, 0, getWidth(), getHeight(), 0x88000000);
        drawPanel(g, bx, by, bw, bh);
        drawTopSection(g, theme);

        int ix = bx + PAD + 2;

        String header = Component.translatable("uc7core.claimshop.bulksell.confirm_header", chunksToSell.size()).getString();
        theme.drawString(g, header, ix, contentY, COL_HEADING, 0);

        theme.drawString(g, Component.translatable("uc7core.claimshop.admin.item").getString(), ix, itemLabelY, COL_TEXT, 0);
        theme.drawString(g, Component.translatable("uc7core.claimshop.admin.amount").getString(), ix, amtLabelY, COL_TEXT, 0);

        boolean hasItem = !pendingItemId.isEmpty();
        drawBtn(g, theme, sellX, sellY, BTN_W, BTN_H,
                hasItem ? 0xFF1a3d16 : 0xFF2a2a2a,
                hasItem ? 0xFF4CAF50 : 0xFF555555,
                hasItem ? COL_GREEN_T : COL_HINT,
                Component.translatable("uc7core.claimshop.bulksell.sell_btn").getString());

        drawBtn(g, theme, cancelX, cancelY, BTN_W, BTN_H, 0xFF3d1616, 0xFFE53935, COL_RED_T,
                Component.translatable("uc7core.claimshop.confirm.cancel").getString());

        boolean overPick = hit((int) getMouseX(), (int) getMouseY(), pickerX, pickerY, PICKER_W, FIELD_H);
        int pickBg = overPick ? 0xFF3a5a6a : 0xFF2a3d4a;
        drawBtn(g, theme, pickerX, pickerY, PICKER_W, FIELD_H, pickBg, pickBg, COL_TEXT, "\uD83D\uDD0D");
    }

    @Override
    public void drawForeground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        int mx = (int) getMouseX(), my = (int) getMouseY();
        if (hit(backX, backY, BACK_W, BTN_H))
            drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.back.tip"), mx, my);
        if (hit(sellX, sellY, BTN_W, BTN_H))
            drawTooltip(g, theme, Component.translatable("uc7core.claimshop.bulksell.sell_btn.tip"), mx, my);
        if (hit(cancelX, cancelY, BTN_W, BTN_H))
            drawTooltip(g, theme, Component.translatable("uc7core.claimshop.confirm.cancel"), mx, my);
        if (itemField != null && hit(itemField.getX(), itemFieldY, itemField.getWidth(), FIELD_H))
            drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.item_field.tip"), mx, my);
        if (amountField != null && hit(amountField.getX(), amtFieldY, amountField.getWidth(), FIELD_H))
            drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.amount_field.tip"), mx, my);
        if (hit(pickerX, pickerY, PICKER_W, FIELD_H))
            drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.picker.tip"), mx, my);
    }

    @Override
    public boolean mousePressed(MouseButton button) {
        if (button.isLeft()) {
            if (hit(backX, backY, BACK_W, BTN_H)) { doCancel(); return true; }
            if (hit(sellX, sellY, BTN_W, BTN_H)) { doSell(); return true; }
            if (hit(cancelX, cancelY, BTN_W, BTN_H)) { doCancel(); return true; }
            if (hit(pickerX, pickerY, PICKER_W, FIELD_H)) { openItemPicker(); return true; }
        }
        return super.mousePressed(button);
    }

    @Override
    protected String getTitleStr() {
        return Component.translatable("uc7core.claimshop.bulksell.title").getString();
    }

    private void doSell() {
        String id = itemField.getText().trim();
        if (id.isEmpty()) return;
        int a;
        try { a = Integer.parseInt(amountField.getText().trim()); if (a < 1) a = 1; if (a > 64) a = 64; }
        catch (NumberFormatException e) { a = 1; }

        PacketDistributor.sendToServer(new snoopypupser.buyingchunks.network.BulkSellChunksPacket(id, a, chunksToSell));

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 0.5f, 1.0f);
        }
        closeGui(false);
        onClose.run();
    }

    private void doCancel() {
        closeGui(false);
        onClose.run();
    }

    private void openItemPicker() {
        ItemStackConfig cfg = new ItemStackConfig(true, true);
        pendingItemId = itemField.getText().trim();
        pendingAmount = amountField.getText().trim();
        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(pendingItemId));
            if (!pendingItemId.isEmpty() && item != Items.AIR) {
                int cnt = 1;
                try { cnt = Math.max(1, Math.min(64, Integer.parseInt(pendingAmount))); } catch (Exception ignored) {}
                cfg.setValue(new ItemStack(item, cnt));
            }
        } catch (Exception ignored) {}
        cfg.onClicked(this, MouseButton.LEFT, saved -> {
            if (saved) {
                ItemStack stack = cfg.getValue();
                if (stack != null && !stack.isEmpty()) {
                    pendingItemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    pendingAmount = String.valueOf(stack.getCount());
                }
            }
            Minecraft.getInstance().tell(this::openGui);
        });
    }
}
