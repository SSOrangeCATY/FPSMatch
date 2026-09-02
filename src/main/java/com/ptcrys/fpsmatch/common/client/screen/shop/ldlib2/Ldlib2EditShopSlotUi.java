package com.ptcrys.fpsmatch.common.client.screen.shop.ldlib2;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.math.Size;
import com.ptcrys.fpsmatch.common.client.screen.EditShopSlotMenu;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleButton;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessiblePanel;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleTextField;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.Ldlib2AccessibilityController;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.IntConsumer;

/** Responsive LDLib2 work surface for one server-owned shop slot. */
public final class Ldlib2EditShopSlotUi {

    private Ldlib2EditShopSlotUi() {}

    public static View create(
                              EditShopSlotMenu menu,
                              Runnable saveAction,
                              Runnable closeAction,
                              Runnable copyHeldItemAction) {
        UIElement root = element(ShopEditorWidgetCatalog.SLOT_EDITOR);
        root.layout(layout -> layout.widthPercent(100).heightPercent(100));
        FPSMLdlib2Theme.root(root);

        UIElement header = element(ShopEditorWidgetCatalog.HEADER + ".slot");
        UIElement product = panel(ShopEditorWidgetCatalog.ITEM + ".panel");
        UIElement form = panel(ShopEditorWidgetCatalog.SLOT_EDITOR + ".form");
        UIElement inventory = panel(ShopEditorWidgetCatalog.SLOT_LIST + ".player");
        UIElement actions = panel(ShopEditorWidgetCatalog.ACTIONS + ".slot");

        Label system = label(ShopEditorWidgetCatalog.HEADER + ".slot.system",
                Component.literal("FPSM // MAP SYSTEM  ·  SLOT CONFIGURATION"));
        FPSMLdlib2Theme.systemLabel(system);
        Label title = label(ShopEditorWidgetCatalog.HEADER + ".slot.title",
                Component.translatable("gui.fpsm.edit_shop_slot.title"));
        FPSMLdlib2Theme.title(title);
        Label identity = label(ShopEditorWidgetCatalog.HEADER + ".slot.identity",
                Component.literal(menu.getGameType() + " / " + menu.getMapName() + " / " + menu.getTeamName() + " / " + menu.getShopType() + " #" + (menu.getSlotNum() + 1)));
        FPSMLdlib2Theme.mapIdentity(identity);
        header.addChildren(system, title, identity);

        AccessiblePanel productCard = new AccessiblePanel();
        productCard.setId(ShopEditorWidgetCatalog.ITEM + ".card");
        productCard.setAccessibleName(Component.translatable("gui.fpsm.shop_editor.item_label"));
        productCard.setAccessibleState(() -> menu.slots.get(0).getItem().getHoverName());
        productCard.setAccessibleHint(() -> Component.translatable(
                "gui.fpsm.shop_editor.item.replace.hint"));
        productCard.setOnActivate(copyHeldItemAction);
        FPSMLdlib2Theme.elevated(productCard);
        Label itemCaption = label(ShopEditorWidgetCatalog.ITEM + ".caption",
                Component.translatable("gui.fpsm.shop_editor.item_label"));
        FPSMLdlib2Theme.sectionTitle(itemCaption);
        ItemSlot item = new ItemSlot(menu.slots.get(0));
        item.setId(ShopEditorWidgetCatalog.ITEM + ".selected");
        item.setAllowHitTest(false);
        item.setFocusable(false);
        FPSMLdlib2Theme.slot(item);
        Label itemHint = label(ShopEditorWidgetCatalog.ITEM + ".hint",
                Component.translatable("gui.fpsm.shop_editor.item.replace"));
        FPSMLdlib2Theme.muted(itemHint);
        productCard.addChildren(itemCaption, item, itemHint);
        product.addChild(productCard);

        Label ammoLabel = caption(ShopEditorWidgetCatalog.AMMO + ".label", "gui.fpsm.dummy_ammo");
        Label priceLabel = caption(ShopEditorWidgetCatalog.PRICE + ".label", "gui.fpsm.price");
        Label groupLabel = caption(ShopEditorWidgetCatalog.GROUP + ".label", "gui.fpsm.group");
        AccessibleTextField ammo = field(ShopEditorWidgetCatalog.AMMO, menu.getAmmo(), 0, 999_999,
                menu::setAmmo, "gui.fpsm.dummy_ammo");
        AccessibleTextField price = field(ShopEditorWidgetCatalog.PRICE + ".selected", menu.getPrice(),
                0, 1_000_000, menu::setPrice, "gui.fpsm.price");
        AccessibleTextField group = field(ShopEditorWidgetCatalog.GROUP + ".selected", menu.getGroupId(),
                -1, 999_999, menu::setGroupId, "gui.fpsm.group");
        if (!menu.isGun()) {
            ammo.setVisible(false);
            ammo.setActive(false);
            ammo.setAllowHitTest(false);
            ammo.setFocusable(false);
            ammoLabel.setVisible(false);
        }
        form.addChildren(ammoLabel, ammo, priceLabel, price, groupLabel, group);

        Label inventoryCaption = label(ShopEditorWidgetCatalog.SLOT_LIST + ".player.caption",
                Component.translatable("container.inventory"));
        FPSMLdlib2Theme.sectionTitle(inventoryCaption);
        List<ItemSlot> playerSlots = new ArrayList<>();
        for (int index = 1; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            ItemSlot playerSlot = new ItemSlot(slot);
            playerSlot.setId(ShopEditorWidgetCatalog.ITEM + ".player." + (index - 1));
            playerSlot.slotStyle(style -> style.isPlayerSlot(true).acceptQuickMove(true));
            FPSMLdlib2Theme.slot(playerSlot);
            playerSlots.add(playerSlot);
            inventory.addChild(playerSlot);
        }
        inventory.addChild(inventoryCaption);

        Label status = label(ShopEditorWidgetCatalog.STATUS,
                Component.translatable("gui.fpsm.shop_editor.state.editing"));
        FPSMLdlib2Theme.status(status, FPSMLdlib2Theme.ACCENT);
        AccessibleButton save = new AccessibleButton();
        save.setId(ShopEditorWidgetCatalog.SAVE);
        save.setText(Component.translatable("gui.fpsm.shop_editor.save_button"));
        save.setAccessibleHint(() -> Component.translatable("gui.fpsm.shop_editor.save.hint"));
        save.setOnClick(event -> saveAction.run());
        AccessibleButton close = new AccessibleButton();
        close.setId(ShopEditorWidgetCatalog.CLOSE + ".slot");
        close.setText(Component.translatable("gui.back"));
        close.setOnClick(event -> closeAction.run());
        actions.addChildren(status, save, close);

        root.addChildren(header, product, form, inventory, actions);
        ModularUI ui = ModularUI.of(UI.of(root, size -> Size.of(
                Math.max(280, size.getWidth() - 12),
                Math.max(220, size.getHeight() - 12))));
        ui.setMenu(menu);
        return new View(ui, menu, header, product, form, inventory, actions, system, title,
                identity, productCard, itemCaption, item, itemHint, ammoLabel, ammo, priceLabel,
                price, groupLabel, group, inventoryCaption, playerSlots, status, save, close);
    }

    private static UIElement element(String id) {
        return new UIElement().setId(id);
    }

    private static UIElement panel(String id) {
        UIElement panel = element(id);
        FPSMLdlib2Theme.panel(panel);
        return panel;
    }

    private static Label label(String id, Component value) {
        Label label = new Label();
        label.setId(id);
        label.setValue(value);
        label.setAllowHitTest(false);
        label.setFocusable(false);
        return label;
    }

    private static Label caption(String id, String key) {
        Label label = label(id, Component.translatable(key));
        FPSMLdlib2Theme.muted(label);
        return label;
    }

    private static AccessibleTextField field(
                                             String id,
                                             int value,
                                             int minimum,
                                             int maximum,
                                             IntConsumer responder,
                                             String labelKey) {
        AccessibleTextField field = new AccessibleTextField();
        field.setId(id);
        field.setAccessibleName(Component.translatable(labelKey));
        field.setAccessibleHint(() -> Component.translatable(
                "gui.fpsm.shop_editor.numeric.hint", minimum, maximum));
        field.setNumbersOnlyInt(minimum, maximum);
        field.setText(Integer.toString(value));
        FPSMLdlib2Theme.input(field, Component.literal(Integer.toString(minimum)));
        field.setTextResponder(text -> parse(text, minimum, maximum).ifPresent(responder));
        return field;
    }

    private static OptionalInt parse(String text, int minimum, int maximum) {
        try {
            int value = Integer.parseInt(text);
            return value >= minimum && value <= maximum ? OptionalInt.of(value) : OptionalInt.empty();
        } catch (NumberFormatException ignored) {
            return OptionalInt.empty();
        }
    }

    public static final class View {

        private final ModularUI ui;
        private final EditShopSlotMenu menu;
        private final UIElement header;
        private final UIElement product;
        private final UIElement form;
        private final UIElement inventory;
        private final UIElement actions;
        private final Label system;
        private final Label title;
        private final Label identity;
        private final AccessiblePanel productCard;
        private final Label itemCaption;
        private final ItemSlot item;
        private final Label itemHint;
        private final Label ammoLabel;
        private final AccessibleTextField ammo;
        private final Label priceLabel;
        private final AccessibleTextField price;
        private final Label groupLabel;
        private final AccessibleTextField group;
        private final Label inventoryCaption;
        private final List<ItemSlot> playerSlots;
        private final Label status;
        private final AccessibleButton save;
        private final AccessibleButton close;

        private View(
                     ModularUI ui, EditShopSlotMenu menu, UIElement header, UIElement product,
                     UIElement form, UIElement inventory, UIElement actions, Label system, Label title,
                     Label identity, AccessiblePanel productCard, Label itemCaption, ItemSlot item,
                     Label itemHint, Label ammoLabel, AccessibleTextField ammo, Label priceLabel,
                     AccessibleTextField price, Label groupLabel, AccessibleTextField group,
                     Label inventoryCaption, List<ItemSlot> playerSlots, Label status,
                     AccessibleButton save, AccessibleButton close) {
            this.ui = ui;
            this.menu = menu;
            this.header = header;
            this.product = product;
            this.form = form;
            this.inventory = inventory;
            this.actions = actions;
            this.system = system;
            this.title = title;
            this.identity = identity;
            this.productCard = productCard;
            this.itemCaption = itemCaption;
            this.item = item;
            this.itemHint = itemHint;
            this.ammoLabel = ammoLabel;
            this.ammo = ammo;
            this.priceLabel = priceLabel;
            this.price = price;
            this.groupLabel = groupLabel;
            this.group = group;
            this.inventoryCaption = inventoryCaption;
            this.playerSlots = List.copyOf(playerSlots);
            this.status = status;
            this.save = save;
            this.close = close;
        }

        public ModularUI modularUI() {
            return ui;
        }

        public Label statusLabel() {
            return status;
        }

        public AccessibleButton saveButton() {
            return save;
        }

        public AccessibleButton closeButton() {
            return close;
        }

        public List<Ldlib2AccessibilityController.FocusTarget> focusTargets() {
            List<Ldlib2AccessibilityController.FocusTarget> targets = new ArrayList<>();
            targets.add(productCard);
            if (menu.isGun()) {
                targets.add(ammo);
            }
            targets.add(price);
            targets.add(group);
            targets.add(save);
            targets.add(close);
            return List.copyOf(targets);
        }

        public boolean inputValid() {
            return (!menu.isGun() || parse(ammo.getText(), 0, 999_999).isPresent()) && parse(price.getText(), 0, 1_000_000).isPresent() && parse(group.getText(), -1, 999_999).isPresent();
        }

        public Draft draft() {
            return new Draft(ammo.getText(), price.getText(), group.getText());
        }

        public void restoreDraft(Draft draft) {
            if (draft == null) {
                return;
            }
            ammo.setText(draft.ammo());
            price.setText(draft.price());
            group.setText(draft.group());
        }

        public void applyResponsiveLayout(int width, int height) {
            boolean compact = width < 440 || height < 300;
            int headerHeight = compact ? 42 : 48;
            int actionHeight = compact ? 46 : 38;
            int inventoryHeight = 90;
            int mainHeight = Math.max(42, height - headerHeight - actionHeight - inventoryHeight);
            int inventoryTop = Math.min(height - actionHeight - inventoryHeight,
                    headerHeight + mainHeight);

            absolute(header, 2, 2, width - 4, headerHeight - 4);
            int productWidth = compact ? 98 : Math.min(132, width / 3);
            absolute(product, 2, headerHeight + 2, productWidth - 4, mainHeight - 4);
            absolute(form, productWidth + 2, headerHeight + 2,
                    width - productWidth - 4, mainHeight - 4);
            absolute(inventory, 2, inventoryTop + 2, width - 4, inventoryHeight - 4);
            absolute(actions, 2, height - actionHeight + 2, width - 4, actionHeight - 4);

            layoutHeader(width - 4, headerHeight - 4);
            layoutProduct(productWidth - 4, mainHeight - 4, compact);
            layoutForm(width - productWidth - 4, mainHeight - 4, compact);
            layoutInventory(width - 4);
            layoutActions(width - 4, actionHeight - 4, compact);
        }

        private void layoutHeader(int width, int height) {
            absolute(system, 8, 2, Math.max(1, width - 16), 10);
            absolute(title, 8, 13, Math.max(1, width - 16), 18);
            absolute(identity, 8, Math.max(28, height - 14), Math.max(1, width - 16), 12);
        }

        private void layoutProduct(int width, int height, boolean compact) {
            absolute(productCard, 4, 4, Math.max(1, width - 8), Math.max(1, height - 8));
            absolute(itemCaption, 5, 3, Math.max(1, width - 10), 12);
            int itemSize = Math.max(24, Math.min(compact ? 30 : 38, height - 23));
            absolute(item, Math.max(4, (width - itemSize) / 2), 15, itemSize, itemSize);
            absolute(itemHint, 5, Math.max(16, height - 14), Math.max(1, width - 10), 11);
        }

        private void layoutForm(int width, int height, boolean compact) {
            if (compact) {
                int cell = Math.max(1, width / 3);
                layoutField(ammoLabel, ammo, 0, cell, height);
                layoutField(priceLabel, price, cell, cell, height);
                layoutField(groupLabel, group, cell * 2, width - cell * 2, height);
                return;
            }
            int rowHeight = Math.max(18, Math.min(26, height / 3));
            int labelWidth = Math.min(82, Math.max(58, width / 3));
            layoutRow(ammoLabel, ammo, 6, 4, width, rowHeight, labelWidth);
            layoutRow(priceLabel, price, 6, 4 + rowHeight, width, rowHeight, labelWidth);
            layoutRow(groupLabel, group, 6, 4 + rowHeight * 2, width, rowHeight, labelWidth);
        }

        private static void layoutField(
                                        Label label, AccessibleTextField field, int left, int width, int height) {
            absolute(label, left + 4, 4, Math.max(1, width - 8), 12);
            absolute(field, left + 4, 18, Math.max(1, width - 8), Math.max(18, height - 24));
        }

        private static void layoutRow(
                                      Label label, AccessibleTextField field, int left, int top,
                                      int width, int rowHeight, int labelWidth) {
            absolute(label, left, top + 5, labelWidth, 14);
            absolute(field, left + labelWidth, top + 1,
                    Math.max(1, width - labelWidth - left - 6), Math.max(18, rowHeight - 3));
        }

        private void layoutInventory(int width) {
            absolute(inventoryCaption, 8, 3, Math.max(1, width - 16), 12);
            int gridWidth = 9 * 18;
            int gridLeft = Math.max(6, (width - gridWidth) / 2);
            for (int index = 0; index < playerSlots.size(); index++) {
                int column = index % 9;
                int row = index < 27 ? index / 9 : 3;
                absolute(playerSlots.get(index), gridLeft + column * 18, 14 + row * 18, 18, 18);
            }
        }

        private void layoutActions(int width, int height, boolean compact) {
            int buttonHeight = compact ? 20 : Math.max(20, height - 8);
            int closeWidth = Math.min(102, Math.max(76, width / 4));
            int saveWidth = Math.min(132, Math.max(106, width / 3));
            int buttonTop = compact ? Math.max(20, height - buttonHeight - 2) : Math.max(2, (height - buttonHeight) / 2);
            absolute(close, width - closeWidth - 6, buttonTop, closeWidth, buttonHeight);
            absolute(save, width - closeWidth - saveWidth - 12, buttonTop, saveWidth, buttonHeight);
            int statusWidth = compact ? width - 12 : width - closeWidth - saveWidth - 24;
            absolute(status, 8, compact ? 4 : buttonTop + 4,
                    Math.max(1, statusWidth), compact ? 14 : Math.max(12, buttonHeight - 4));
        }
    }

    public record Draft(String ammo, String price, String group) {}

    private static void absolute(UIElement element, int left, int top, int width, int height) {
        element.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto().left(left).top(top)
                .width(Math.max(1, width)).height(Math.max(1, height)));
    }
}
