package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.screen.FPSMWidgetScreen;
import com.phasetranscrystal.fpsmatch.common.client.screen.FPSMWidgets;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.EditableShopInfo;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.phasetranscrystal.fpsmatch.common.packet.shop.OpenShopEditorC2SPacket;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class FPSMMapShopScreen extends FPSMWidgetScreen implements FPSMMapDetailChildScreen {
    private static final int PANEL_WIDTH = 360;
    private static final int ROW_HEIGHT = 26;
    private static final int LIST_TOP = 72;

    private MapRoomDetail detail;
    private final Screen parent;

    public FPSMMapShopScreen(MapRoomDetail detail, Screen parent) {
        super(Component.translatable("gui.fpsm.map_shop.title"));
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

        // 背景
        root.addWidget(new WidgetGroup(left - 6, LIST_TOP - 6, PANEL_WIDTH + 12, bottom - LIST_TOP + 12)
                .setBackground(new ColorRectTexture(0x77000000)));

        int contentHeight = Math.max(visibleRows * ROW_HEIGHT, detail.editableShops().size() * ROW_HEIGHT);
        DraggableScrollableWidgetGroup scroll = new DraggableScrollableWidgetGroup(left, LIST_TOP, PANEL_WIDTH, bottom - LIST_TOP);
        scroll.setScrollable(true);
        scroll.setYScrollBarWidth(4).setYBarStyle(new ColorRectTexture(0x33000000), new ColorRectTexture(0x88FFFFFF));

        WidgetGroup content = new WidgetGroup(0, 0, PANEL_WIDTH, contentHeight);
        content.setClientSideWidget();

        for (int i = 0; i < detail.editableShops().size(); i++) {
            EditableShopInfo shop = detail.editableShops().get(i);
            int y = i * ROW_HEIGHT;
            content.addWidget(new LabelWidget(0, y + 8, shop.displayName()).setTextColor(0xFFE6F2FF));
            content.addWidget(new LabelWidget(132, y + 8, shop.teamName()).setTextColor(0xFF74E084));

            EditableShopInfo s = shop;
            content.addWidget(FPSMWidgets.button(258, y + 3, 78, 20,
                    Component.translatable("gui.fpsm.map_shop.edit"),
                    () -> FPSMatch.sendToServer(new OpenShopEditorC2SPacket(s.gameType(), s.mapName(), s.teamName()))));
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