package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.screen.FPSMWidgetScreen;
import com.phasetranscrystal.fpsmatch.common.client.screen.FPSMWidgets;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomPlayerInfo;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class FPSMMapInviteScreen extends FPSMWidgetScreen implements FPSMMapDetailChildScreen {
    private static final int PANEL_WIDTH = 360;
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 72;

    private MapRoomDetail detail;
    private final Screen parent;

    public FPSMMapInviteScreen(MapRoomDetail detail, Screen parent) {
        super(Component.translatable("gui.fpsm.map_select.invite.title"));
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

        root.addWidget(new WidgetGroup(left - 6, LIST_TOP - 6, PANEL_WIDTH + 12, bottom - LIST_TOP + 12)
                .setBackground(new ColorRectTexture(0x77000000)));

        int contentHeight = Math.max(visibleRows * ROW_HEIGHT, detail.availableInviteTargets().size() * ROW_HEIGHT);
        DraggableScrollableWidgetGroup scroll = new DraggableScrollableWidgetGroup(left, LIST_TOP, PANEL_WIDTH, bottom - LIST_TOP);
        scroll.setScrollable(true);
        scroll.setYScrollBarWidth(4).setYBarStyle(new ColorRectTexture(0x33000000), new ColorRectTexture(0x88FFFFFF));

        WidgetGroup content = new WidgetGroup(0, 0, PANEL_WIDTH, contentHeight);
        content.setClientSideWidget();

        for (int i = 0; i < detail.availableInviteTargets().size(); i++) {
            MapRoomPlayerInfo player = detail.availableInviteTargets().get(i);
            int y = i * ROW_HEIGHT;
            content.addWidget(new LabelWidget(0, y + 8, player.name()).setTextColor(0xFFE6F2FF));
            content.addWidget(new LabelWidget(160, y + 8,
                    Component.translatable("gui.fpsm.map_select.online").getString()).setTextColor(0xFF74E084));

            MapRoomPlayerInfo p = player;
            content.addWidget(FPSMWidgets.button(260, y + 2, 78, 20,
                    Component.translatable("gui.fpsm.map_select.invite"),
                    () -> FPSMatch.sendToServer(new MapRoomActionC2SPacket(
                            MapRoomActionC2SPacket.Action.INVITE,
                            detail.summary().gameType(), detail.summary().mapName(), p.uuid()))));
        }

        scroll.addWidget(content);
        root.addWidget(scroll);

        root.addWidget(FPSMWidgets.button(centerX - 50, height - 52, 100, 20,
                Component.translatable("gui.back"), this::onClose));
    }

    private int visibleRows() { return Math.max(1, (height - LIST_TOP - 92) / ROW_HEIGHT); }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}