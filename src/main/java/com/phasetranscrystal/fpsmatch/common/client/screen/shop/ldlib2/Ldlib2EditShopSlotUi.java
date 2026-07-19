package com.phasetranscrystal.fpsmatch.common.client.screen.shop.ldlib2;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.screen.EditShopSlotMenu;
import com.phasetranscrystal.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme;
import com.phasetranscrystal.fpsmatch.common.packet.shop.OpenShopEditorC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.shop.SaveSlotDataC2SPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.appliedenergistics.yoga.YogaPositionType;

/** LDLib2 presentation tree for a single shop slot and player inventory. */
public final class Ldlib2EditShopSlotUi {
    private static final int ROOT_WIDTH = 360;
    private static final int ROOT_HEIGHT = 260;
    private static final int INV_LEFT = 18;
    private static final int INV_TOP = 150; // absolute content origin inside root

    private Ldlib2EditShopSlotUi() {
    }

    public static ModularUI create(EditShopSlotMenu menu, Runnable closeAction) {
        UIElement root = new UIElement().setId(ShopEditorWidgetCatalog.SLOT_EDITOR);
        FPSMLdlib2Theme.root(root);
        root.layout(layout -> layout.width(ROOT_WIDTH).height(ROOT_HEIGHT));

        Label title = new Label();
        title.setId(ShopEditorWidgetCatalog.HEADER + ".slot");
        title.setValue(Component.translatable("gui.fpsm.edit_shop_slot.title"));
        title.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(12).top(8).right(12).height(20));
        FPSMLdlib2Theme.title(title);

        UIElement selectedCard = new UIElement().setId(ShopEditorWidgetCatalog.ITEM + ".card");
        selectedCard.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(16).top(36).width(88).height(96));
        FPSMLdlib2Theme.elevated(selectedCard);
        Label itemCaption = label(ShopEditorWidgetCatalog.ITEM + ".caption", Component.translatable("gui.fpsm.shop_editor.item_label"), 8, 6, 72, 14);
        FPSMLdlib2Theme.muted(itemCaption);
        ItemSlot item = new ItemSlot(menu.slots.get(0));
        item.setId(ShopEditorWidgetCatalog.ITEM + ".selected");
        item.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(20).top(28).width(48).height(48));
        FPSMLdlib2Theme.slot(item);
        selectedCard.addChildren(itemCaption, item);

        TextField ammo = field(ShopEditorWidgetCatalog.AMMO, menu.getAmmo(), 188, 44, 0, menu);
        TextField price = field(ShopEditorWidgetCatalog.PRICE + ".selected", menu.getPrice(), 188, 76, 1, menu);
        TextField group = field(ShopEditorWidgetCatalog.GROUP + ".selected", menu.getGroupId(), 188, 108, 2, menu);
        Label ammoLabel = label(ShopEditorWidgetCatalog.AMMO + ".label", Component.translatable("gui.fpsm.dummy_ammo"), 112, 48, 68, 18);
        Label priceLabel = label(ShopEditorWidgetCatalog.PRICE + ".label", Component.translatable("gui.fpsm.price"), 112, 80, 68, 18);
        Label groupLabel = label(ShopEditorWidgetCatalog.GROUP + ".label", Component.translatable("gui.fpsm.group"), 112, 112, 68, 18);
        FPSMLdlib2Theme.muted(ammoLabel);
        FPSMLdlib2Theme.muted(priceLabel);
        FPSMLdlib2Theme.muted(groupLabel);
        if (!menu.isGun()) {
            ammo.setVisible(false);
            ammoLabel.setVisible(false);
        }

        UIElement inventoryPanel = new UIElement().setId(ShopEditorWidgetCatalog.SLOT_LIST + ".player");
        inventoryPanel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(12).top(136).width(336).height(90));
        FPSMLdlib2Theme.panel(inventoryPanel);
        Label invCaption = label(ShopEditorWidgetCatalog.SLOT_LIST + ".player.caption",
                Component.translatable("container.inventory"), 8, 4, 120, 12);
        FPSMLdlib2Theme.muted(invCaption);
        inventoryPanel.addChild(invCaption);
        for (int i = 1; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            int playerIndex = i - 1;
            int column = playerIndex % 9;
            int row = playerIndex < 27 ? playerIndex / 9 : 3;
            if (playerIndex >= 27) {
                column = playerIndex - 27;
            }
            int left = INV_LEFT + column * 18 - 12;
            int top = (playerIndex < 27 ? INV_TOP + row * 18 : INV_TOP + 58) - 136;
            ItemSlot playerSlot = new ItemSlot(slot);
            playerSlot.setId(ShopEditorWidgetCatalog.ITEM + ".player." + playerIndex);
            int finalLeft = left;
            int finalTop = top;
            playerSlot.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .left(finalLeft).top(finalTop).width(18).height(18));
            playerSlot.slotStyle(style -> style.isPlayerSlot(true).acceptQuickMove(true));
            FPSMLdlib2Theme.slot(playerSlot);
            inventoryPanel.addChild(playerSlot);
        }

        Button save = new Button();
        save.setId(ShopEditorWidgetCatalog.SAVE);
        save.setText(Component.translatable("gui.fpsm.shop_editor.save_button"));
        save.setOnClick(event -> {
            FPSMatch.sendToServer(new SaveSlotDataC2SPacket(menu.getAmmo(), menu.getPrice(), menu.getGroupId()));
            FPSMatch.sendToServer(new OpenShopEditorC2SPacket(menu.getGameType(), menu.getMapName(), menu.getTeamName()));
            closeAction.run();
        });
        FPSMLdlib2Theme.button(save, FPSMLdlib2Theme.ButtonKind.PRIMARY);
        save.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(42).bottom(10).width(120).height(22));

        Button close = new Button();
        close.setId(ShopEditorWidgetCatalog.CLOSE + ".slot");
        close.setText(Component.translatable("gui.back"));
        close.setOnClick(event -> {
            FPSMatch.sendToServer(new OpenShopEditorC2SPacket(menu.getGameType(), menu.getMapName(), menu.getTeamName()));
            closeAction.run();
        });
        FPSMLdlib2Theme.button(close, FPSMLdlib2Theme.ButtonKind.QUIET);
        close.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).right(42).bottom(10).width(120).height(22));

        root.addChildren(title, selectedCard, ammoLabel, ammo, priceLabel, price, groupLabel, group, inventoryPanel, save, close);
        ModularUI ui = ModularUI.of(UI.of(root));
        ui.setMenu(menu);
        return ui;
    }

    private static TextField field(String id, int value, int left, int top, int index, EditShopSlotMenu menu) {
        TextField field = new TextField();
        field.setId(id);
        field.setNumbersOnlyInt(-999999, 999999);
        field.setText(Integer.toString(value));
        FPSMLdlib2Theme.input(field, Component.literal("0"));
        field.setTextResponder(text -> {
            try {
                if (!text.isBlank()) {
                    if (index == 0) {
                        menu.setAmmo(Integer.parseInt(text));
                    } else if (index == 1) {
                        menu.setPrice(Integer.parseInt(text));
                    } else if (index == 2) {
                        menu.setGroupId(Integer.parseInt(text));
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        });
        field.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(left).top(top).width(90).height(22));
        return field;
    }

    private static Label label(String id, Component text, int left, int top, int width, int height) {
        Label label = new Label();
        label.setId(id);
        label.setValue(text);
        label.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(left).top(top).width(width).height(height));
        return label;
    }
}
