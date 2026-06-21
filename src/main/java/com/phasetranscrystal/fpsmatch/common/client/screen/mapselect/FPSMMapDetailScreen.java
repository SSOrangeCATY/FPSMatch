package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
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
    private Button manageButton;
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
        int bw = FPSMGuiTheme.BUTTON_MEDIUM_WIDTH;
        int gap = FPSMGuiTheme.BUTTON_GAP;
        boolean op = detail != null && detail.summary().currentPlayerOp();
        int mainTotal = op ? 5 : 4;
        int buttonY = height - 52;
        joinButton = addRenderableWidget(createMediumButton(Component.translatable("gui.fpsm.map_select.join"), buttonX(centerX, bw, gap, 0, mainTotal), buttonY,
                button -> onJoinOrTeamManage()));
        leaveButton = addRenderableWidget(createMediumButton(Component.translatable("gui.fpsm.map_select.leave"), buttonX(centerX, bw, gap, 1, mainTotal), buttonY,
                button -> sendRoomAction(MapRoomActionC2SPacket.Action.LEAVE, new UUID(0L, 0L))));
        inviteButton = addRenderableWidget(createMediumButton(Component.translatable("gui.fpsm.map_select.invite"), buttonX(centerX, bw, gap, 2, mainTotal), buttonY,
                button -> FPSMMapSelectScreens.openChild(new FPSMMapInviteScreen(detail, this))));
        int backIndex = 3;
        if (op) {
            manageButton = addRenderableWidget(createMediumButton(Component.translatable("gui.fpsm.map_select.manage"), buttonX(centerX, bw, gap, 3, mainTotal), buttonY,
                    button -> FPSMMapSelectScreens.openChild(new FPSMMapManageScreen(detail, this))));
            backIndex = 4;
        }
        addRenderableWidget(createMediumButton(Component.translatable("gui.back"), buttonX(centerX, bw, gap, backIndex, mainTotal), buttonY,
                button -> onClose()));
        updateButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawMultiLayerBackground(graphics);

        drawScreenTitle(graphics, title, null, 12);

        if (detail != null) {
            MapRoomSummary summary = detail.summary();
            int left = Math.max(18, width / 2 - 210);
            int right = Math.min(width - 18, width / 2 + 210);

            graphics.drawCenteredString(font, clipped(Component.literal(summary.displayName()), right - left - 24), width / 2, 32, FPSMGuiTheme.TEXT_SUB);

            int contentBottom = Math.min(height - PANEL_BOTTOM + 2, PANEL_TOP + 178);
            drawListBackground(graphics, left - 6, PANEL_TOP - 2, right + 6, contentBottom);

            int previewWidth = Math.min(PREVIEW_WIDTH, Math.max(96, (right - left) / 2 - 8));
            int previewH = Math.max(72, contentBottom - PANEL_TOP - 4);
            MapThumbnailRenderer.render(graphics, left, PANEL_TOP, previewWidth, previewH,
                    detail.backgroundTexture(), summary.mapName(), summary.gameType(), summary.displayName(), true);

            int infoX = left + previewWidth + 10;
            int infoRight = right - 8;
            int infoY = PANEL_TOP;
            drawStatusChip(graphics, statusText(summary), infoX, infoY, statusColor(summary));
            Component gameTypeName = Component.translatable("fpsm.game_type." + summary.gameType());
            drawClippedString(graphics, Component.translatable("gui.fpsm.map_select.game_info", gameTypeName), infoX, infoY + 19, FPSMGuiTheme.TEXT_SUB, infoRight - infoX);
            drawClippedString(graphics, Component.translatable("gui.fpsm.map_select.detail.area", summary.areaText()), infoX, infoY + 34, FPSMGuiTheme.TEXT_SUB, infoRight - infoX);
            drawClippedString(graphics, Component.translatable(detail.rulesKey()), infoX, infoY + 52, FPSMGuiTheme.TEXT_BODY, infoRight - infoX);

            renderPlayers(graphics, infoX, infoY + 74, contentBottom, infoRight - infoX);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPlayers(GuiGraphics graphics, int left, int top, int bottom, int width) {
        MapRoomSummary summary = detail.summary();
        drawSectionLabel(graphics, Component.translatable("gui.fpsm.map_select.players.title")
                .append(" (" + summary.joinedPlayers() + "/" + maxPlayersText(summary) + ")"), left, top);

        int listTop = top + 16;
        int availableHeight = Math.max(ROW_HEIGHT, bottom - listTop - 4);
        int visibleRows = Math.max(1, availableHeight / ROW_HEIGHT);
        int maxScroll = Math.max(0, detail.players().size() - visibleRows);
        playerScrollOffset = Mth.clamp(playerScrollOffset, 0, maxScroll);

        if (detail.players().isEmpty()) {
            drawEmptyState(graphics, Component.translatable("gui.fpsm.map_select.players.empty"), left + width / 2, listTop + Math.min(30, availableHeight / 2));
            return;
        }

        int listRight = left + width;
        boolean freeForAll = isFreeForAll(summary);
        int teamX = left + Math.max(78, width / 2);
        graphics.enableScissor(left - 2, listTop, listRight + 2, listTop + visibleRows * ROW_HEIGHT);
        for (int i = playerScrollOffset; i < Math.min(detail.players().size(), playerScrollOffset + visibleRows); i++) {
            MapRoomPlayerInfo player = detail.players().get(i);
            int py = listTop + (i - playerScrollOffset) * ROW_HEIGHT;
            graphics.fill(left - 2, py - 1, listRight - 8, py + ROW_HEIGHT - 1, i % 2 == 0 ? FPSMGuiTheme.ROW_NORMAL : FPSMGuiTheme.BG_PANEL);
            int color = player.online() ? FPSMGuiTheme.TEXT_HIGHLIGHT : FPSMGuiTheme.TEXT_DISABLED;
            int nameWidth = freeForAll ? Math.max(40, listRight - left - 12) : Math.max(40, teamX - left - 8);
            drawClippedString(graphics, Component.literal(player.name()), left, py, color, nameWidth);
            if (!freeForAll) {
                drawClippedString(graphics, Component.literal(player.teamName()), teamX, py, player.spectator() ? FPSMGuiTheme.ST_SPECTATOR : FPSMGuiTheme.ST_ONLINE, Math.max(32, listRight - teamX - 12));
            }
        }
        graphics.disableScissor();

        drawScrollBar(graphics, listRight - 6, listTop, visibleRows * ROW_HEIGHT, playerScrollOffset, maxScroll, detail.players().size(), visibleRows);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int infoY = PANEL_TOP;
        int listTop = infoY + 74 + 14;
        int contentBottom = Math.min(height - PANEL_BOTTOM + 2, PANEL_TOP + 178);
        int availableHeight = Math.max(ROW_HEIGHT, contentBottom - listTop - 4);
        int visibleRows = Math.max(1, availableHeight / ROW_HEIGHT);
        int maxScroll = Math.max(0, detail == null ? 0 : detail.players().size() - visibleRows);
        playerScrollOffset -= (int) scrollY;
        playerScrollOffset = Mth.clamp(playerScrollOffset, 0, maxScroll);
        return true;
    }

    private void sendRoomAction(MapRoomActionC2SPacket.Action action, UUID target) {
        if (detail != null) {
            FPSMatch.sendToServer(new MapRoomActionC2SPacket(action, detail.summary().gameType(), detail.summary().mapName(), target));
            if (action == MapRoomActionC2SPacket.Action.LEAVE) {
                FPSMClient.getGlobalData().setMapRoomDetail(null);
                FPSMatch.sendToServer(new OpenMapSelectionC2SPacket());
                onClose();
            }
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
        if (manageButton != null) {
            manageButton.active = hasDetail && summary.currentPlayerOp();
        }
    }

    private void onJoinOrTeamManage() {
        if (detail != null && isFreeForAll(detail.summary()) && !detail.summary().currentPlayerJoined()) {
            sendRoomAction(MapRoomActionC2SPacket.Action.JOIN, new UUID(0L, 0L));
            return;
        }
        openTeamManage();
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
        if (detail != null && !isFreeForAll(detail.summary())) {
            FPSMMapSelectScreens.openChild(new FPSMTeamManageScreen(detail, this));
        }
    }

    private boolean isFreeForAll(MapRoomSummary summary) {
        return "csdm".equals(summary.gameType());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
