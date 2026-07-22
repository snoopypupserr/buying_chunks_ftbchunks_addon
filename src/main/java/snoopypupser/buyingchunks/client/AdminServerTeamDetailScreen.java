package snoopypupser.buyingchunks.client;

import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbchunks.client.gui.ChunkScreen;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.*;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import snoopypupser.buyingchunks.BuyingChunks;
import snoopypupser.buyingchunks.network.AdminActionPacket;

public class AdminServerTeamDetailScreen extends BaseAdminScreen {

    private static final int PROT_BTN_W = 72;
    private static final int SAVE_BTN_W = 50;
    private static final int COLOR_SQ = 14;
    private static final int SCROLLBAR_W = 8;
    private static final double SCROLL_FACTOR = 20.0;

    private static final String[] BOOL_KEYS = {
        "allow_explosions", "allow_mob_griefing", "allow_pvp",
        "allow_all_fake_players", "allow_fake_players_by_id"
    };
    private static final String[] PRIVACY_KEYS = {
        "block_edit_mode", "block_interact_mode", "entity_interact_mode",
        "nonliving_entity_attack_mode", "claim_visibility", "location_mode",
        "block_edit_and_interact_mode"
    };
    private static final PrivacyMode[] PRIVACY_VALUES = PrivacyMode.values();

    private final BaseScreen previous;
    private final Team team;
    private final int teamColor;

    private TextBox colorField;

    private int colorFieldY, colorSquareY, colorFieldW;
    private int saveX, saveY;
    private int protBtnX;
    private int protAreaY, protAreaW, protAreaH, protAreaX;
    private int scrollbarX, scrollbarH;
    private int maxScroll;
    private double scrollY;
    private int[] boolRowOff = new int[BOOL_KEYS.length];
    private int[] privacyRowOff = new int[PRIVACY_KEYS.length];
    private int totalProtH;
    private int bottomBtnY, btnW;
    private int deleteX, chunkX;

    public AdminServerTeamDetailScreen(BaseScreen previous, Team team) {
        this.previous = previous;
        this.team = team;
        int c = 0xFF888888;
        try { c = team.getProperty(dev.ftb.mods.ftbteams.api.property.TeamProperties.COLOR).rgb(); } catch (Exception ignored) {}
        this.teamColor = c;
    }

    @Override
    public boolean onInit() {
        setFullscreen();
        Minecraft mc = Minecraft.getInstance();
        int lh = mc.font.lineHeight;
        int scrH = mc.getWindow().getGuiScaledHeight();

        int cw = Math.max(mc.font.width(getTitleStr()), 280);
        bw = cw + PAD * 2 + 10;

        int teamPropsH = 3 + lh + (lh + GAP_LABEL_FIELD + FIELD_H) + GAP_CONTENT_BTN;
        totalProtH = 3 + lh + GAP_FIELD_NEXT
                + (BTN_H + GAP_FIELD_NEXT) * (BOOL_KEYS.length + PRIVACY_KEYS.length);
        int bottomH = GAP_CONTENT_BTN + BTN_H;
        int staticTop = PAD + BTN_H + GAP_BACK_TITLE + lh + GAP_TITLE_DIVIDER + 1 + GAP_DIVIDER_CONTENT;
        protAreaH = Math.max(3 + lh + GAP_FIELD_NEXT, scrH - staticTop - teamPropsH - bottomH - PAD * 2);
        bh = staticTop + teamPropsH + protAreaH + bottomH + PAD;
        computeTopSection(lh);

        int ix = bx + PAD + 2;
        int rightEdge = bx + bw - PAD - 2;
        protBtnX = rightEdge - PROT_BTN_W;
        saveX = rightEdge - SAVE_BTN_W;
        saveY = backY;

        int y = contentY;

            y += lh + lh + GAP_LABEL_FIELD;
        colorFieldY = y;
        colorSquareY = y;
        colorFieldW = rightEdge - (ix + COLOR_SQ + 4) - 4;

        protAreaY = colorFieldY + FIELD_H + GAP_CONTENT_BTN;
        protAreaX = ix;
        protAreaW = rightEdge - ix;
        scrollbarX = rightEdge + 2;
        scrollbarH = protAreaH;
        maxScroll = Math.max(0, totalProtH - protAreaH);

        int off = 0;
        off += 3 + lh + GAP_FIELD_NEXT;
        for (int i = 0; i < BOOL_KEYS.length; i++) {
            boolRowOff[i] = off;
            off += BTN_H + GAP_FIELD_NEXT;
        }
        for (int i = 0; i < PRIVACY_KEYS.length; i++) {
            privacyRowOff[i] = off;
            off += BTN_H + GAP_FIELD_NEXT;
        }

        int bottomBtnY0 = protAreaY + protAreaH + GAP_CONTENT_BTN;
        btnW = (rightEdge - ix) / 2;
        deleteX = ix;
        chunkX = deleteX + btnW + 6;
        bottomBtnY = bottomBtnY0;

        String hexStr = String.format("%06X", teamColor & 0xFFFFFF);
        colorField = new TextBox(this);
        colorField.setPosAndSize(ix + COLOR_SQ + 4, colorFieldY, colorFieldW, FIELD_H);
        colorField.setMaxLength(6);
        colorField.setText(hexStr);
        colorField.setFilter(s -> s.matches("[0-9A-Fa-f]*"));
        colorField.textColor = COL_TEXT;

        return true;
    }

    @Override
    public void addWidgets() {
        add(colorField);
    }

    @Override
    public void alignWidgets() {}

    @Override
    public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        int lh = theme.getFont().lineHeight;
        int ix = bx + PAD + 2;

        g.fill(0, 0, getWidth(), getHeight(), 0x88000000);
        drawPanel(g, bx, by, bw, bh);
        drawTopSection(g, theme);
        drawBtn(g, theme, saveX, saveY, SAVE_BTN_W, BTN_H, 0xFF1a3d16, 0xFF4CAF50, COL_GREEN_T,
                Component.translatable("uc7core.claimshop.admin.serverteam.detail.saveall").getString());

        // === Team Properties ===
        theme.drawString(g, Component.translatable("uc7core.claimshop.admin.serverteam.detail.properties").getString(),
                ix, contentY, COL_HEADING, 0);

        theme.drawString(g, Component.translatable("uc7core.claimshop.admin.serverteam.detail.color").getString(),
                ix, colorFieldY - lh - GAP_LABEL_FIELD, COL_TEXT, 0);
        int previewColor = teamColor;
        try { previewColor = 0xFF000000 | Integer.parseInt(colorField.getText().trim(), 16); } catch (Exception ignored) {}
        g.fill(ix, colorSquareY, ix + COLOR_SQ, colorSquareY + COLOR_SQ, previewColor);
        g.fill(ix, colorSquareY, ix + COLOR_SQ, colorSquareY + 1, 0x55FFFFFF);
        g.fill(ix, colorSquareY, ix + 1, colorSquareY + COLOR_SQ, 0x55FFFFFF);
        g.fill(ix, colorSquareY + COLOR_SQ - 1, ix + COLOR_SQ, colorSquareY + COLOR_SQ, 0x55000000);
        g.fill(ix + COLOR_SQ - 1, colorSquareY, ix + COLOR_SQ, colorSquareY + COLOR_SQ, 0x55000000);

        // === Claim Protection (scrollable) ===
        int scy = (int) scrollY;

        GuiHelper.pushScissor(Minecraft.getInstance().getWindow(), protAreaX, protAreaY, protAreaW, protAreaH);

        int drawY = protAreaY - scy;

        g.fill(ix, drawY, bx + bw - PAD - 2, drawY + 1, C_BORDER);
        theme.drawString(g, Component.translatable("uc7core.claimshop.admin.serverteam.detail.protection").getString(),
                ix, drawY + 3, COL_HEADING, 0);
        drawY += 3 + lh + GAP_FIELD_NEXT;

        int labelY;
        for (int i = 0; i < BOOL_KEYS.length; i++) {
            labelY = drawY + (BTN_H - lh) / 2;
            theme.drawString(g, getBoolLabel(i), ix, labelY, COL_TEXT, 0);
            boolean val = readBoolProp(BOOL_KEYS[i]);
            int btnBg = val ? 0xFF1a3d16 : 0xFF3d1616;
            int btnBorder = val ? 0xFF4CAF50 : 0xFFE53935;
            Color4I txtCol = val ? COL_GREEN_T : COL_RED_T;
            drawBtn(g, theme, protBtnX, drawY, PROT_BTN_W, BTN_H, btnBg, btnBorder, txtCol,
                    Component.translatable(val ? "uc7core.claimshop.admin.serverteam.detail.on" : "uc7core.claimshop.admin.serverteam.detail.off").getString());
            drawY += BTN_H + GAP_FIELD_NEXT;
        }

        for (int i = 0; i < PRIVACY_KEYS.length; i++) {
            labelY = drawY + (BTN_H - lh) / 2;
            theme.drawString(g, getPrivacyLabel(i), ix, labelY, COL_TEXT, 0);
            PrivacyMode mode = readPrivacyProp(PRIVACY_KEYS[i]);
            drawBtn(g, theme, protBtnX, drawY, PROT_BTN_W, BTN_H, 0xFF1a2a3d, 0xFF4a6d9a, COL_TEXT,
                    Component.translatable("uc7core.claimshop.admin.privacy." + mode.name().toLowerCase()).getString());
            drawY += BTN_H + GAP_FIELD_NEXT;
        }

        GuiHelper.popScissor(Minecraft.getInstance().getWindow());

        // Scrollbar track + thumb
        if (maxScroll > 0) {
            int trackX = scrollbarX;
            int trackY = protAreaY;
            int trackW = SCROLLBAR_W;
            int trackH = protAreaH;
            g.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0xFF1a1a2e);
            g.fill(trackX, trackY, trackX + trackW, trackY + 1, 0xFF495366);
            g.fill(trackX, trackY, trackX + 1, trackY + trackH, 0xFF495366);

            int thumbH = Math.max(16, protAreaH * protAreaH / totalProtH);
            int thumbY = trackY + (int) ((scrollY / maxScroll) * (trackH - thumbH));
            g.fill(trackX + 1, thumbY, trackX + trackW - 1, thumbY + thumbH, 0xFF4a6d9a);
            g.fill(trackX + 1, thumbY, trackX + trackW - 1, thumbY + 1, 0xFF6a8dba);
        }

        // === Bottom buttons ===
        drawBtn(g, theme, deleteX, bottomBtnY, btnW, BTN_H, 0xFF3d1616, 0xFFE53935, COL_RED_T,
                Component.translatable("uc7core.claimshop.admin.serverteam.detail.delete_btn").getString());
        drawBtn(g, theme, chunkX, bottomBtnY, btnW, BTN_H, 0xFF1a3d6a, 0xFF4a6d9a, COL_TEXT,
                Component.translatable("uc7core.claimshop.admin.serverteam.detail.chunkmap_btn").getString());
    }

    @Override
    public void drawForeground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        int mx = (int) getMouseX(), my = (int) getMouseY();
        int scy = (int) scrollY;
        int lh = theme.getFont().lineHeight;

        if (hit(backX, backY, BACK_W, BTN_H))
            drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.back.tip"), mx, my);

        if (hit(saveX, saveY, SAVE_BTN_W, BTN_H))
            drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.serverteam.detail.saveall_tip"), mx, my);

        if (my >= protAreaY && my < protAreaY + protAreaH) {
            int drawY = protAreaY - scy + 3 + lh + GAP_FIELD_NEXT;
            for (int i = 0; i < BOOL_KEYS.length; i++) {
                if (hit(protBtnX, drawY, PROT_BTN_W, BTN_H))
                    drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.serverteam.detail.protection_tip"), mx, my);
                drawY += BTN_H + GAP_FIELD_NEXT;
            }
            for (int i = 0; i < PRIVACY_KEYS.length; i++) {
                if (hit(protBtnX, drawY, PROT_BTN_W, BTN_H))
                    drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.serverteam.detail.protection_tip"), mx, my);
                drawY += BTN_H + GAP_FIELD_NEXT;
            }
        }

        if (hit(deleteX, bottomBtnY, btnW, BTN_H))
            drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.serverteam.detail.delete_tip"), mx, my);
        if (hit(chunkX, bottomBtnY, btnW, BTN_H))
            drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.open_chunkmap.tip"), mx, my);
    }

    @Override
    public boolean mousePressed(MouseButton button) {
        if (button.isLeft()) {
            int mx = (int) getMouseX(), my = (int) getMouseY();
            int scy = (int) scrollY;
            int lh = getTheme().getFont().lineHeight;

            if (hit(backX, backY, BACK_W, BTN_H)) { previous.openGui(); return true; }

            if (hit(saveX, saveY, SAVE_BTN_W, BTN_H)) { doSaveAll(); return true; }

            if (my >= protAreaY && my < protAreaY + protAreaH) {
                int drawY = protAreaY - scy + 3 + lh + GAP_FIELD_NEXT;
                for (int i = 0; i < BOOL_KEYS.length; i++) {
                    if (hit(protBtnX, drawY, PROT_BTN_W, BTN_H)) {
                        toggleBoolProp(i);
                        return true;
                    }
                    drawY += BTN_H + GAP_FIELD_NEXT;
                }
                for (int i = 0; i < PRIVACY_KEYS.length; i++) {
                    if (hit(protBtnX, drawY, PROT_BTN_W, BTN_H)) {
                        cyclePrivacyProp(i);
                        return true;
                    }
                    drawY += BTN_H + GAP_FIELD_NEXT;
                }
            }

            if (hit(deleteX, bottomBtnY, btnW, BTN_H)) { doDeleteTeam(); return true; }
            if (hit(chunkX, bottomBtnY, btnW, BTN_H)) { ChunkScreen.openChunkScreen(team); return true; }
        }
        return super.mousePressed(button);
    }

    @Override
    public boolean mouseScrolled(double scroll) {
        if (maxScroll > 0) {
            scrollY = Math.max(0, Math.min(maxScroll, scrollY - scroll * SCROLL_FACTOR));
            return true;
        }
        return false;
    }

    @Override
    protected String getTitleStr() {
        return Component.translatable("uc7core.claimshop.admin.serverteam.detail.title", team.getName().getString()).getString();
    }

    // --- Label helpers ---

    private Component getBoolLabel(int index) {
        return Component.translatable("uc7core.claimshop.admin.serverteam.detail.prop." + BOOL_KEYS[index]);
    }

    private Component getPrivacyLabel(int index) {
        return Component.translatable("uc7core.claimshop.admin.serverteam.detail.prop." + PRIVACY_KEYS[index]);
    }

    // --- Property readers ---

    private boolean readBoolProp(String key) {
        try {
            return switch (key) {
                case "allow_explosions" -> team.getProperty(FTBChunksProperties.ALLOW_EXPLOSIONS);
                case "allow_mob_griefing" -> team.getProperty(FTBChunksProperties.ALLOW_MOB_GRIEFING);
                case "allow_pvp" -> team.getProperty(FTBChunksProperties.ALLOW_PVP);
                case "allow_all_fake_players" -> team.getProperty(FTBChunksProperties.ALLOW_ALL_FAKE_PLAYERS);
                case "allow_fake_players_by_id" -> team.getProperty(FTBChunksProperties.ALLOW_FAKE_PLAYERS_BY_ID);
                default -> false;
            };
        } catch (Exception e) {
            return false;
        }
    }

    private PrivacyMode readPrivacyProp(String key) {
        try {
            return switch (key) {
                case "block_edit_mode" -> team.getProperty(FTBChunksProperties.BLOCK_EDIT_MODE);
                case "block_interact_mode" -> team.getProperty(FTBChunksProperties.BLOCK_INTERACT_MODE);
                case "entity_interact_mode" -> team.getProperty(FTBChunksProperties.ENTITY_INTERACT_MODE);
                case "nonliving_entity_attack_mode" -> team.getProperty(FTBChunksProperties.NONLIVING_ENTITY_ATTACK_MODE);
                case "claim_visibility" -> team.getProperty(FTBChunksProperties.CLAIM_VISIBILITY);
                case "location_mode" -> team.getProperty(FTBChunksProperties.LOCATION_MODE);
                case "block_edit_and_interact_mode" -> team.getProperty(FTBChunksProperties.BLOCK_EDIT_AND_INTERACT_MODE);
                default -> PrivacyMode.ALLIES;
            };
        } catch (Exception e) {
            return PrivacyMode.ALLIES;
        }
    }

    // --- Property mutators ---

    private void toggleBoolProp(int index) {
        String key = BOOL_KEYS[index];
        boolean current = readBoolProp(key);
        PacketDistributor.sendToServer(new AdminActionPacket(
                AdminActionPacket.ACTION_SET_BOOL_PROPERTY, team.getId(), key, 0, !current, AdminActionPacket.NO_DIMENSION));
    }

    private void cyclePrivacyProp(int index) {
        String key = PRIVACY_KEYS[index];
        PrivacyMode current = readPrivacyProp(key);
        int nextOrd = (current.ordinal() + 1) % PRIVACY_VALUES.length;
        PrivacyMode next = PRIVACY_VALUES[nextOrd];
        PacketDistributor.sendToServer(new AdminActionPacket(
                AdminActionPacket.ACTION_SET_PRIVACY_PROPERTY, team.getId(), key + "=" + next.name(), 0, false, AdminActionPacket.NO_DIMENSION));
    }

    // --- Team property actions ---

    private void doSaveAll() {
        String hex = colorField.getText().trim();
        if (!hex.isEmpty()) {
            try {
                int rgb = Integer.parseInt(hex, 16);
                PacketDistributor.sendToServer(new AdminActionPacket(
                        AdminActionPacket.ACTION_UPDATE_TEAM_COLOR, team.getId(), "", rgb, false, AdminActionPacket.NO_DIMENSION));
            } catch (NumberFormatException ignored) {}
        }
    }

    private void doDeleteTeam() {
        String teamName = team.getName().getString();
        PacketDistributor.sendToServer(new AdminActionPacket(
                AdminActionPacket.ACTION_DELETE_SERVER_TEAM, team.getId(), teamName, 0, false, AdminActionPacket.NO_DIMENSION));
        previous.openGui();
    }
}
