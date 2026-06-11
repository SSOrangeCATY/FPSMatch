package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomPlayerInfo;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomSettingInfo;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomSummary;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class FPSMMapDetailScreen extends Screen implements FPSMMapDetailChildScreen {
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_TOP = 70;

    private MapRoomDetail detail;
    private final Screen parent;
    private Button joinButton;
    private Button leaveButton;
    private Button settingsButton;
    private Button manageButton;
    private Button shopButton;
    private Button inviteButton;

    public FPSMMapDetailScreen(MapRoomDetail detail, Screen parent) {
        super(Component.translatable("gui.fpsm.map_select.detail.title"));
        this.detail = detail;
        this.parent = parent;
    }

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
                .bounds(centerX - 80, height - 76, 76, 20)
                .build());
        manageButton = addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.map_select.manage"), button -> FPSMMapSelectScreens.openChild(new FPSMMapManageScreen(detail, this)))
                .bounds(centerX + 4, height - 76, 76, 20)
                .build());
        shopButton = addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.map_detail.edit_shop"), button -> FPSMMapSelectScreens.openChild(new FPSMMapShopScreen(detail, this)))
                .bounds(centerX + 88, height - 76, 96, 20)
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
        graphics.drawCenteredString(font, title, width / 2, 24, 0xFFFFFFFF);
        if (detail != null) {
            MapRoomSummary summary = detail.summary();
            int left = width / 2 - PANEL_WIDTH / 2;
            int right = width / 2 + PANEL_WIDTH / 2;
            graphics.drawCenteredString(font, Component.literal(summary.gameType() + " / " + summary.mapName()), width / 2, 48, 0xFFB8D4E3);
            graphics.fill(left - 6, PANEL_TOP - 6, right + 6, height - 82, 0x77000000);
            graphics.drawString(font, Component.translatable("gui.fpsm.map_select.detail.status", statusText(summary)), left, PANEL_TOP, statusColor(summary), false);
            graphics.drawString(font, Component.translatable("gui.fpsm.map_select.detail.players", summary.joinedPlayers(), maxPlayersText(summary)), left + 160, PANEL_TOP, 0xFFE6F2FF, false);
            graphics.drawString(font, Component.translatable("gui.fpsm.map_select.detail.dimension", summary.dimension()), left, PANEL_TOP + 16, 0xFFB8D4E3, false);
            graphics.drawString(font, Component.translatable("gui.fpsm.map_select.detail.area", summary.areaText()), left, PANEL_TOP + 32, 0xFFB8D4E3, false);
            graphics.drawString(font, Component.translatable(detail.rulesKey()), left, PANEL_TOP + 52, 0xFFD9E8F2, false);
            renderPlayers(graphics, left, PANEL_TOP + 78);
            renderSettings(graphics, left + 224, PANEL_TOP + 78);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPlayers(GuiGraphics graphics, int left, int top) {
        graphics.drawString(font, Component.translatable("gui.fpsm.map_select.players.title"), left, top, 0xFFFFFFFF, false);
        int y = top + 14;
        if (detail.players().isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.fpsm.map_select.players.empty"), left, y, 0xFFAAAAAA, false);
            return;
        }
        for (int i = 0; i < Math.min(detail.players().size(), 8); i++) {
            MapRoomPlayerInfo player = detail.players().get(i);
            int color = player.online() ? 0xFFE6F2FF : 0xFF8F9AA3;
            graphics.drawString(font, Component.literal(player.name()), left, y, color, false);
            graphics.drawString(font, Component.literal(player.teamName()), left + 100, y, player.spectator() ? 0xFFBBA7FF : 0xFF74E084, false);
            y += 12;
        }
    }

    private void renderSettings(GuiGraphics graphics, int left, int top) {
        graphics.drawString(font, Component.translatable("gui.fpsm.map_select.settings.title"), left, top, 0xFFFFFFFF, false);
        int y = top + 14;
        if (detail.settings().isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.fpsm.map_select.settings.empty"), left, y, 0xFFAAAAAA, false);
            return;
        }
        for (int i = 0; i < Math.min(detail.settings().size(), 8); i++) {
            MapRoomSettingInfo setting = detail.settings().get(i);
            graphics.drawString(font, Component.literal(setting.name()), left, y, setting.editable() ? 0xFFE6F2FF : 0xFF8F9AA3, false);
            graphics.drawString(font, Component.literal(setting.value()), left + 94, y, 0xFFB8D4E3, false);
            y += 12;
        }
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
