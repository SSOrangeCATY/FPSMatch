package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.ldlib2;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.math.Size;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.EditableShopInfo;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.phasetranscrystal.fpsmatch.common.packet.shop.OpenShopEditorC2SPacket;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaPositionType;

/** Shop picker for opening the LDLib2 shop editor. */
public final class Ldlib2MapShopScreen extends Ldlib2MapChildScreen {
    private final Label subtitleLabel;
    private final VirtualScrollerView<EditableShopInfo> list;
    private final Label emptyLabel;

    public Ldlib2MapShopScreen(MapRoomDetail detail, Screen parent) {
        this(build(), detail, parent);
    }

    private Ldlib2MapShopScreen(Parts parts, MapRoomDetail detail, Screen parent) {
        super(parts.ui(), Component.translatable("gui.fpsm.map_shop.title"), detail, parent);
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
        list.setItems(detail.editableShops());
        list.refreshVisibleItems();
        emptyLabel.setVisible(detail.editableShops().isEmpty());
    }

    private static Parts build() {
        UIElement root = new UIElement().setId("fpsmatch.map_shop.root");
        root.layout(layout -> layout.widthPercent(100).heightPercent(100));
        FPSMLdlib2Theme.root(root);

        Label header = label("fpsmatch.map_shop.header", Component.translatable("gui.fpsm.map_shop.title"));
        header.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).right(18).top(12).height(20));
        FPSMLdlib2Theme.title(header);

        Label subtitle = label("fpsmatch.map_shop.subtitle", Component.empty());
        subtitle.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).right(18).top(34).height(16));
        FPSMLdlib2Theme.muted(subtitle);

        UIElement panel = new UIElement().setId("fpsmatch.map_shop.panel");
        panel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(16).right(16).top(56).bottom(56));
        FPSMLdlib2Theme.panel(panel);

        Label empty = label("fpsmatch.map_shop.empty", Component.translatable("gui.fpsm.map_shop.unsupported"));
        empty.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(14).right(14).top(20).height(18));
        FPSMLdlib2Theme.muted(empty);

        VirtualScrollerView<EditableShopInfo> list = new VirtualScrollerView<>();
        list.setId("fpsmatch.map_shop.list");
        list.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(8).right(8).top(8).bottom(8));
        list.virtualScrollerViewStyle(style -> style.estimatedItemHeight(28f));
        list.setItemUIProvider(shop -> {
            UIElement row = new UIElement().setId("fpsmatch.map_shop.row." + shop.teamName());
            row.layout(layout -> layout.widthPercent(100).height(26));
            FPSMLdlib2Theme.elevated(row);
            Label name = label("fpsmatch.map_shop.name." + shop.teamName(), Component.literal(shop.displayName()));
            name.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(10).top(6).width(180).height(14));
            FPSMLdlib2Theme.body(name);
            Label team = label("fpsmatch.map_shop.team." + shop.teamName(), Component.literal(shop.teamName()));
            team.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(200).top(6).width(120).height(14));
            FPSMLdlib2Theme.status(team, FPSMLdlib2Theme.SUCCESS);
            Button edit = new Button();
            edit.setId("fpsmatch.map_shop.edit." + shop.teamName());
            edit.setText(Component.translatable("gui.fpsm.map_shop.edit"));
            edit.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).right(8).top(2).width(72).height(22));
            FPSMLdlib2Theme.button(edit, FPSMLdlib2Theme.ButtonKind.PRIMARY);
            edit.setOnClick(e -> FPSMatch.sendToServer(new OpenShopEditorC2SPacket(shop.gameType(), shop.mapName(), shop.teamName())));
            row.addChildren(name, team, edit);
            return row;
        });

        panel.addChildren(empty, list);

        Button back = new Button();
        back.setId("fpsmatch.map_shop.back");
        back.setText(Component.translatable("gui.back"));
        back.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).bottom(16).width(96).height(24));
        FPSMLdlib2Theme.button(back, FPSMLdlib2Theme.ButtonKind.QUIET);

        root.addChildren(header, subtitle, panel, back);
        return new Parts(ModularUI.of(UI.of(root, size -> Size.of(size.getWidth(), size.getHeight()))), subtitle, list, empty, back);
    }

    private static Label label(String id, Component text) {
        Label label = new Label();
        label.setId(id);
        label.setValue(text);
        return label;
    }

    private record Parts(ModularUI ui, Label subtitle, VirtualScrollerView<EditableShopInfo> list, Label empty, Button back) {}
}
