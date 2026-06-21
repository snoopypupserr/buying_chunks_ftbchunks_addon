package snoopypupser.buyingchunks.client;

import dev.ftb.mods.ftbchunks.client.gui.ChunkScreen;
import dev.ftb.mods.ftblibrary.ui.*;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import snoopypupser.buyingchunks.BuyingChunks;
import snoopypupser.buyingchunks.network.AdminActionPacket;

import java.util.*;
import java.util.stream.Collectors;

public class AdminServerTeamMgmtScreen extends BaseAdminScreen {

    private static final int PANEL_W = 280;
    private static final int MAX_VIS = 8;
    private static final int BTN_W = 50;
    private static final int SMALL_BTN_W = 20;

    private final BaseScreen previous;
    private TextBox createNameField;
    private List<Team> serverTeams = new ArrayList<>();
    private int scroll = 0;

    private int listTop, listH;
    private int createLabelY, createFieldY, createBtnY;

    public AdminServerTeamMgmtScreen(BaseScreen previous) {
        this.previous = previous;
    }

    @Override
    public boolean onInit() {
        setFullscreen();
        refreshTeamList();
        Minecraft mc = Minecraft.getInstance();
        int lh = mc.font.lineHeight;
        bw = PANEL_W;
        listH = Math.min(serverTeams.size(), MAX_VIS) * ROW_H;
        listH = Math.max(listH, ROW_H);
        bh = PAD + BTN_H + GAP_BACK_TITLE + lh + GAP_TITLE_DIVIDER + 1 + GAP_DIVIDER_CONTENT
                + listH + GAP_CONTENT_BTN + lh + GAP_LABEL_FIELD + FIELD_H + GAP_CONTENT_BTN + BTN_H + PAD;
        computeTopSection(lh);

        int y = contentY;
        listTop = y;
        y += listH + GAP_CONTENT_BTN;
        createLabelY = y;
        y += lh + GAP_LABEL_FIELD;
        createFieldY = y;
        y += FIELD_H + GAP_CONTENT_BTN;
        createBtnY = y;
        return true;
    }

    @Override
    public void addWidgets() {
        int fw = bw - PAD * 2 - 4;
        int x = bx + PAD + 2;

        createNameField = new TextBox(this);
        createNameField.setPosAndSize(x, createFieldY, fw, FIELD_H);
        createNameField.setMaxLength(64);
        createNameField.textColor = COL_TEXT;
        add(createNameField);
    }

    @Override
    public void alignWidgets() {}

    public void refreshTeamList() {
        serverTeams.clear();
        try {
            serverTeams = FTBTeamsAPI.api().getClientManager().getTeams().stream()
                    .filter(Team::isServerTeam)
                    .sorted(Comparator.comparing(t -> t.getName().getString()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            BuyingChunks.LOGGER.warn("AdminServerTeamMgmtScreen: failed to refresh team list", e);
        }
    }

    @Override
    public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        int mx = (int) getMouseX(), my = (int) getMouseY();
        int lh = theme.getFont().lineHeight;
        int ix = bx + PAD + 2;

        g.fill(0, 0, getWidth(), getHeight(), 0x88000000);
        drawPanel(g, bx, by, bw, bh);
        drawTopSection(g, theme);

        int vis = Math.min(serverTeams.size(), MAX_VIS);

        if (serverTeams.isEmpty()) {
            String empty = Component.translatable("uc7core.claimshop.admin.serverteam.no_teams").getString();
            theme.drawString(g, empty, bx + (bw - theme.getFont().width(empty)) / 2, listTop + 2, COL_HINT, 0);
        } else {
            for (int i = scroll; i < serverTeams.size() && i < scroll + vis; i++) {
                Team team = serverTeams.get(i);
                int ry = listTop + (i - scroll) * ROW_H;
                if (hit(mx, my, bx + PAD, ry, bw - PAD * 2, ROW_H))
                    g.fill(bx + PAD, ry, bx + bw - PAD, ry + ROW_H, C_HOVER);

                int teamColor = 0xFF888888;
                try { teamColor = team.getProperty(dev.ftb.mods.ftbteams.api.property.TeamProperties.COLOR).rgb(); } catch (Exception ignored) {}
                g.fill(ix, ry + 3, ix + 10, ry + 13, teamColor);
                g.fill(ix, ry + 3, ix + 10, ry + 4, 0x55FFFFFF);
                g.fill(ix, ry + 3, ix + 1, ry + 13, 0x55FFFFFF);
                g.fill(ix, ry + 12, ix + 10, ry + 13, 0x55000000);
                g.fill(ix + 9, ry + 3, ix + 10, ry + 13, 0x55000000);

                String teamName = team.getName().getString();
                int nameX = ix + 14;
                int maxNameW = bw - PAD * 2 - 14 - SMALL_BTN_W * 3 - 8;
                if (theme.getFont().width(teamName) > maxNameW)
                    teamName = theme.getFont().plainSubstrByWidth(teamName, maxNameW - theme.getFont().width("...")) + "...";
                theme.drawString(g, teamName, nameX, ry + (ROW_H - lh) / 2 + 1, COL_TEXT, 0);

                int btnRight = bx + bw - PAD - 2;
                int rx_chunk = btnRight - SMALL_BTN_W;
                int rx_delete = rx_chunk - SMALL_BTN_W - 2;
                int rx_manage = rx_delete - BTN_W - 2;

                drawBtn(g, theme, rx_manage, ry + 1, BTN_W, BTN_H, 0xFF1a2a3d, 0xFF4a6d9a, COL_TEXT,
                        Component.translatable("uc7core.claimshop.admin.serverteam.manage").getString());
                drawBtn(g, theme, rx_delete, ry + 1, SMALL_BTN_W, BTN_H, 0xFF3d1616, 0xFFE53935, COL_RED_T, "\u2715");
                drawBtn(g, theme, rx_chunk, ry + 1, SMALL_BTN_W, BTN_H, 0xFF1a3d6a, 0xFF4a6d9a, COL_TEXT, "\u2302");
            }
        }

        theme.drawString(g, Component.translatable("uc7core.claimshop.admin.serverteam.create_label").getString(),
                ix, createLabelY, COL_TEXT, 0);

        int createBtnW = 60;
        drawBtn(g, theme, bx + bw / 2 - createBtnW / 2, createBtnY, createBtnW, BTN_H, 0xFF1a3d16, 0xFF4CAF50, COL_GREEN_T,
                Component.translatable("uc7core.claimshop.admin.serverteam.create_btn").getString());
    }

    @Override
    public void drawForeground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        int mx = (int) getMouseX(), my = (int) getMouseY();

        if (hit(backX, backY, BACK_W, BTN_H))
            drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.back.tip"), mx, my);

        int vis = Math.min(serverTeams.size(), MAX_VIS);
        for (int i = scroll; i < serverTeams.size() && i < scroll + vis; i++) {
            int ry = listTop + (i - scroll) * ROW_H;
            int btnRight = bx + bw - PAD - 2;
            int rx_chunk = btnRight - SMALL_BTN_W;
            int rx_delete = rx_chunk - SMALL_BTN_W - 2;
            int rx_manage = rx_delete - BTN_W - 2;

            if (hit(rx_manage, ry + 1, BTN_W, BTN_H))
                drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.serverteam.edit_tip"), mx, my);
            if (hit(rx_delete, ry + 1, SMALL_BTN_W, BTN_H))
                drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.serverteam.delete_tip"), mx, my);
            if (hit(rx_chunk, ry + 1, SMALL_BTN_W, BTN_H))
                drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.open_chunkmap.tip"), mx, my);
        }

        if (createNameField != null && hit(createNameField.getX(), createFieldY, createNameField.getWidth(), FIELD_H))
            drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.team_field.tip"), mx, my);

        int createBtnW = 60;
        if (hit(bx + bw / 2 - createBtnW / 2, createBtnY, createBtnW, BTN_H))
            drawTooltip(g, theme, Component.translatable("uc7core.claimshop.admin.serverteam.create_tip"), mx, my);
    }

    @Override
    public boolean mousePressed(MouseButton button) {
        if (button.isLeft()) {
            int mx = (int) getMouseX(), my = (int) getMouseY();

            if (hit(backX, backY, BACK_W, BTN_H)) { previous.openGui(); return true; }

            int vis = Math.min(serverTeams.size(), MAX_VIS);
            for (int i = scroll; i < serverTeams.size() && i < scroll + vis; i++) {
                Team team = serverTeams.get(i);
                int ry = listTop + (i - scroll) * ROW_H;
                int btnRight = bx + bw - PAD - 2;
                int rx_chunk = btnRight - SMALL_BTN_W;
                int rx_delete = rx_chunk - SMALL_BTN_W - 2;
                int rx_manage = rx_delete - BTN_W - 2;

                if (hit(rx_manage, ry + 1, BTN_W, BTN_H)) {
                    new AdminServerTeamDetailScreen(this, team).openGui();
                    return true;
                }
                if (hit(rx_delete, ry + 1, SMALL_BTN_W, BTN_H)) {
                    doDelete(team);
                    return true;
                }
                if (hit(rx_chunk, ry + 1, SMALL_BTN_W, BTN_H)) {
                    ChunkScreen.openChunkScreen(team);
                    return true;
                }
            }

            int createBtnW = 60;
            if (hit(bx + bw / 2 - createBtnW / 2, createBtnY, createBtnW, BTN_H)) {
                doCreate();
                return true;
            }
        }
        return super.mousePressed(button);
    }

    @Override
    public boolean mouseScrolled(double amount) {
        int maxOff = Math.max(0, serverTeams.size() - MAX_VIS);
        scroll = (int) Math.max(0, Math.min(maxOff, scroll - amount));
        return true;
    }

    @Override
    protected String getTitleStr() {
        return Component.translatable("uc7core.claimshop.admin.serverteam.title").getString();
    }

    private void doCreate() {
        String name = createNameField.getText().trim();
        if (name.isEmpty()) return;
        PacketDistributor.sendToServer(new AdminActionPacket(
                AdminActionPacket.ACTION_CREATE_SERVER_TEAM, AdminActionPacket.NO_TEAM, name, 0, false));
        createNameField.setText("");
    }

    private void doDelete(Team team) {
        String teamName = team.getName().getString();
        PacketDistributor.sendToServer(new AdminActionPacket(
                AdminActionPacket.ACTION_DELETE_SERVER_TEAM, team.getId(), teamName, 0, false));
    }
}
