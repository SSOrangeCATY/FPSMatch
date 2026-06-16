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

public class FPSMMapDetailScreen extends Screen implements FPSMMapDetailChildScreen {
    private static final int GUI_SHADOW_COLOR = 0x80000000;
    private static final int GUI_MAIN_BACKGROUND = 0xFF444444;
    private static final int GUI_INNER_BORDER = 0xFF666666;
    private static final int GUI_OUTER_BORDER = 0xFF222222;
    private static final int GUI_PADDING = 4;

    private static final int PANEL_TOP = 68;
    private static final int PANEL_BOTTOM = 82;
    private static final int ROW_HEIGHT = 12;

    private MapRoomDetail detail;
    private final Screen parent;
    private Button joinButton;
    private Button leaveButton;
    private Button settingsButton;
    private Button manageButton;
    private Button shopButton;
    private Button teamManageButton;
    private Button inviteButton;
    private int playerScrollOffset;
    private int settingsScrollOffset;

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
        joinButton = addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.map_select.join"), button -> sendRoomAction(MapRoomActionC2SPacket.Action.JOIN, new UUID(0L, 0L)))
                .bounds(centerX - 215, height - 52, 80, 20)
                .build());
        leaveButton = addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.map_select.leave"), button -> sendRoomAction(MapRoomActionC2SPacket.Action.LEAVE, new UUID(0L, 0L)))
                .bounds(centerX - 129, height - 52, 80, 20)
                .build());
        settingsButton = addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.map_select.settings"), button -> FPSMMapSelectScreens.openChild(new FPSMMapSettingsScreen(detail, this)))
                .bounds(centerX - 175, height - 76, 70, 20)
                .build());
        manageButton = addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.map_select.manage"), button -> FPSMMapSelectScreens.openChild(new FPSMMapManageScreen(detail, this)))
                .bounds(centerX - 99, height - 76, 70, 20)
                .build());
        shopButton = addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.map_detail.edit_shop"), button -> FPSMMapSelectScreens.openChild(new FPSMMapShopScreen(detail, this)))
                .bounds(centerX - 23, height - 76, 80, 20)
                .build());
        teamManageButton = addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.team_manage.button"), button -> openTeamManage())
                .bounds(centerX + 63, height - 76, 88, 20)
                .build());
        inviteButton = addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.map_select.invite"), button -> FPSMMapSelectScreens.openChild(new FPSMMapInviteScreen(detail, this)))
                .bounds(centerX + 129, height - 52, 80, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(centerX + 215, height - 52, 80, 20)
                .build());
        updateButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        renderMultiLayerBackground(graphics);

        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);

        if (detail != null) {
            MapRoomSummary summary = detail.summary();
            int left = width / 2 - 210;
            int right = width / 2 + 210;

            graphics.drawCenteredString(font, Component.literal(summary.gameType() + " / " + summary.mapName()), width / 2, 26, 0xFFB8D4E3);

            // 内容区背景
            graphics.fill(left - 6, PANEL_TOP - 2, right + 6, height - PANEL_BOTTOM + 2, 0x77000000);
            graphics.fill(left - 6, PANEL_TOP - 2, right + 6, PANEL_TOP - 1, 0xFF666666);
            graphics.fill(left - 6, height - PANEL_BOTTOM + 1, right + 6, height - PANEL_BOTTOM + 2, 0xFF666666);

            // 信息区
            int infoY = PANEL_TOP;
            graphics.drawString(font, Component.translatable("gui.fpsm.map_select.detail.status", statusText(summary)), left, infoY, statusColor(summary), false);
            graphics.drawString(font, Component.translatable("gui.fpsm.map_select.detail.dimension", summary.dimension()), left, infoY + 16, 0xFFB8D4E3, false);
            graphics.drawString(font, Component.translatable("gui.fpsm.map_select.detail.area", summary.areaText()), left, infoY + 32, 0xFFB8D4E3, false);
            graphics.drawString(font, Component.translatable(detail.rulesKey()), left, infoY + 52, 0xFFD9E8F2, false);

            // 玩家列表
            renderPlayers(graphics, left, infoY + 78);
            // 设置列表
            renderSettings(graphics, left + 224, infoY + 78, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPlayers(GuiGraphics graphics, int left, int top) {
        MapRoomSummary summary = detail.summary();
        graphics.drawString(font, Component.translatable("gui.fpsm.map_select.players.title")
                .append(" (" + summary.joinedPlayers() + "/" + maxPlayersText(summary) + ")"),
                left, top, 0xFFFFFFFF, false);

        int listTop = top + 14;
        int availableHeight = height - PANEL_BOTTOM - listTop;
        int visibleRows = Math.max(1, availableHeight / ROW_HEIGHT);
        int maxScroll = Math.max(0, detail.players().size() - visibleRows);
        playerScrollOffset = Mth.clamp(playerScrollOffset, 0, maxScroll);

        if (detail.players().isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.fpsm.map_select.players.empty"), left, listTop, 0xFFAAAAAA, false);
            return;
        }

        graphics.enableScissor(left - 2, listTop, left + 210, listTop + visibleRows * ROW_HEIGHT);
        for (int i = playerScrollOffset; i < Math.min(detail.players().size(), playerScrollOffset + visibleRows); i++) {
            MapRoomPlayerInfo player = detail.players().get(i);
            int py = listTop + (i - playerScrollOffset) * ROW_HEIGHT;
            int color = player.online() ? 0xFFE6F2FF : 0xFF8F9AA3;
            graphics.drawString(font, Component.literal(player.name()), left, py, color, false);
            graphics.drawString(font, Component.literal(player.teamName()), left + 100, py, player.spectator() ? 0xFFBBA7FF : 0xFF74E084, false);
        }
        graphics.disableScissor();

        renderScrollBar(graphics, left + 204, listTop, visibleRows * ROW_HEIGHT, playerScrollOffset, maxScroll, detail.players().size(), visibleRows);
    }

    private void renderSettings(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("gui.fpsm.map_select.settings.title"), left, top, 0xFFFFFFFF, false);

        int listTop = top + 14;
        int availableHeight = height - PANEL_BOTTOM - listTop;
        int visibleRows = Math.max(1, availableHeight / ROW_HEIGHT);
        int maxScroll = Math.max(0, detail.settings().size() - visibleRows);
        settingsScrollOffset = Mth.clamp(settingsScrollOffset, 0, maxScroll);

        if (detail.settings().isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.fpsm.map_select.settings.empty"), left, listTop, 0xFFAAAAAA, false);
            return;
        }

        graphics.enableScissor(left - 2, listTop, left + 210, listTop + visibleRows * ROW_HEIGHT);
        for (int i = settingsScrollOffset; i < Math.min(detail.settings().size(), settingsScrollOffset + visibleRows); i++) {
            MapRoomSettingInfo setting = detail.settings().get(i);
            int sy = listTop + (i - settingsScrollOffset) * ROW_HEIGHT;
            Component settingName = Component.translatable(setting.translationKey());
            graphics.drawString(font, settingName, left, sy, setting.editable() ? 0xFFE6F2FF : 0xFF8F9AA3, false);
            graphics.drawString(font, Component.literal(setting.value()), left + 94, sy, 0xFFB8D4E3, false);
        }
        graphics.disableScissor();

        renderScrollBar(graphics, left + 204, listTop, visibleRows * ROW_HEIGHT, settingsScrollOffset, maxScroll, detail.settings().size(), visibleRows);

        // 设置项悬浮提示
        for (int i = settingsScrollOffset; i < Math.min(detail.settings().size(), settingsScrollOffset + visibleRows); i++) {
            int sy = listTop + (i - settingsScrollOffset) * ROW_HEIGHT;
            if (mouseX >= left - 2 && mouseX <= left + 90 && mouseY >= sy && mouseY <= sy + ROW_HEIGHT) {
                MapRoomSettingInfo setting = detail.settings().get(i);
                graphics.renderTooltip(font, Component.translatable(setting.translationKey() + ".desc"), mouseX, mouseY);
                break;
            }
        }
    }

    private void renderMultiLayerBackground(GuiGraphics guiGraphics) {
        guiGraphics.fill(2, 2, width + 2, height + 2, GUI_SHADOW_COLOR);
        guiGraphics.fill(0, 0, width, 1, GUI_OUTER_BORDER);
        guiGraphics.fill(0, height - 1, width, height, GUI_OUTER_BORDER);
        guiGraphics.fill(0, 1, 1, height - 1, GUI_OUTER_BORDER);
        guiGraphics.fill(width - 1, 1, width, height - 1, GUI_OUTER_BORDER);
        guiGraphics.fill(1, 1, width - 1, height - 1, GUI_MAIN_BACKGROUND);
        guiGraphics.fill(1 + GUI_PADDING, 1 + GUI_PADDING, width - 1 - GUI_PADDING, 1 + GUI_PADDING + 1, GUI_INNER_BORDER);
        guiGraphics.fill(1 + GUI_PADDING, height - 1 - GUI_PADDING - 1, width - 1 - GUI_PADDING, height - 1 - GUI_PADDING, GUI_INNER_BORDER);
        guiGraphics.fill(1 + GUI_PADDING, 1 + GUI_PADDING + 1, 1 + GUI_PADDING + 1, height - 1 - GUI_PADDING - 1, GUI_INNER_BORDER);
        guiGraphics.fill(width - 1 - GUI_PADDING - 1, 1 + GUI_PADDING + 1, width - 1 - GUI_PADDING, height - 1 - GUI_PADDING - 1, GUI_INNER_BORDER);
    }

    private void renderScrollBar(GuiGraphics graphics, int barX, int barY, int barHeight, int scroll, int maxScroll, int totalItems, int visibleItems) {
        if (maxScroll <= 0) return;
        int barWidth = 4;
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0x33000000);
        int thumbSize = Math.max(10, barHeight * visibleItems / Math.max(1, totalItems));
        int thumbY = barY + scroll * (barHeight - thumbSize) / Math.max(1, maxScroll);
        graphics.fill(barX, thumbY, barX + barWidth, thumbY + thumbSize, 0x88FFFFFF);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int infoY = PANEL_TOP;
        int listTop = infoY + 78 + 14;
        int availableHeight = height - PANEL_BOTTOM - listTop;
        int visibleRows = Math.max(1, availableHeight / ROW_HEIGHT);

        int left = width / 2 - 210;
        int settingsLeft = left + 224;

        // 判断鼠标在设置区域还是玩家列表区域
        if (mouseX >= settingsLeft && mouseX <= settingsLeft + 210) {
            int maxScroll = Math.max(0, detail == null ? 0 : detail.settings().size() - visibleRows);
            settingsScrollOffset -= (int) scrollY;
            settingsScrollOffset = Mth.clamp(settingsScrollOffset, 0, maxScroll);
        } else {
            int maxScroll = Math.max(0, detail == null ? 0 : detail.players().size() - visibleRows);
            playerScrollOffset -= (int) scrollY;
            playerScrollOffset = Mth.clamp(playerScrollOffset, 0, maxScroll);
        }
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
            joinButton.active = hasDetail && !joined && !summary.full() && (!summary.started() || summary.allowJoinInProgress());
        }
        if (leaveButton != null) {
            leaveButton.active = joined;
        }
        if (settingsButton != null) {
            settingsButton.active = hasDetail && summary.currentPlayerOp();
        }
        if (manageButton != null) {
            manageButton.active = hasDetail && summary.currentPlayerOp();
        }
        if (shopButton != null) {
            shopButton.active = hasDetail && summary.currentPlayerOp();
        }
        if (teamManageButton != null) {
            teamManageButton.active = hasDetail && summary.currentPlayerOp();
        }
        if (inviteButton != null) {
            inviteButton.active = hasDetail && joined && !detail.availableInviteTargets().isEmpty();
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
            return 0xFFFFC857;
        }
        if (summary.started()) {
            return summary.allowJoinInProgress() ? 0xFF66D9E8 : 0xFFFF6B6B;
        }
        return 0xFF74E084;
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
            minecraft.setScreen(new FPSMTeamManageScreen(detail.summary().mapName()));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}