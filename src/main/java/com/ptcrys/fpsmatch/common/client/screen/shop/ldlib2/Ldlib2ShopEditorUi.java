package com.ptcrys.fpsmatch.common.client.screen.shop.ldlib2;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.math.Size;
import com.ptcrys.fpsmatch.common.client.screen.EditorShopContainer;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleButton;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessiblePanel;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.Ldlib2AccessibilityController;
import com.ptcrys.fpsmatch.core.shop.slot.ShopSlot;
import com.ptcrys.fpsmatch.compat.gun.GunCompatManager;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/** Responsive LDLib2 work surface for selecting and opening one fixed shop slot. */
public final class Ldlib2ShopEditorUi {
    private static final int CARD_WIDTH = 80;
    private static final int CARD_HEIGHT = 64;
    private static final int GAP = 6;

    private Ldlib2ShopEditorUi() {
    }

    public static View create(
            EditorShopContainer menu,
            int initialSelection,
            IntConsumer selectionChanged,
            IntConsumer editAction,
            Runnable closeAction
    ) {
        UIElement root = element(ShopEditorWidgetCatalog.ROOT);
        root.layout(layout -> layout.widthPercent(100).heightPercent(100));
        FPSMLdlib2Theme.root(root);

        UIElement header = element(ShopEditorWidgetCatalog.HEADER);
        UIElement categories = panel(ShopEditorWidgetCatalog.CATEGORIES);
        UIElement slots = panel(ShopEditorWidgetCatalog.SLOT_LIST);
        UIElement properties = panel(ShopEditorWidgetCatalog.PROPERTIES);
        UIElement actions = panel(ShopEditorWidgetCatalog.ACTIONS);

        Label system = label(ShopEditorWidgetCatalog.HEADER + ".system",
                Component.literal("FPSM // MAP SYSTEM  ·  SHOP CONFIGURATION"));
        FPSMLdlib2Theme.systemLabel(system);
        Label title = label(ShopEditorWidgetCatalog.HEADER + ".title",
                Component.translatable("gui.fpsm.shop_editor.title"));
        FPSMLdlib2Theme.title(title);
        Label identity = label(ShopEditorWidgetCatalog.SUBTITLE, Component.literal(
                menu.getGameType() + " / " + menu.getMapName() + " / " + menu.getTeamName()));
        FPSMLdlib2Theme.mapIdentity(identity);
        Label mode = label(ShopEditorWidgetCatalog.HEADER + ".mode",
                Component.translatable("gui.fpsm.shop_editor.edit_mode"));
        FPSMLdlib2Theme.status(mode, FPSMLdlib2Theme.ACCENT);
        header.addChildren(system, title, identity, mode);

        Label categoryTitle = section(ShopEditorWidgetCatalog.CATEGORIES + ".title",
                "gui.fpsm.shop_editor.categories");
        ScrollerView categoryScroller = new ScrollerView();
        categoryScroller.setId(ShopEditorWidgetCatalog.CATEGORY_TABS);
        FPSMLdlib2Theme.settingsScroller(categoryScroller);
        UIElement categoryList = element(ShopEditorWidgetCatalog.CATEGORY_TABS + ".content");
        categoryScroller.addScrollViewChild(categoryList);
        categories.addChildren(categoryTitle, categoryScroller);

        Label slotTitle = section(ShopEditorWidgetCatalog.SLOT_LIST + ".title",
                "gui.fpsm.shop_editor.slots");
        slots.addChild(slotTitle);

        Label propertyTitle = section(ShopEditorWidgetCatalog.PROPERTIES + ".title",
                "gui.fpsm.shop_editor.properties");
        Label selectedName = label(ShopEditorWidgetCatalog.PROPERTIES + ".name",
                Component.translatable("gui.fpsm.shop_editor.selection.none"));
        FPSMLdlib2Theme.body(selectedName);
        Label selectedType = muted(ShopEditorWidgetCatalog.PROPERTIES + ".type");
        Label selectedSlot = muted(ShopEditorWidgetCatalog.PROPERTIES + ".slot");
        Label selectedPrice = muted(ShopEditorWidgetCatalog.PROPERTIES + ".price");
        Label selectedQuantity = muted(ShopEditorWidgetCatalog.PROPERTIES + ".quantity");
        Label selectedGroup = muted(ShopEditorWidgetCatalog.PROPERTIES + ".group");
        properties.addChildren(propertyTitle, selectedName, selectedType, selectedSlot,
                selectedPrice, selectedQuantity, selectedGroup);

        Label actionStatus = label(ShopEditorWidgetCatalog.STATUS,
                Component.translatable("gui.fpsm.shop_editor.selection.none"));
        FPSMLdlib2Theme.status(actionStatus, FPSMLdlib2Theme.MUTED);
        AccessibleButton edit = new AccessibleButton();
        edit.setId(ShopEditorWidgetCatalog.EDIT_SELECTED);
        edit.setText(Component.translatable("gui.fpsm.shop_editor.edit_selected"));
        edit.setAccessibleHint(() -> Component.translatable("gui.fpsm.shop_editor.edit_selected.hint"));
        AccessibleButton close = new AccessibleButton();
        close.setId(ShopEditorWidgetCatalog.CLOSE);
        close.setText(Component.translatable("gui.back"));
        close.setOnClick(event -> closeAction.run());
        actions.addChildren(actionStatus, edit, close);

        root.addChildren(header, categories, slots, properties, actions);
        ModularUI ui = ModularUI.of(UI.of(root, size -> Size.of(
                Math.max(280, size.getWidth() - 12),
                Math.max(200, size.getHeight() - 12))));
        ui.setMenu(menu);
        View view = new View(ui, menu, header, categories, slots, properties, actions,
                system, title, identity, mode, categoryTitle, categoryScroller, categoryList,
                slotTitle, propertyTitle, selectedName, selectedType, selectedSlot, selectedPrice,
                selectedQuantity, selectedGroup, actionStatus, edit, close,
                selectionChanged, editAction);
        view.buildCategories();
        view.restoreSelection(initialSelection);
        return view;
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

    private static Label section(String id, String key) {
        Label label = label(id, Component.translatable(key));
        FPSMLdlib2Theme.sectionTitle(label);
        return label;
    }

    private static Label muted(String id) {
        Label label = label(id, Component.empty());
        FPSMLdlib2Theme.muted(label);
        return label;
    }

    public static final class View {
        private final ModularUI ui;
        private final EditorShopContainer menu;
        private final UIElement header;
        private final UIElement categories;
        private final UIElement slots;
        private final UIElement properties;
        private final UIElement actions;
        private final Label system;
        private final Label title;
        private final Label identity;
        private final Label mode;
        private final Label categoryTitle;
        private final ScrollerView categoryScroller;
        private final UIElement categoryList;
        private final Label slotTitle;
        private final Label propertyTitle;
        private final Label selectedName;
        private final Label selectedTypeLabel;
        private final Label selectedSlotLabel;
        private final Label selectedPrice;
        private final Label selectedQuantity;
        private final Label selectedGroup;
        private final Label actionStatus;
        private final AccessibleButton edit;
        private final AccessibleButton close;
        private final IntConsumer selectionChanged;
        private final IntConsumer editAction;
        private final Map<String, AccessibleButton> categoryButtons = new LinkedHashMap<>();
        private final Map<String, CategoryView> categoryViews = new LinkedHashMap<>();
        private final Map<Integer, AccessiblePanel> slotCards = new LinkedHashMap<>();
        private String selectedType;
        private int selectedSlotIndex = -1;
        private boolean opening;
        private boolean openTimedOut;

        private View(
                ModularUI ui, EditorShopContainer menu, UIElement header, UIElement categories,
                UIElement slots, UIElement properties, UIElement actions, Label system, Label title,
                Label identity, Label mode, Label categoryTitle, ScrollerView categoryScroller,
                UIElement categoryList, Label slotTitle, Label propertyTitle, Label selectedName,
                Label selectedTypeLabel, Label selectedSlotLabel, Label selectedPrice,
                Label selectedQuantity, Label selectedGroup, Label actionStatus,
                AccessibleButton edit, AccessibleButton close, IntConsumer selectionChanged,
                IntConsumer editAction
        ) {
            this.ui = ui;
            this.menu = menu;
            this.header = header;
            this.categories = categories;
            this.slots = slots;
            this.properties = properties;
            this.actions = actions;
            this.system = system;
            this.title = title;
            this.identity = identity;
            this.mode = mode;
            this.categoryTitle = categoryTitle;
            this.categoryScroller = categoryScroller;
            this.categoryList = categoryList;
            this.slotTitle = slotTitle;
            this.propertyTitle = propertyTitle;
            this.selectedName = selectedName;
            this.selectedTypeLabel = selectedTypeLabel;
            this.selectedSlotLabel = selectedSlotLabel;
            this.selectedPrice = selectedPrice;
            this.selectedQuantity = selectedQuantity;
            this.selectedGroup = selectedGroup;
            this.actionStatus = actionStatus;
            this.edit = edit;
            this.close = close;
            this.selectionChanged = selectionChanged;
            this.editAction = editAction;
            edit.setOnClick(event -> openSelected());
        }

        public ModularUI modularUI() {
            return ui;
        }

        public List<Ldlib2AccessibilityController.FocusTarget> focusTargets() {
            List<Ldlib2AccessibilityController.FocusTarget> targets = new ArrayList<>();
            targets.addAll(categoryButtons.values());
            CategoryView category = categoryViews.get(selectedType);
            if (category != null) {
                targets.addAll(category.cards());
            }
            targets.add(edit);
            targets.add(close);
            return targets;
        }

        public void applyResponsiveLayout(int width, int height) {
            ShopEditorLayoutModel model = ShopEditorLayoutModel.responsive(width, height);
            place(header, model.header());
            place(categories, model.categories());
            place(slots, model.slots());
            place(properties, model.properties());
            place(actions, model.actions());
            layoutHeader(model.header().width() - 4, model.header().height() - 4);
            layoutCategories(model.categories().width() - 4, model.categories().height() - 4, model.compact());
            layoutSlots(model.slots().width() - 4, model.slots().height() - 4);
            layoutProperties(model.properties().width() - 4,
                    model.properties().height() - 4, model.compact());
            layoutActions(model.actions().width() - 4, model.actions().height() - 4);
        }

        public void setOpeningState(boolean opening, boolean timedOut) {
            this.opening = opening;
            this.openTimedOut = timedOut;
            refreshSelection();
        }

        private void buildCategories() {
            List<String> types = new ArrayList<>(menu.getTypes().keySet());
            selectedType = types.isEmpty() ? null : types.get(0);
            for (String type : types) {
                AccessibleButton button = new AccessibleButton();
                button.setId(ShopEditorWidgetCatalog.CATEGORY_TABS + "." + type);
                button.setText(Component.translatable("fpsm.shop.title." + type));
                button.setAccessibleState(() -> type.equals(selectedType)
                        ? Component.translatable("gui.fpsm.shop_editor.category.selected")
                        : Component.empty());
                button.setOnClick(event -> selectCategory(type));
                categoryButtons.put(type, button);
                categoryList.addChild(button);
                CategoryView category = buildCategory(type);
                categoryViews.put(type, category);
                slots.addChild(category.root());
            }
            refreshCategory();
        }

        private CategoryView buildCategory(String type) {
            EditorShopContainer.TypeInfo info = menu.getTypes().get(type);
            UIElement root = element(ShopEditorWidgetCatalog.GROUP + "." + type);
            ScrollerView scroller = new ScrollerView();
            scroller.setId(ShopEditorWidgetCatalog.SLOT_LIST + "." + type);
            scroller.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL));
            FPSMLdlib2Theme.settingsScroller(scroller);
            UIElement content = element(ShopEditorWidgetCatalog.SLOT_LIST + "." + type + ".content");
            List<AccessiblePanel> cards = new ArrayList<>();
            List<ShopSlot> all = menu.getAllSlots();
            for (int localIndex = 0; localIndex < info.slotCount(); localIndex++) {
                int slotIndex = info.startIndex() + localIndex;
                ShopSlot shopSlot = slotIndex < all.size() ? all.get(slotIndex) : null;
                Component itemName = shopSlot == null
                        ? Component.translatable("gui.fpsm.shop_editor.empty_slot")
                        : shopSlot.process().getHoverName();
                AccessiblePanel card = new AccessiblePanel();
                card.setId(ShopEditorWidgetCatalog.ITEM + "." + type + "." + localIndex + ".card");
                card.setAccessibleName(Component.translatable(
                        "gui.fpsm.shop_editor.slot.accessible", localIndex + 1, itemName));
                card.setAccessibleState(() -> selectedSlotIndex == slotIndex
                        ? Component.translatable("gui.fpsm.shop_editor.slot.selected")
                        : Component.empty());
                if (shopSlot == null) {
                    card.setActive(false);
                    card.setAllowHitTest(false);
                    card.setFocusable(false);
                } else {
                    card.setOnActivate(() -> selectSlot(type, slotIndex));
                }
                FPSMLdlib2Theme.elevated(card);
                Label name = label(card.getId() + ".name", itemName);
                FPSMLdlib2Theme.muted(name);
                ItemSlot item = new ItemSlot(menu.slots.get(slotIndex));
                item.setId(ShopEditorWidgetCatalog.ITEM + "." + type + "." + localIndex);
                item.setAllowHitTest(false);
                item.setFocusable(false);
                FPSMLdlib2Theme.slot(item);
                Label price = label(card.getId() + ".price", shopSlot == null
                        ? Component.literal("-")
                        : Component.literal("$" + shopSlot.getDefaultCost()));
                FPSMLdlib2Theme.status(price, shopSlot == null
                        ? FPSMLdlib2Theme.DISABLED : FPSMLdlib2Theme.WARNING);
                absolute(name, 4, 4, CARD_WIDTH - 8, 12);
                absolute(item, 24, 15, 32, 32);
                absolute(price, 4, 50, CARD_WIDTH - 8, 12);
                card.addChildren(name, item, price);
                cards.add(card);
                slotCards.put(slotIndex, card);
                content.addChild(card);
            }
            scroller.addScrollViewChild(content);
            root.addChild(scroller);
            return new CategoryView(root, scroller, content, cards, info);
        }

        private void restoreSelection(int initialSelection) {
            if (initialSelection >= 0 && initialSelection < menu.getAllSlots().size()
                    && menu.getAllSlots().get(initialSelection) != null) {
                for (Map.Entry<String, EditorShopContainer.TypeInfo> entry : menu.getTypes().entrySet()) {
                    EditorShopContainer.TypeInfo info = entry.getValue();
                    if (initialSelection >= info.startIndex()
                            && initialSelection < info.startIndex() + info.slotCount()) {
                        selectedType = entry.getKey();
                        selectedSlotIndex = initialSelection;
                        break;
                    }
                }
            }
            refreshCategory();
            refreshSelection();
        }

        private void selectCategory(String type) {
            if (type.equals(selectedType)) {
                return;
            }
            selectedType = type;
            selectedSlotIndex = -1;
            opening = false;
            openTimedOut = false;
            selectionChanged.accept(-1);
            refreshCategory();
            refreshSelection();
        }

        private void selectSlot(String type, int slotIndex) {
            if (slotIndex < 0 || slotIndex >= menu.getAllSlots().size()
                    || menu.getAllSlots().get(slotIndex) == null) {
                return;
            }
            selectedType = type;
            selectedSlotIndex = slotIndex;
            opening = false;
            openTimedOut = false;
            selectionChanged.accept(slotIndex);
            refreshCategory();
            refreshSelection();
        }

        private void openSelected() {
            if (selectedSlotIndex >= 0 && !opening) {
                editAction.accept(selectedSlotIndex);
            }
        }

        private void refreshCategory() {
            categoryButtons.forEach((type, button) -> FPSMLdlib2Theme.button(button,
                    type.equals(selectedType)
                            ? FPSMLdlib2Theme.ButtonKind.PRIMARY
                            : FPSMLdlib2Theme.ButtonKind.QUIET));
            categoryViews.forEach((type, view) -> view.root().setDisplay(type.equals(selectedType)));
        }

        private void refreshSelection() {
            ShopSlot selected = selectedSlotIndex >= 0 && selectedSlotIndex < menu.getAllSlots().size()
                    ? menu.getAllSlots().get(selectedSlotIndex) : null;
            EditorShopContainer.TypeInfo info = selectedType == null ? null : menu.getTypes().get(selectedType);
            if (selected == null || info == null) {
                selectedName.setValue(Component.translatable("gui.fpsm.shop_editor.selection.none"));
                for (Label value : List.of(selectedTypeLabel, selectedSlotLabel, selectedPrice,
                        selectedQuantity, selectedGroup)) {
                    value.setValue(Component.empty());
                }
            } else {
                int localIndex = selectedSlotIndex - info.startIndex();
                selectedName.setValue(selected.process().getHoverName());
                selectedTypeLabel.setValue(Component.translatable("gui.fpsm.shop_editor.property.type",
                        Component.translatable("fpsm.shop.title." + selectedType)));
                selectedSlotLabel.setValue(Component.translatable("gui.fpsm.shop_editor.property.slot",
                        localIndex + 1, info.slotCount()));
                selectedPrice.setValue(Component.translatable("gui.fpsm.shop_editor.property.price",
                        selected.getDefaultCost()));
                selectedQuantity.setValue(GunCompatManager.isGun(selected.process())
                        ? Component.translatable("gui.fpsm.shop_editor.property.ammo",
                        selected.getAmmoCount())
                        : Component.empty());
                selectedGroup.setValue(Component.translatable("gui.fpsm.shop_editor.property.group",
                        selected.getGroupId()));
            }
            slotCards.forEach((index, card) -> {
                if (card.isActive()) {
                    if (index == selectedSlotIndex) {
                        FPSMLdlib2Theme.statusSurface(card, FPSMLdlib2Theme.ACCENT);
                    } else {
                        FPSMLdlib2Theme.elevated(card);
                    }
                }
            });
            if (opening) {
                actionStatus.setValue(Component.translatable("gui.fpsm.shop_editor.state.opening"));
                FPSMLdlib2Theme.status(actionStatus, FPSMLdlib2Theme.WARNING);
            } else if (openTimedOut) {
                actionStatus.setValue(Component.translatable("gui.fpsm.shop_editor.open.timeout"));
                FPSMLdlib2Theme.status(actionStatus, FPSMLdlib2Theme.DANGER);
            } else if (selected == null) {
                actionStatus.setValue(Component.translatable("gui.fpsm.shop_editor.selection.none"));
                FPSMLdlib2Theme.status(actionStatus, FPSMLdlib2Theme.MUTED);
            } else {
                actionStatus.setValue(Component.translatable("gui.fpsm.shop_editor.selection.ready"));
                FPSMLdlib2Theme.status(actionStatus, FPSMLdlib2Theme.SUCCESS);
            }
            FPSMLdlib2Theme.buttonState(edit, FPSMLdlib2Theme.ButtonKind.PRIMARY,
                    selected != null && !opening);
            FPSMLdlib2Theme.buttonState(close, FPSMLdlib2Theme.ButtonKind.QUIET, !opening);
        }

        private void layoutHeader(int width, int height) {
            if (height < 42) {
                absolute(system, 8, 1, Math.max(1, width - 16), 8);
                absolute(title, 8, 10, Math.max(1, width - 116), 16);
                absolute(identity, 8, 27, Math.max(1, width - 16), 10);
                absolute(mode, Math.max(8, width - 104), 10, 96, 16);
                return;
            }
            absolute(system, 8, 2, Math.max(1, width - 16), 10);
            absolute(title, 8, 13, Math.max(1, width - 116), 20);
            absolute(identity, 8, Math.max(30, height - 16), Math.max(1, width - 16), 14);
            absolute(mode, Math.max(8, width - 104), 13, 96, 18);
        }

        private void layoutCategories(int width, int height, boolean compact) {
            if (compact && height < 30) {
                int titleWidth = Math.min(88, Math.max(64, width / 5));
                absolute(categoryTitle, 8, 3, titleWidth - 8, 14);
                absolute(categoryScroller, titleWidth, 2,
                        Math.max(1, width - titleWidth - 6), Math.max(1, height - 4));
                int buttonWidth = 90;
                int contentWidth = Math.max(1, categoryButtons.size() * (buttonWidth + 4));
                categoryScroller.scrollerStyle(style -> style.mode(ScrollerMode.HORIZONTAL));
                categoryList.layout(layout -> layout.width(contentWidth)
                        .height(Math.max(1, height - 6)));
                int index = 0;
                for (AccessibleButton button : categoryButtons.values()) {
                    absolute(button, index * (buttonWidth + 4), 0, buttonWidth,
                            Math.max(16, height - 6));
                    index++;
                }
                return;
            }
            absolute(categoryTitle, 8, 5, Math.max(1, width - 16), 16);
            absolute(categoryScroller, 6, 24, Math.max(1, width - 12), Math.max(1, height - 30));
            int buttonWidth = compact ? 90 : Math.max(1, width - 14);
            int contentWidth = compact ? Math.max(1, categoryButtons.size() * (buttonWidth + 4)) : Math.max(1, width - 14);
            int contentHeight = compact ? 22 : Math.max(1, categoryButtons.size() * 24);
            categoryScroller.scrollerStyle(style -> style.mode(compact ? ScrollerMode.HORIZONTAL : ScrollerMode.VERTICAL));
            categoryList.layout(layout -> layout.width(contentWidth).height(contentHeight));
            int index = 0;
            for (AccessibleButton button : categoryButtons.values()) {
                int left = compact ? index * (buttonWidth + 4) : 0;
                int top = compact ? 0 : index * 24;
                absolute(button, left, top, buttonWidth, 20);
                index++;
            }
        }

        private void layoutSlots(int width, int height) {
            boolean shortViewport = height < 110;
            int scrollerTop = shortViewport ? 18 : 24;
            absolute(slotTitle, 8, shortViewport ? 2 : 5,
                    Math.max(1, width - 16), shortViewport ? 14 : 16);
            for (CategoryView category : categoryViews.values()) {
                int scrollerHeight = Math.max(1, height - scrollerTop - 2);
                absolute(category.root(), 6, scrollerTop,
                        Math.max(1, width - 12), scrollerHeight);
                absolute(category.scroller(), 0, 0,
                        Math.max(1, width - 12), scrollerHeight);
                int contentWidth = Math.max(CARD_WIDTH, width - 22);
                int columns = Math.max(1, contentWidth / (CARD_WIDTH + GAP));
                int rows = Math.max(1, (category.cards().size() + columns - 1) / columns);
                category.content().layout(layout -> layout.width(contentWidth)
                        .height(Math.max(CARD_HEIGHT, rows * (CARD_HEIGHT + GAP) + 4)));
                for (int index = 0; index < category.cards().size(); index++) {
                    int left = 2 + index % columns * (CARD_WIDTH + GAP);
                    int top = 2 + index / columns * (CARD_HEIGHT + GAP);
                    absolute(category.cards().get(index), left, top, CARD_WIDTH, CARD_HEIGHT);
                }
            }
        }

        private void layoutProperties(int width, int height, boolean compact) {
            if (compact && height < 48) {
                propertyTitle.setDisplay(false);
                absolute(selectedName, 8, 1, Math.max(1, width - 16), 11);
                int firstRowCell = Math.max(1, (width - 16) / 3);
                absolute(selectedTypeLabel, 8, 13, firstRowCell, 10);
                absolute(selectedSlotLabel, 8 + firstRowCell, 13, firstRowCell, 10);
                absolute(selectedPrice, 8 + firstRowCell * 2, 13, firstRowCell, 10);
                int secondRowCell = Math.max(1, (width - 16) / 2);
                absolute(selectedQuantity, 8, 24, secondRowCell, 10);
                absolute(selectedGroup, 8 + secondRowCell, 24, secondRowCell, 10);
                return;
            }
            propertyTitle.setDisplay(true);
            absolute(propertyTitle, 8, 5, Math.max(1, width - 16), 16);
            absolute(selectedName, 8, 23, Math.max(1, width - 16), 16);
            if (compact) {
                int cell = Math.max(1, (width - 16) / 3);
                absolute(selectedTypeLabel, 8, 42, cell, 14);
                absolute(selectedSlotLabel, 8 + cell, 42, cell, 14);
                absolute(selectedPrice, 8 + cell * 2, 42, cell, 14);
                absolute(selectedQuantity, 8, 58, cell, 14);
                absolute(selectedGroup, 8 + cell, 58, cell, 14);
            } else {
                absolute(selectedTypeLabel, 8, 44, Math.max(1, width - 16), 14);
                absolute(selectedSlotLabel, 8, 62, Math.max(1, width - 16), 14);
                absolute(selectedPrice, 8, 80, Math.max(1, width - 16), 14);
                absolute(selectedQuantity, 8, 98, Math.max(1, width - 16), 14);
                absolute(selectedGroup, 8, 116, Math.max(1, width - 16), 14);
            }
        }

        private void layoutActions(int width, int height) {
            int buttonHeight = Math.max(18, Math.min(24, height - 8));
            int closeWidth = Math.min(88, Math.max(60, width / 5));
            int editWidth = Math.min(132, Math.max(96, width / 4));
            int top = Math.max(2, (height - buttonHeight) / 2);
            absolute(close, Math.max(4, width - closeWidth - 6), top, closeWidth, buttonHeight);
            absolute(edit, Math.max(4, width - closeWidth - editWidth - 12), top, editWidth, buttonHeight);
            absolute(actionStatus, 8, top + 4, Math.max(1, width - closeWidth - editWidth - 26),
                    Math.max(12, buttonHeight - 4));
        }

        private static void place(UIElement element, ShopEditorLayoutModel.Rect rect) {
            absolute(element, rect.x() + 2, rect.y() + 2,
                    Math.max(1, rect.width() - 4), Math.max(1, rect.height() - 4));
        }
    }

    private static void absolute(UIElement element, int left, int top, int width, int height) {
        element.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto().left(left).top(top).width(width).height(height));
    }

    private record CategoryView(
            UIElement root,
            ScrollerView scroller,
            UIElement content,
            List<AccessiblePanel> cards,
            EditorShopContainer.TypeInfo info
    ) {
    }
}
