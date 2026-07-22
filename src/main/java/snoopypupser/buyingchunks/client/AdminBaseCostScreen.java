package snoopypupser.buyingchunks.client;

import dev.ftb.mods.ftblibrary.config.ItemStackConfig;
import dev.ftb.mods.ftblibrary.ui.*;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import snoopypupser.buyingchunks.BuyingChunks;
import snoopypupser.buyingchunks.claimshop.ClientClaimShopData;
import snoopypupser.buyingchunks.network.AdminActionPacket;


public class AdminBaseCostScreen extends BaseAdminScreen {

    private static final int BTN_W = 64, PICKER_W = 18;
    private static final ResourceLocation[] DIMENSIONS = {
            ResourceLocation.parse("minecraft:overworld"),
            ResourceLocation.parse("minecraft:the_nether"),
            ResourceLocation.parse("minecraft:the_end")
    };
    private static final String[] DIM_NAMES = {"Overworld", "Nether", "End"};

    private final BaseScreen previous;
    private int curY, setX, setY, remX, remY;
    private int itemLabelY, itemFieldY, amtLabelY, amtFieldY, hintY;
    private int pickerX, pickerY;
    private TextBox itemField, amountField;
    private String pendingItemId = "";
    private String pendingAmount = "1";

    private int selectedTab = 0;
    private int tabY, tabH;
    private final int[] tabX = new int[3];
    private int[] tabW = new int[3];

    public AdminBaseCostScreen(BaseScreen previous) { this.previous = previous; }

    private ResourceLocation selectedDimension() {
        return DIMENSIONS[selectedTab];
    }

    @Override
    public boolean onInit() {
        setFullscreen();
        Minecraft mc = Minecraft.getInstance();
        int lh = mc.font.lineHeight;
        int cw = Math.max(mc.font.width(Component.translatable("uc7core.claimshop.admin.basecost").getString()),
                mc.font.width(Component.translatable("uc7core.claimshop.admin.basecost.item_hint").getString()));
        cw = Math.max(cw, BTN_W * 2 + 10); cw = Math.max(cw, 180);
        bw = cw + PAD * 2 + 10;
        bh = PAD + BTN_H + GAP_BACK_TITLE + lh + GAP_TITLE_DIVIDER + 1 + GAP_DIVIDER_CONTENT
                + lh + 4 + lh + 8 + lh + GAP_LABEL_FIELD + FIELD_H + GAP_FIELD_NEXT
                + lh + GAP_LABEL_FIELD + FIELD_H + 8 + lh + GAP_CONTENT_BTN + BTN_H + PAD;
        computeTopSection(lh);

        tabH = lh + 6;
        tabY = contentY;

        int y = tabY + tabH + 4;
        curY = y;
        y += lh + 6; itemLabelY = y;
        y += lh + GAP_LABEL_FIELD; itemFieldY = y;
        y += FIELD_H + GAP_FIELD_NEXT; amtLabelY = y;
        y += lh + GAP_LABEL_FIELD; amtFieldY = y;
        y += FIELD_H + 6; hintY = y;
        y += lh + GAP_CONTENT_BTN;
        int cx = bx + bw / 2;
        setX = cx - BTN_W - 5; setY = y;
        remX = cx + 5; remY = y;
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
        int mx = (int) getMouseX(), my = (int) getMouseY();
        g.fill(0, 0, getWidth(), getHeight(), 0x88000000);
        drawPanel(g, bx, by, bw, bh);
        drawTopSection(g, theme);

        int tabContentW = bw - PAD * 2;
        int singleTabW = tabContentW / 3;
        for (int i = 0; i < 3; i++) {
            tabW[i] = singleTabW;
            tabX[i] = bx + PAD + i * singleTabW;
        }

        for (int i = 0; i < 3; i++) {
            boolean selected = (i == selectedTab);
            boolean hover = hit(mx, my, tabX[i], tabY, tabW[i], tabH);
            int bg = selected ? 0xFF2a4a2a : (hover ? 0xFF2a3a4a : 0xFF1a2a3a);
            int border = selected ? 0xFF4CAF50 : 0xFF555555;
            g.fill(tabX[i], tabY, tabX[i] + tabW[i], tabY + tabH, bg);
            g.fill(tabX[i], tabY + tabH - 1, tabX[i] + tabW[i], tabY + tabH, border);
            String dimName = DIM_NAMES[i];
            int textW = theme.getFont().width(dimName);
            dev.ftb.mods.ftblibrary.icon.Color4I textColor = selected ? dev.ftb.mods.ftblibrary.icon.Color4I.rgb(0x4CAF50) : dev.ftb.mods.ftblibrary.icon.Color4I.rgb(0xAAAAAA);
            theme.drawString(g, dimName, tabX[i] + (tabW[i] - textW) / 2, tabY + 4, textColor, 0);
        }

        int ix = bx + PAD + 2;

        ItemStack cur = ClientClaimShopData.getBaseCost(selectedDimension());
        if (cur == null || cur.isEmpty())
            theme.drawString(g, Component.translatable("uc7core.claimshop.admin.basecost.notset").getString(), ix, curY, COL_TEXT, 0);
        else
            theme.drawString(g, Component.translatable("uc7core.claimshop.admin.basecost.current",
                    cur.getCount(), cur.getItem().getDescription().getString()).getString(), ix, curY, COL_GREEN_T, 0);

        theme.drawString(g, Component.translatable("uc7core.claimshop.admin.item").getString(), ix, itemLabelY, COL_TEXT, 0);
        theme.drawString(g, Component.translatable("uc7core.claimshop.admin.amount").getString(), ix, amtLabelY, COL_TEXT, 0);
        theme.drawString(g, Component.translatable("uc7core.claimshop.admin.basecost.item_hint").getString(), ix, hintY, COL_HINT, 0);

        drawBtn(g, theme, setX, setY, BTN_W, BTN_H, 0xFF1a3d16, 0xFF4CAF50, COL_GREEN_T,
                Component.translatable("uc7core.claimshop.admin.set").getString());
        drawBtn(g, theme, remX, remY, BTN_W, BTN_H, 0xFF3d1616, 0xFFE53935, COL_RED_T,
                Component.translatable("uc7core.claimshop.admin.remove").getString());

        boolean overPick = hit(mx, my, pickerX, pickerY, PICKER_W, FIELD_H);
        int pickBg = overPick ? 0xFF3a5a6a : 0xFF2a3d4a;
        drawBtn(g, theme, pickerX, pickerY, PICKER_W, FIELD_H, pickBg, pickBg, COL_TEXT, "\uD83D\uDD0D");
    }

    @Override
    public void drawForeground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        int mx = (int) getMouseX(), my = (int) getMouseY();
        if (hit(backX, backY, BACK_W, BTN_H))
            drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.back.tip"), mx, my);
        if (hit(setX, setY, BTN_W, BTN_H))
            drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.set.tip"), mx, my);
        if (hit(remX, remY, BTN_W, BTN_H))
            drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.remove.tip"), mx, my);
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
            if (hit(backX, backY, BACK_W, BTN_H)) { previous.openGui(); return true; }
            if (hit(setX, setY, BTN_W, BTN_H)) { doSet(); return true; }
            if (hit(remX, remY, BTN_W, BTN_H)) { doRemove(); return true; }
            if (hit(pickerX, pickerY, PICKER_W, FIELD_H)) { openItemPicker(); return true; }

            int mx = (int) getMouseX(), my = (int) getMouseY();
            for (int i = 0; i < 3; i++) {
                if (hit(mx, my, tabX[i], tabY, tabW[i], tabH)) {
                    selectedTab = i;
                    openGui();
                    return true;
                }
            }
        }
        return super.mousePressed(button);
    }

    @Override
    protected String getTitleStr() {
        return Component.translatable("uc7core.claimshop.admin.basecost").getString() + " — " + DIM_NAMES[selectedTab];
    }

    private void doSet() {
        String id = itemField.getText().trim();
        if (id.isEmpty()) return;
        int a;
        try { a = Integer.parseInt(amountField.getText().trim()); if (a < 1) a = 1; if (a > 64) a = 64; }
        catch (NumberFormatException e) { a = 1; }
        PacketDistributor.sendToServer(new AdminActionPacket(
                AdminActionPacket.ACTION_SET_BASE_COST, AdminActionPacket.NO_TEAM, id, a, false, selectedDimension().toString()));
        previous.openGui();
    }

    private void doRemove() {
        PacketDistributor.sendToServer(new AdminActionPacket(
                AdminActionPacket.ACTION_REMOVE_BASE_COST, AdminActionPacket.NO_TEAM, "", 0, false, selectedDimension().toString()));
        previous.openGui();
    }

    private void openItemPicker() {
        ItemStackConfig cfg = new ItemStackConfig(true, true);
        pendingItemId = itemField.getText().trim();
        pendingAmount = amountField.getText().trim();
        BuyingChunks.LOGGER.info("openItemPicker: pendingItemId='{}', pendingAmount='{}'", pendingItemId, pendingAmount);
        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(pendingItemId));
            if (!pendingItemId.isEmpty() && item != Items.AIR) {
                int cnt = 1;
                try { cnt = Math.max(1, Math.min(64, Integer.parseInt(pendingAmount))); } catch (Exception ignored) {}
                cfg.setValue(new ItemStack(item, cnt));
            }
        } catch (Exception ignored) {}
        cfg.onClicked(this, MouseButton.LEFT, saved -> {
            BuyingChunks.LOGGER.info("callback: saved={}", saved);
            if (saved) {
                ItemStack stack = cfg.getValue();
                if (stack != null && !stack.isEmpty()) {
                    pendingItemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    pendingAmount = String.valueOf(stack.getCount());
                    BuyingChunks.LOGGER.info("callback: updated pendingItemId='{}', pendingAmount='{}'", pendingItemId, pendingAmount);
                }
            }
            Minecraft.getInstance().tell(this::openGui);
        });
    }
}
