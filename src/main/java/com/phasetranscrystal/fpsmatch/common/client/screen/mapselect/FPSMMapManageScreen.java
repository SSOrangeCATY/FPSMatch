package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.screen.FPSMWidgetScreen;
import com.phasetranscrystal.fpsmatch.common.client.screen.FPSMWidgets;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomPlayerInfo;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class FPSMMapManageScreen extends FPSMWidgetScreen implements FPSMMapDetailChildScreen {
    private static final int PANEL_WIDTH = 440;
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 118;

    private MapRoomDetail detail;
    private final Screen parent;

    public FPSMMapManageScreen(MapRoomDetail detail, Screen parent) {
        super(Component.translatable("gui.fpsm.map_select.manage.title"));
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
        int centerX = width / 2;
        int left = centerX - PANEL_WIDTH / 2;
        int visibleRows = visibleRows();
        int bottom = Math.min(height - 84, LIST_TOP + visibleRows * ROW_HEIGHT);

        root.addWidget(new LabelWidget(centerX - font.width(title) / 2, 24, title.getString()).setTextColor(0xFFFFFFFF));
        Component sub = Component.literal(detail.summary().gameType() + " / " + detail.summary().mapName());
        root.addWidget(new LabelWidget(centerX - font.width(sub) / 2, 48, sub.getString()).setTextColor(0xFFB8D4E3));

        // 调试按钮行
        WidgetGroup debugBar = new WidgetGroup(centerX - 220, 74, 440, 20);
        debugBar.setClientSideWidget();
        debugBar.addWidget(FPSMWidgets.button(2, 0, 84, 20,
                Component.translatable("gui.fpsm.map_select.debug.start"),
                () -> sendAction(MapRoomActionC2SPacket.Action.DEBUG_START, new UUID(0L, 0L))).setActive(detail.summary().currentPlayerOp()));
        debugBar.addWidget(FPSMWidgets.button(90, 0, 84, 20,
                Component.translatable("gui.fpsm.map_select.debug.reset"),
                () -> sendAction(MapRoomActionC2SPacket.Action.DEBUG_RESET, new UUID(0L, 0L))).setActive(detail.summary().currentPlayerOp()));
        debugBar.addWidget(FPSMWidgets.button(178, 0, 84, 20,
                Component.translatable("gui.fpsm.map_select.debug.new_round"),
                () -> sendAction(MapRoomActionC2SPacket.Action.DEBUG_NEW_ROUND, new UUID(0L, 0L))).setActive(detail.summary().currentPlayerOp()));
        debugBar.addWidget(FPSMWidgets.button(266, 0, 84, 20,
                Component.translatable("gui.fpsm.map_select.debug.cleanup"),
                () -> sendAction(MapRoomActionC2SPacket.Action.DEBUG_CLEANUP, new UUID(0L, 0L))).setActive(detail.summary().currentPlayerOp()));
        debugBar.addWidget(FPSMWidgets.button(354, 0, 84, 20,
                Component.translatable("gui.fpsm.map_select.debug.switch"),
                () -> sendAction(MapRoomActionC2SPacket.Action.DEBUG_SWITCH, new UUID(0L, 0L))).setActive(detail.summary().currentPlayerOp()));
        root.addWidget(debugBar);

        // 背景
        root.addWidget(new WidgetGroup(left - 6, LIST_TOP - 6, PANEL_WIDTH + 12, bottom - LIST_TOP + 12)
                .setBackground(new ColorRectTexture(0x77000000)));

        root.addWidget(new LabelWidget(left, LIST_TOP - 22,
                Component.translatable("gui.fpsm.map_select.players.title").getString()).setTextColor(0xFFFFFFFF));

        // 玩家列表
        int contentHeight = Math.max(visibleRows * ROW_HEIGHT, detail.players().size() * ROW_HEIGHT);
        DraggableScrollableWidgetGroup scroll = new DraggableScrollableWidgetGroup(left, LIST_TOP, PANEL_WIDTH, bottom - LIST_TOP);
        scroll.setScrollable(true);
        scroll.setYScrollBarWidth(4).setYBarStyle(new ColorRectTexture(0x33000000), new ColorRectTexture(0x88FFFFFF));

        WidgetGroup content = new WidgetGroup(0, 0, PANEL_WIDTH, contentHeight);
        content.setClientSideWidget();

        for (int i = 0; i < detail.players().size(); i++) {
            MapRoomPlayerInfo player = detail.players().get(i);
            int y = i * ROW_HEIGHT;

            content.addWidget(new LabelWidget(0, y + 8, player.name())
                    .setTextColor(player.online() ? 0xFFE6F2FF : 0xFF8F9AA3));
            content.addWidget(new LabelWidget(132, y + 8, player.teamName())
                    .setTextColor(player.spectator() ? 0xFFBBA7FF : 0xFF74E084));
            content.addWidget(new LabelWidget(252, y + 8,
                    Component.translatable(player.online() ? "gui.fpsm.map_select.online" : "gui.fpsm.map_select.offline").getString())
                    .setTextColor(player.online() ? 0xFF74E084 : 0xFF8F9AA3));

            MapRoomPlayerInfo p = player;
            content.addWidget(FPSMWidgets.button(356, y + 2, 68, 20,
                    Component.translatable("gui.fpsm.map_select.kick"),
                    () -> sendAction(MapRoomActionC2SPacket.Action.KICK, p.uuid()))
                    .setActive(detail.summary().currentPlayerOp()));
        }

        scroll.addWidget(content);
        root.addWidget(scroll);

        root.addWidget(FPSMWidgets.button(centerX - 50, height - 52, 100, 20,
                Component.translatable("gui.back"), this::onClose));
    }

    private void sendAction(MapRoomActionC2SPacket.Action action, UUID target) {
        FPSMatch.sendToServer(new MapRoomActionC2SPacket(action, detail.summary().gameType(), detail.summary().mapName(), target));
    }

    private int visibleRows() { return Math.max(1, (height - LIST_TOP - 92) / ROW_HEIGHT); }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}