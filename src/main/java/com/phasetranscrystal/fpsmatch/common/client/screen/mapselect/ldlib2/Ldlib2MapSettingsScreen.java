package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.ldlib2;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.math.Size;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomSettingInfo;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomSettingsC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaPositionType;

/** Editable map-room settings list. */
public final class Ldlib2MapSettingsScreen extends Ldlib2MapChildScreen {
    private final Label subtitleLabel;
    private final VirtualScrollerView<MapRoomSettingInfo> list;
    private final Label emptyLabel;

    public Ldlib2MapSettingsScreen(MapRoomDetail detail, Screen parent) {
        this(build(), detail, parent);
    }

    private Ldlib2MapSettingsScreen(Parts parts, MapRoomDetail detail, Screen parent) {
        super(parts.ui(), Component.translatable("gui.fpsm.map_select.settings.title"), detail, parent);
        this.subtitleLabel = parts.subtitle();
        this.list = parts.list();
        this.emptyLabel = parts.empty();
        parts.back().setOnClick(e -> onClose());
        refreshContent();
    }

    @Override
    protected void onDetailApplied() {
        refreshContent();
    }

    private void refreshContent() {
        subtitleLabel.setValue(Component.literal(detail.summary().gameType() + " / " + detail.summary().mapName()));
        list.setItems(detail.settings());
        list.refreshVisibleItems();
        emptyLabel.setVisible(detail.settings().isEmpty());
    }

    void applySetting(MapRoomSettingInfo setting, String value) {
        FPSMatch.sendToServer(new MapRoomSettingsC2SPacket(
                detail.summary().gameType(), detail.summary().mapName(), setting.name(), value));
    }

    private static Parts build() {
        UIElement root = new UIElement().setId("fpsmatch.map_settings.root");
        root.layout(layout -> layout.widthPercent(100).heightPercent(100));
        FPSMLdlib2Theme.root(root);

        Label header = label("fpsmatch.map_settings.header", Component.translatable("gui.fpsm.map_select.settings.title"));
        header.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).right(18).top(12).height(20));
        FPSMLdlib2Theme.title(header);

        Label subtitle = label("fpsmatch.map_settings.subtitle", Component.empty());
        subtitle.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).right(18).top(34).height(16));
        FPSMLdlib2Theme.muted(subtitle);

        UIElement panel = new UIElement().setId("fpsmatch.map_settings.panel");
        panel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(16).right(16).top(56).bottom(56));
        FPSMLdlib2Theme.panel(panel);

        Label empty = label("fpsmatch.map_settings.empty", Component.translatable("gui.fpsm.map_select.settings.empty"));
        empty.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(14).right(14).top(20).height(18));
        FPSMLdlib2Theme.muted(empty);
        empty.setVisible(false);

        VirtualScrollerView<MapRoomSettingInfo> list = new VirtualScrollerView<>();
        list.setId("fpsmatch.map_settings.list");
        list.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(8).right(8).top(8).bottom(8));
        list.virtualScrollerViewStyle(style -> style.estimatedItemHeight(30f));
        list.setItemUIProvider(Ldlib2MapSettingsScreen::settingRow);

        panel.addChildren(empty, list);

        Button back = new Button();
        back.setId("fpsmatch.map_settings.back");
        back.setText(Component.translatable("gui.back"));
        back.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).bottom(16).width(96).height(24));
        FPSMLdlib2Theme.button(back, FPSMLdlib2Theme.ButtonKind.QUIET);

        root.addChildren(header, subtitle, panel, back);
        return new Parts(ModularUI.of(UI.of(root, size -> Size.of(size.getWidth(), size.getHeight()))), subtitle, list, empty, back);
    }

    private static UIElement settingRow(MapRoomSettingInfo setting) {
        UIElement row = new UIElement().setId("fpsmatch.map_settings.row." + setting.name());
        row.layout(layout -> layout.widthPercent(100).height(28).paddingLeft(6).paddingRight(6));
        FPSMLdlib2Theme.elevated(row);

        Label name = label("fpsmatch.map_settings.name." + setting.name(), Component.translatable(setting.translationKey()));
        name.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(8).top(6).widthPercent(42).height(16));
        FPSMLdlib2Theme.body(name);
        row.addChild(name);

        if (setting.type() == MapRoomSettingInfo.SettingType.BOOLEAN) {
            boolean value = Boolean.parseBoolean(setting.value());
            Button toggle = new Button();
            toggle.setId("fpsmatch.map_settings.toggle." + setting.name());
            toggle.setText(toggleLabel(value));
            toggle.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).right(8).top(3).width(148).height(22));
            FPSMLdlib2Theme.button(toggle, FPSMLdlib2Theme.ButtonKind.SECONDARY);
            toggle.setActive(setting.editable());
            toggle.setOnClick(e -> {
                boolean next = !Boolean.parseBoolean(setting.value());
                if (Minecraft.getInstance().screen instanceof Ldlib2MapSettingsScreen screen) {
                    screen.applySetting(setting, String.valueOf(next));
                }
            });
            row.addChild(toggle);
        } else {
            TextField field = new TextField();
            field.setId("fpsmatch.map_settings.field." + setting.name());
            field.setText(setting.value());
            field.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).right(92).top(3).width(148).height(22));
            FPSMLdlib2Theme.input(field, Component.literal(setting.defaultValue()));
            field.setActive(setting.editable());

            Button apply = new Button();
            apply.setId("fpsmatch.map_settings.apply." + setting.name());
            apply.setText(Component.translatable("gui.fpsm.map_select.apply"));
            apply.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).right(8).top(3).width(76).height(22));
            FPSMLdlib2Theme.button(apply, FPSMLdlib2Theme.ButtonKind.PRIMARY);
            apply.setActive(setting.editable());
            apply.setOnClick(e -> {
                if (Minecraft.getInstance().screen instanceof Ldlib2MapSettingsScreen screen) {
                    screen.applySetting(setting, field.getText());
                }
            });
            row.addChildren(field, apply);
        }
        return row;
    }

    private static Component toggleLabel(boolean value) {
        return Component.translatable(value ? "options.on" : "options.off");
    }

    private static Label label(String id, Component text) {
        Label label = new Label();
        label.setId(id);
        label.setValue(text);
        return label;
    }

    private record Parts(ModularUI ui, Label subtitle, VirtualScrollerView<MapRoomSettingInfo> list, Label empty, Button back) {}
}
