package com.phasetranscrystal.fpsmatch.common.client.screen.shop.ldlib2;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import com.phasetranscrystal.fpsmatch.common.client.screen.EditorShopContainer;
import com.phasetranscrystal.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.ArrayList;
import java.util.List;

/** LDLib2 presentation tree for the shop container. */
public final class Ldlib2ShopEditorUi {
    private static final int CARD_WIDTH = 72;
    private static final int CARD_HEIGHT = 78;
    private static final int CARD_GAP_X = 8;
    private static final int CARD_GAP_Y = 8;
    private static final int COLUMNS = 6;

    private Ldlib2ShopEditorUi() {
    }

    public static ModularUI create(EditorShopContainer menu, Runnable closeAction) {
        UIElement root = new UIElement().setId(ShopEditorWidgetCatalog.ROOT);
        FPSMLdlib2Theme.root(root);
        root.layout(layout -> layout.width(520).height(Math.max(280, menu.getImageHeight())));

        Label title = new Label();
        title.setId(ShopEditorWidgetCatalog.HEADER);
        title.setValue(Component.translatable("gui.fpsm.shop_editor.title"));
        title.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(10).top(8).right(110).height(20));
        FPSMLdlib2Theme.title(title);

        Label subtitle = new Label();
        subtitle.setId(ShopEditorWidgetCatalog.SUBTITLE);
        subtitle.setValue(Component.literal(menu.getGameType() + " / " + menu.getMapName() + " / " + menu.getTeamName()));
        subtitle.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(10).top(28).right(10).height(18));
        FPSMLdlib2Theme.muted(subtitle);

        Label mode = new Label();
        mode.setId(ShopEditorWidgetCatalog.HEADER + ".mode");
        mode.setValue(Component.translatable("gui.fpsm.shop_editor.edit_mode"));
        mode.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).right(10).top(8).width(92).height(18));
        FPSMLdlib2Theme.status(mode, FPSMLdlib2Theme.ACCENT);

        TabView tabs = new TabView();
        tabs.setId(ShopEditorWidgetCatalog.CATEGORIES);
        tabs.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(8).right(8).top(52).bottom(40));
        FPSMLdlib2Theme.panel(tabs);
        for (String type : new ArrayList<>(menu.getTypes().keySet())) {
            Tab tab = new Tab().setText(Component.translatable("fpsm.shop.title." + type));
            FPSMLdlib2Theme.tab(tab);
            tabs.addTab(tab, category(menu, type));
        }

        Button close = new Button();
        close.setId(ShopEditorWidgetCatalog.CLOSE);
        close.setText(Component.translatable("gui.back"));
        close.setOnClick(event -> closeAction.run());
        close.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(200).bottom(8).width(120).height(22));
        FPSMLdlib2Theme.button(close, FPSMLdlib2Theme.ButtonKind.QUIET);

        root.addChildren(title, subtitle, mode, tabs, close);
        ModularUI ui = ModularUI.of(UI.of(root));
        ui.setMenu(menu);
        return ui;
    }

    private static UIElement category(EditorShopContainer menu, String type) {
        EditorShopContainer.TypeInfo info = menu.getTypes().get(type);
        UIElement root = new UIElement().setId(ShopEditorWidgetCatalog.GROUP + "." + type);
        root.layout(layout -> layout.widthPercent(100).heightPercent(100));
        FPSMLdlib2Theme.panel(root);

        ScrollerView scroller = new ScrollerView();
        scroller.setId(ShopEditorWidgetCatalog.SLOT_LIST + "." + type);
        scroller.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(6).right(6).top(6).bottom(6));
        FPSMLdlib2Theme.elevated(scroller);

        UIElement slots = new UIElement();
        int rows = Math.max(1, (info.slotCount() + COLUMNS - 1) / COLUMNS);
        int contentHeight = Math.max(CARD_HEIGHT, rows * (CARD_HEIGHT + CARD_GAP_Y));
        slots.layout(layout -> layout.widthPercent(100).height(contentHeight));

        List<ShopSlot> shopSlots = menu.getAllSlots();
        for (int i = 0; i < info.slotCount(); i++) {
            int slotIndex = info.startIndex() + i;
            int column = i % COLUMNS;
            int row = i / COLUMNS;
            int left = 8 + column * (CARD_WIDTH + CARD_GAP_X);
            int top = 6 + row * (CARD_HEIGHT + CARD_GAP_Y);

            UIElement card = new UIElement().setId(ShopEditorWidgetCatalog.ITEM + "." + type + "." + i + ".card");
            int cardLeft = left;
            int cardTop = top;
            card.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .left(cardLeft).top(cardTop).width(CARD_WIDTH).height(CARD_HEIGHT));
            FPSMLdlib2Theme.elevated(card);

            ShopSlot shopSlot = slotIndex < shopSlots.size() ? shopSlots.get(slotIndex) : null;
            Component nameText = shopSlot == null
                    ? Component.translatable("gui.fpsm.shop_editor.empty_slot")
                    : shopSlot.process().getHoverName();
            Label name = new Label();
            name.setId(ShopEditorWidgetCatalog.ITEM + "." + type + "." + i + ".name");
            name.setValue(nameText);
            name.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(4).top(4).right(4).height(14));
            FPSMLdlib2Theme.muted(name);

            ItemSlot item = new ItemSlot(menu.slots.get(slotIndex));
            item.setId(ShopEditorWidgetCatalog.ITEM + "." + type + "." + i);
            item.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(15).top(22).width(42).height(42));
            FPSMLdlib2Theme.slot(item);

            int cost = shopSlot == null ? 0 : shopSlot.getDefaultCost();
            Label price = new Label();
            price.setId(ShopEditorWidgetCatalog.PRICE + "." + type + "." + i);
            price.setValue(Component.literal("$" + cost));
            price.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(4).right(4).bottom(4).height(14));
            FPSMLdlib2Theme.status(price, FPSMLdlib2Theme.WARNING);

            card.addChildren(name, item, price);
            slots.addChild(card);
        }

        scroller.addScrollViewChild(slots);
        root.addChild(scroller);
        return root;
    }
}
