package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.screen.FPSMWidgetScreen;
import com.phasetranscrystal.fpsmatch.common.client.screen.FPSMWidgets;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class FPSMMapDetailScreen extends FPSMWidgetScreen implements FPSMMapDetailChildScreen {
    private static final int PANEL_TOP = 68;
    private static final int PANEL_BOTTOM = 82;
    private static final int ROW_HEIGHT = 12;

    private MapRoomDetail detail;
    private final Screen parent;
    private ButtonWidget joinButton;
    private ButtonWidget leaveButton;
    private ButtonWidget settingsButton;
    private ButtonWidget manageButton;
    private ButtonWidget shopButton;
    private ButtonWidget inviteButton;

    private DraggableScrollableWidgetGroup playerScroll;
    private DraggableScrollableWidgetGroup settingsScroll;

    public FPSMMapDetailScreen(MapRoomDetail detail, Screen parent) {
        super(Component.translatable("gui.fpsm.map_select.detail.title"));
        this.detail = detail;
        this.parent = parent;
    }

    @Override
    public void applyDetail(MapRoomDetail detail) {
        this.detail = detail;
        rebuildUI();
    }

    @Override
    protected void buildUI() {
        if (detail == null) return;
        MapRoomSummary summary = detail.summary();
        int centerX = width / 2;
        int left = centerX - 210;

        // 多层背景
        root.addWidget(new WidgetGroup(0, 0, width, height)
                .setBackground(new ColorRectTexture(0xFF444444)));
        root.addWidget(new WidgetGroup(1, 1, width - 2, height - 2)
                .setBackground(new ColorRectTexture(0xFF222222)));
        root.addWidget(new WidgetGroup(5, 5, width - 10, height - 10)
                .setBackground(new ColorRectTexture(0xFF666666)));
        root.addWidget(new WidgetGroup(6, 6, width - 12, height - 12)
                .setBackground(new ColorRectTexture(0xFF444444)));

        // 标题
        root.addWidget(new LabelWidget(centerX - font.width(title) / 2, 12, title.getString()).setTextColor(0xFFFFFFFF));
        Component sub = Component.literal(summary.gameType() + " / " + summary.mapName());
        root.addWidget(new LabelWidget(centerX - font.width(sub) / 2, 26, sub.getString()).setTextColor(0xFFB8D4E3));

        // 内容区背景
        int contentLeft = left - 6;
        int contentRight = centerX + 216;
        root.addWidget(new WidgetGroup(contentLeft, PANEL_TOP - 2, contentRight - contentLeft, height - PANEL_BOTTOM - PANEL_TOP + 4)
                .setBackground(new ColorRectTexture(0x77000000)));

        // 信息区
        int infoY = PANEL_TOP;
        root.addWidget(new LabelWidget(left, infoY, statusText(summary).getString()).setTextColor(statusColor(summary)));
        root.addWidget(new LabelWidget(left, infoY + 16, Component.translatable("gui.fpsm.map_select.detail.dimension", summary.dimension()).getString()).setTextColor(0xFFB8D4E3));
        root.addWidget(new LabelWidget(left, infoY + 32, Component.translatable("gui.fpsm.map_select.detail.area", summary.areaText()).getString()).setTextColor(0xFFB8D4E3));
        root.addWidget(new LabelWidget(left, infoY + 52, Component.translatable(detail.rulesKey()).getString()).setTextColor(0xFFD9E8F2));

        // 玩家列表
        buildPlayerList(left, infoY + 78);
        // 设置列表
        buildSettingsList(left + 224, infoY + 78);

        // 底部按钮
        int btnY = height - 52;
        joinButton = FPSMWidgets.button(centerX - 215, btnY, 80, 20,
                Component.translatable("gui.fpsm.map_select.join"),
                () -> sendRoomAction(MapRoomActionC2SPacket.Action.JOIN, new UUID(0L, 0L)));
        leaveButton = FPSMWidgets.button(centerX - 129, btnY, 80, 20,
                Component.translatable("gui.fpsm.map_select.leave"),
                () -> sendRoomAction(MapRoomActionC2SPacket.Action.LEAVE, new UUID(0L, 0L)));

        root.addWidget(joinButton);
        root.addWidget(leaveButton);

        settingsButton = FPSMWidgets.button(centerX - 80, height - 76, 76, 20,
                Component.translatable("gui.fpsm.map_select.settings"),
                () -> FPSMMapSelectScreens.openChild(new FPSMMapSettingsScreen(detail, this)));
        manageButton = FPSMWidgets.button(centerX + 4, height - 76, 76, 20,
                Component.translatable("gui.fpsm.map_select.manage"),
                () -> FPSMMapSelectScreens.openChild(new FPSMMapManageScreen(detail, this)));
        shopButton = FPSMWidgets.button(centerX + 88, height - 76, 96, 20,
                Component.translatable("gui.fpsm.map_detail.edit_shop"),
                () -> FPSMMapSelectScreens.openChild(new FPSMMapShopScreen(detail, this)));
        inviteButton = FPSMWidgets.button(centerX + 129, btnY, 80, 20,
                Component.translatable("gui.fpsm.map_select.invite"),
                () -> FPSMMapSelectScreens.openChild(new FPSMMapInviteScreen(detail, this)));

        root.addWidget(settingsButton);
        root.addWidget(manageButton);
        root.addWidget(shopButton);
        root.addWidget(inviteButton);

        root.addWidget(FPSMWidgets.button(centerX + 215, btnY, 80, 20,
                Component.translatable("gui.back"), this::onClose));

        updateButtons();
    }

    private void buildPlayerList(int left, int top) {
        MapRoomSummary summary = detail.summary();
        String titleStr = Component.translatable("gui.fpsm.map_select.players.title").getString()
                + " (" + summary.joinedPlayers() + "/" + maxPlayersText(summary) + ")";
        root.addWidget(new LabelWidget(left, top, titleStr).setTextColor(0xFFFFFFFF));

        int listTop = top + 14;
        int availableH = height - PANEL_BOTTOM - listTop;
        int visibleRows = Math.max(1, availableH / ROW_HEIGHT);
        int contentHeight = Math.max(visibleRows * ROW_HEIGHT, detail.players().size() * ROW_HEIGHT);

        playerScroll = new DraggableScrollableWidgetGroup(left - 2, listTop, 212, Math.min(availableH, visibleRows * ROW_HEIGHT));
        playerScroll.setScrollable(true);
        playerScroll.setYScrollBarWidth(4).setYBarStyle(new ColorRectTexture(0x33000000), new ColorRectTexture(0x88FFFFFF));

        WidgetGroup content = new WidgetGroup(2, 0, 206, contentHeight);
        content.setClientSideWidget();

        for (int i = 0; i < detail.players().size(); i++) {
            MapRoomPlayerInfo player = detail.players().get(i);
            int py = i * ROW_HEIGHT;
            int color = player.online() ? 0xFFE6F2FF : 0xFF8F9AA3;
            content.addWidget(new LabelWidget(0, py, player.name()).setTextColor(color));
            content.addWidget(new LabelWidget(100, py, player.teamName())
                    .setTextColor(player.spectator() ? 0xFFBBA7FF : 0xFF74E084));
        }

        playerScroll.addWidget(content);
        root.addWidget(playerScroll);
    }

    private void buildSettingsList(int left, int top) {
        root.addWidget(new LabelWidget(left, top,
                Component.translatable("gui.fpsm.map_select.settings.title").getString()).setTextColor(0xFFFFFFFF));

        int listTop = top + 14;
        int availableH = height - PANEL_BOTTOM - listTop;
        int visibleRows = Math.max(1, availableH / ROW_HEIGHT);
        int contentHeight = Math.max(visibleRows * ROW_HEIGHT, detail.settings().size() * ROW_HEIGHT);

        settingsScroll = new DraggableScrollableWidgetGroup(left - 2, listTop, 212, Math.min(availableH, visibleRows * ROW_HEIGHT));
        settingsScroll.setScrollable(true);
        settingsScroll.setYScrollBarWidth(4).setYBarStyle(new ColorRectTexture(0x33000000), new ColorRectTexture(0x88FFFFFF));

        WidgetGroup content = new WidgetGroup(2, 0, 206, contentHeight);
        content.setClientSideWidget();

        for (int i = 0; i < detail.settings().size(); i++) {
            MapRoomSettingInfo setting = detail.settings().get(i);
            int sy = i * ROW_HEIGHT;
            content.addWidget(new LabelWidget(0, sy,
                    Component.translatable(setting.translationKey()).getString())
                    .setTextColor(setting.editable() ? 0xFFE6F2FF : 0xFF8F9AA3)
                    .setHoverTooltips(Component.translatable(setting.translationKey() + ".desc")));
            content.addWidget(new LabelWidget(94, sy, setting.value()).setTextColor(0xFFB8D4E3));
        }

        settingsScroll.addWidget(content);
        root.addWidget(settingsScroll);
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
        joinButton.setActive(hasDetail && !joined && !summary.full() && (!summary.started() || summary.allowJoinInProgress()));
        leaveButton.setActive(joined);
        settingsButton.setActive(hasDetail && summary.currentPlayerOp());
        manageButton.setActive(hasDetail && summary.currentPlayerOp());
        shopButton.setActive(hasDetail && summary.currentPlayerOp());
        inviteButton.setActive(hasDetail && joined && !detail.availableInviteTargets().isEmpty());
    }

    private Component statusText(MapRoomSummary s) {
        if (s.debug()) return Component.translatable("gui.fpsm.map_select.status.debug");
        if (s.started()) return Component.translatable(s.allowJoinInProgress() ? "gui.fpsm.map_select.status.started_joinable" : "gui.fpsm.map_select.status.started");
        return Component.translatable("gui.fpsm.map_select.status.waiting");
    }

    private int statusColor(MapRoomSummary s) {
        if (s.debug()) return 0xFFFFC857;
        if (s.started()) return s.allowJoinInProgress() ? 0xFF66D9E8 : 0xFFFF6B6B;
        return 0xFF74E084;
    }

    private String maxPlayersText(MapRoomSummary s) { return s.maxPlayers() < 0 ? "∞" : Integer.toString(s.maxPlayers()); }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}