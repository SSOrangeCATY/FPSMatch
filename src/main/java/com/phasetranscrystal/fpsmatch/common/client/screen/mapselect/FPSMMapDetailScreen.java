package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.screen.FPSMTeamManageScreen;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.UUID;



public class FPSMMapDetailScreen extends FPSMMapScreenBase implements FPSMMapDetailChildScreen {
    private static final int PANEL_TOP = 68;
    private static final int PANEL_BOTTOM = 82;
    private static final int ROW_HEIGHT = 12;
    private static final int PREVIEW_WIDTH = 200;

    private MapRoomDetail detail;
    private final Screen parent;
    private Button joinButton;
    private Button leaveButton;
    private Button inviteButton;
    private int playerScrollOffset;

    public FPSMMapDetailScreen(MapRoomDetail detail, Screen parent) {
        super(Component.translatable("gui.fpsm.map_select.detail.title"));
        this.detail = detail;
        this.parent = parent;
    }

    @Override
    public void applyDetail(MapRoomDetail detail) {
        this.detail = detail;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    protected void rebuildWidgets() {
        clearWidgets();
        int centerX = width / 2;
        // 底部主操作行：加入（已加入则进入队伍管理）/离开/邀请/返回，统一中按钮 90，间距 8
        int mainTotal = 4;
        int bw = FPSMGuiTheme.BUTTON_MEDIUM_WIDTH;
        int gap = FPSMGuiTheme.BUTTON_GAP;
        int[] mainXs = new int[mainTotal];
        for (int i = 0; i < mainTotal; i++) {
            mainXs[i] = buttonX(centerX, bw, gap, i, mainTotal);
        }
        joinButton = addRenderableWidget(createMediumButton(Component.translatable("gui.fpsm.map_select.join"), mainXs[0], height - 52,
                button -> onJoinOrTeamManage()));
        leaveButton = addRenderableWidget(createMediumButton(Component.translatable("gui.fpsm.map_select.leave"), mainXs[1], height - 52,
                button -> sendRoomAction(MapRoomActionC2SPacket.Action.LEAVE, new UUID(0L, 0L))));
        inviteButton = addRenderableWidget(createMediumButton(Component.translatable("gui.fpsm.map_select.invite"), mainXs[2], height - 52,
                button -> FPSMMapSelectScreens.openChild(new FPSMMapInviteScreen(detail, this))));
        addRenderableWidget(createMediumButton(Component.translatable("gui.back"), mainXs[3], height - 52,
                button -> onClose()));
        updateButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawMultiLayerBackground(graphics);

        graphics.drawCenteredString(font, title, width / 2, 12, FPSMGuiTheme.TEXT_TITLE);

        if (detail != null) {
            MapRoomSummary summary = detail.summary();
            int left = width / 2 - 210;
            int right = width / 2 + 210;

            graphics.drawCenteredString(font, Component.literal(summary.displayName()), width / 2, 26, FPSMGuiTheme.TEXT_SUB);

            // 内容区背景（统一）
            drawListBackground(graphics, left - 6, PANEL_TOP - 2, right + 6, height - PANEL_BOTTOM + 2);

            // 左侧预览区：贴图优先，色块兜底
            int previewH = height - PANEL_BOTTOM - PANEL_TOP - 4;
            MapThumbnailRenderer.render(graphics, left, PANEL_TOP, PREVIEW_WIDTH, previewH,
                    detail.backgroundTexture(), summary.mapName(), summary.gameType(), summary.displayName(), true);

            // 右侧信息区
            int infoX = left + PREVIEW_WIDTH + 8;
            int infoY = PANEL_TOP;
            graphics.drawString(font, Component.translatable("gui.fpsm.map_select.detail.status", statusText(summary)), infoX, infoY, statusColor(summary), false);
            Component gameTypeName = Component.translatable("fpsm.game_type." + summary.gameType());
            graphics.drawString(font, Component.translatable("gui.fpsm.map_select.game_info", gameTypeName), infoX, infoY + 16, FPSMGuiTheme.TEXT_SUB, false);
            graphics.drawString(font, Component.translatable("gui.fpsm.map_select.detail.area", summary.areaText()), infoX, infoY + 32, FPSMGuiTheme.TEXT_SUB, false);
            graphics.drawString(font, Component.translatable(detail.rulesKey()), infoX, infoY + 52, FPSMGuiTheme.TEXT_BODY, false);

            // 玩家列表
            renderPlayers(graphics, infoX, infoY + 74);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPlayers(GuiGraphics graphics, int left, int top) {
        MapRoomSummary summary = detail.summary();
        graphics.drawString(font, Component.translatable("gui.fpsm.map_select.players.title")
                .append(" (" + summary.joinedPlayers() + "/" + maxPlayersText(summary) + ")"),
                left, top, FPSMGuiTheme.TEXT_TITLE, false);

        int listTop = top + 14;
        int availableHeight = height - PANEL_BOTTOM - listTop;
        int visibleRows = Math.max(1, availableHeight / ROW_HEIGHT);
        int maxScroll = Math.max(0, detail.players().size() - visibleRows);
        playerScrollOffset = Mth.clamp(playerScrollOffset, 0, maxScroll);

        if (detail.players().isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.fpsm.map_select.players.empty"), left, listTop, FPSMGuiTheme.TEXT_MUTED, false);
            return;
        }

        graphics.enableScissor(left - 2, listTop, left + 210, listTop + visibleRows * ROW_HEIGHT);
        for (int i = playerScrollOffset; i < Math.min(detail.players().size(), playerScrollOffset + visibleRows); i++) {
            MapRoomPlayerInfo player = detail.players().get(i);
            int py = listTop + (i - playerScrollOffset) * ROW_HEIGHT;
            int color = player.online() ? FPSMGuiTheme.TEXT_HIGHLIGHT : FPSMGuiTheme.TEXT_DISABLED;
            graphics.drawString(font, Component.literal(player.name()), left, py, color, false);
            graphics.drawString(font, Component.literal(player.teamName()), left + 100, py, player.spectator() ? FPSMGuiTheme.ST_SPECTATOR : FPSMGuiTheme.ST_ONLINE, false);
        }
        graphics.disableScissor();

        drawScrollBar(graphics, left + 204, listTop, visibleRows * ROW_HEIGHT, playerScrollOffset, maxScroll, detail.players().size(), visibleRows);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int infoY = PANEL_TOP;
        int listTop = infoY + 74 + 14;
        int availableHeight = height - PANEL_BOTTOM - listTop;
        int visibleRows = Math.max(1, availableHeight / ROW_HEIGHT);
        int maxScroll = Math.max(0, detail == null ? 0 : detail.players().size() - visibleRows);
        playerScrollOffset -= (int) scrollY;
        playerScrollOffset = Mth.clamp(playerScrollOffset, 0, maxScroll);
        return true;
    }

    private void sendRoomAction(MapRoomActionC2SPacket.Action action, UUID target) {
        if (detail != null) {
            FPSMatch.sendToServer(new MapRoomActionC2SPacket(action, detail.summary().gameType(), detail.summary().mapName(), target));
        }
    }

    private void updateButtons() {
        MapRoomSummary summary = detail == null ? null : detail.summary();
        boolean hasDetail = summary != null;
        boolean joined = hasDetail && (summary.currentPlayerJoined() || summary.currentPlayerSpectating());
        if (joinButton != null) {
            joinButton.active = hasDetail && (joined || (!summary.full() && (!summary.started() || summary.allowJoinInProgress())));
        }
        if (leaveButton != null) {
            leaveButton.active = joined;
        }
        if (inviteButton != null) {
            inviteButton.active = hasDetail && joined && !detail.availableInviteTargets().isEmpty();
        }
    }

    private void onJoinOrTeamManage() {
        if (detail == null) return;
        MapRoomSummary summary = detail.summary();
        boolean joined = summary.currentPlayerJoined() || summary.currentPlayerSpectating();
        if (joined) {
            openTeamManage();
        } else {
            sendRoomAction(MapRoomActionC2SPacket.Action.JOIN, new UUID(0L, 0L));
        }
    }

    private Component statusText(MapRoomSummary summary) {
        if (summary.debug()) {
            return Component.translatable("gui.fpsm.map_select.status.debug");
        }
        if (summary.started()) {
            return Component.translatable(summary.allowJoinInProgress() ? "gui.fpsm.map_select.status.started_joinable" : "gui.fpsm.map_select.status.started");
        }
        return Component.translatable("gui.fpsm.map_select.status.waiting");
    }

    private int statusColor(MapRoomSummary summary) {
        if (summary.debug()) {
            return FPSMGuiTheme.ST_DEBUG;
        }
        if (summary.started()) {
            return summary.allowJoinInProgress() ? FPSMGuiTheme.ST_RUNNING_JOINABLE : FPSMGuiTheme.ST_RUNNING_CLOSED;
        }
        return FPSMGuiTheme.ST_WAITING;
    }

    private String maxPlayersText(MapRoomSummary summary) {
        return summary.maxPlayers() < 0 ? "∞" : Integer.toString(summary.maxPlayers());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private void openTeamManage() {
        if (detail != null) {
            FPSMMapSelectScreens.openChild(new FPSMTeamManageScreen(detail, this));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
