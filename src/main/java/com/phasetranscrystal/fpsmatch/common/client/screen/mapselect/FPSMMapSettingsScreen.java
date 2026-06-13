package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.screen.FPSMWidgetScreen;
import com.phasetranscrystal.fpsmatch.common.client.screen.FPSMWidgets;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomSettingInfo;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomSettingsC2SPacket;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class FPSMMapSettingsScreen extends FPSMWidgetScreen implements FPSMMapDetailChildScreen {
    private static final int ROW_HEIGHT = 28;
    private static final int LIST_TOP = 62;

    private MapRoomDetail detail;
    private final Screen parent;
    private final List<TextFieldWidget> valueFields = new ArrayList<>();

    public FPSMMapSettingsScreen(MapRoomDetail detail, Screen parent) {
        super(Component.translatable("gui.fpsm.map_select.settings.title"));
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
        // 多层背景
        root.addWidget(new WidgetGroup(0, 0, width, height).setBackground(new ColorRectTexture(0xFF444444)));
        root.addWidget(new WidgetGroup(1, 1, width - 2, height - 2).setBackground(new ColorRectTexture(0xFF222222)));
        root.addWidget(new WidgetGroup(5, 5, width - 10, height - 10).setBackground(new ColorRectTexture(0xFF666666)));
        root.addWidget(new WidgetGroup(6, 6, width - 12, height - 12).setBackground(new ColorRectTexture(0xFF444444)));

        // 标题
        root.addWidget(new LabelWidget(width / 2 - font.width(title) / 2, 12, title.getString()).setTextColor(0xFFFFFFFF));
        if (detail != null) {
            Component sub = Component.literal(detail.summary().gameType() + " / " + detail.summary().mapName());
            root.addWidget(new LabelWidget(width / 2 - font.width(sub) / 2, 26, sub.getString()).setTextColor(0xFFB8D4E3));
        }

        int left = width / 2 - 210;
        int right = width / 2 + 210;
        int panelTop = LIST_TOP - 2;
        int panelBottom = height - 60;

        // 内容背景
        root.addWidget(new WidgetGroup(left - 6, panelTop, right - left + 12, panelBottom - panelTop)
                .setBackground(new ColorRectTexture(0x77000000)));

        // 滚动设置列表
        int contentHeight = Math.max(panelBottom - panelTop - 4, detail.settings().size() * ROW_HEIGHT);
        int visibleH = panelBottom - panelTop - 4;

        DraggableScrollableWidgetGroup scroll = new DraggableScrollableWidgetGroup(left - 4, panelTop + 2, right - left + 8, visibleH);
        scroll.setScrollable(true);
        scroll.setYScrollBarWidth(4).setYBarStyle(new ColorRectTexture(0x33000000), new ColorRectTexture(0x88FFFFFF));

        WidgetGroup content = new WidgetGroup(4, 0, right - left, contentHeight);
        content.setClientSideWidget();
        valueFields.clear();

        for (int i = 0; i < detail.settings().size(); i++) {
            MapRoomSettingInfo setting = detail.settings().get(i);
            int baseY = i * ROW_HEIGHT;

            // 设置名称
            content.addWidget(new LabelWidget(0, baseY + 8,
                    Component.translatable(setting.translationKey()).getString())
                    .setTextColor(setting.editable() ? 0xFFE6F2FF : 0xFF8F9AA3)
                    .setHoverTooltips(Component.translatable(setting.translationKey() + ".desc")));

            // 默认值
            content.addWidget(new LabelWidget(82, baseY + 8,
                    Component.translatable("gui.fpsm.map_select.setting.default", setting.defaultValue()).getString())
                    .setTextColor(0xFFB8D4E3));

            // 输入框
            TextFieldWidget field = new TextFieldWidget(176, baseY + 5, 150, 18,
                    () -> setting.value(), s -> {});
            field.setCurrentString(setting.value());
            field.setMaxStringLength(128);
            field.setActive(setting.editable());
            content.addWidget(field);
            valueFields.add(field);

            // 应用按钮
            MapRoomSettingInfo s = setting;
            TextFieldWidget f = field;
            content.addWidget(FPSMWidgets.button(334, baseY + 4, 70, 20,
                    Component.translatable("gui.fpsm.map_select.apply"),
                    () -> FPSMatch.sendToServer(new MapRoomSettingsC2SPacket(
                            detail.summary().gameType(), detail.summary().mapName(), s.name(), f.getCurrentString())))
                    .setActive(setting.editable()));
        }

        scroll.addWidget(content);
        root.addWidget(scroll);

        // 返回按钮
        root.addWidget(FPSMWidgets.button(width / 2 - 50, height - 52, 100, 20,
                Component.translatable("gui.back"), this::onClose));
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}