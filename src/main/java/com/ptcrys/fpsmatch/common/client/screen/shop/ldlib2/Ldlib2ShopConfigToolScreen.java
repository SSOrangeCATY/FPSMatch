package com.ptcrys.fpsmatch.common.client.screen.shop.ldlib2;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.math.Size;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleButton;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleModularUIScreen;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleSelector;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Backdrop;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.Ldlib2AccessibilityController;
import com.ptcrys.fpsmatch.common.client.screen.shop.ShopEditorNavigation;
import com.ptcrys.fpsmatch.common.packet.mapselect.EditableShopInfo;
import com.ptcrys.fpsmatch.common.packet.shop.OpenShopConfigToolScreenS2CPacket;
import com.ptcrys.fpsmatch.common.packet.shop.OpenShopEditorC2SPacket;
import com.ptcrys.fpsmatch.common.packet.shop.ShopConfigToolActionC2SPacket;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** LDLib2 overview for the handheld shop configuration tool. */
public final class Ldlib2ShopConfigToolScreen extends AccessibleModularUIScreen {

    private static final int OPEN_TIMEOUT_TICKS = 200;

    private final UIElement filters;
    private final AccessibleSelector<String> typeSelector;
    private final AccessibleSelector<String> mapSelector;
    private final VirtualScrollerView<EditableShopInfo> shopList;
    private final Label emptyLabel;
    private final Label statusLabel;
    private final AccessibleButton refreshButton;
    private final AccessibleButton closeButton;
    private OpenShopConfigToolScreenS2CPacket data;
    private boolean openingEditor;
    private int openingTicks;

    public Ldlib2ShopConfigToolScreen(OpenShopConfigToolScreenS2CPacket data) {
        this(build(), data);
    }

    private Ldlib2ShopConfigToolScreen(Parts parts, OpenShopConfigToolScreenS2CPacket data) {
        super(parts.ui(), Component.translatable("gui.fpsm.shop_config.title"));
        this.filters = parts.filters();
        this.typeSelector = parts.typeSelector();
        this.mapSelector = parts.mapSelector();
        this.shopList = parts.shopList();
        this.emptyLabel = parts.emptyLabel();
        this.statusLabel = parts.statusLabel();
        this.refreshButton = parts.refreshButton();
        this.closeButton = parts.closeButton();
        this.data = Objects.requireNonNull(data, "data");
        typeSelector.setOnValueChanged(value -> selectType(value));
        mapSelector.setOnValueChanged(value -> selectMap(value));
        refreshButton.setOnClick(event -> refresh());
        closeButton.setOnClick(event -> onClose());
        shopList.setItemUIProvider(this::shopRow);
        registerFocusGroup(this::focusTargets);
        applyData(data);
    }

    @Override
    public void init() {
        super.init();
        applyResponsiveLayout();
    }

    @Override
    public void tick() {
        super.tick();
        if (openingEditor && ++openingTicks >= OPEN_TIMEOUT_TICKS) {
            applyEditorOpenFailure(Component.translatable("gui.fpsm.shop_editor.open.timeout"));
        }
    }

    public void applyData(OpenShopConfigToolScreenS2CPacket data) {
        this.data = Objects.requireNonNull(data, "data");
        List<String> types = data.maps().stream().map(OpenShopConfigToolScreenS2CPacket.MapEntry::gameType)
                .distinct().toList();
        typeSelector.setCandidates(types);
        String type = types.contains(data.selectedType()) ? data.selectedType() : (types.isEmpty() ? "" : types.get(0));
        typeSelector.setSelected(type, false);

        List<String> maps = mapsFor(type);
        mapSelector.setCandidates(maps);
        String map = maps.contains(data.selectedMap()) ? data.selectedMap() : (maps.isEmpty() ? "" : maps.get(0));
        mapSelector.setSelected(map, false);
        shopList.setItems(data.shops());
        shopList.refreshVisibleItems();
        emptyLabel.setValue(Component.translatable(data.maps().isEmpty() ? "gui.fpsm.shop_config.no_maps" : "gui.fpsm.shop_config.empty"));
        emptyLabel.setVisible(data.shops().isEmpty());
        if (!openingEditor) {
            statusLabel.setValue(Component.translatable(data.shops().isEmpty() ? "gui.fpsm.shop_config.empty" : "gui.fpsm.shop_config.edit"));
            FPSMLdlib2Theme.status(statusLabel,
                    data.shops().isEmpty() ? FPSMLdlib2Theme.MUTED : FPSMLdlib2Theme.SUCCESS);
        }
        refreshButtons();
    }

    public boolean isEditorOpenPending() {
        return openingEditor;
    }

    public void applyEditorOpenFailure(Component message) {
        if (!openingEditor) {
            return;
        }
        openingEditor = false;
        openingTicks = 0;
        statusLabel.setValue(message);
        FPSMLdlib2Theme.status(statusLabel, FPSMLdlib2Theme.DANGER);
        refreshButtons();
        announce(message, true);
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        FPSMLdlib2Backdrop.draw(graphics, width, height);
    }

    @Override
    public void onClose() {
        if (openingEditor) {
            return;
        }
        ShopEditorNavigation.clear();
        minecraft.setScreen(null);
    }

    private List<Ldlib2AccessibilityController.FocusTarget> focusTargets() {
        List<Ldlib2AccessibilityController.FocusTarget> targets = new ArrayList<>();
        targets.add(typeSelector);
        targets.add(mapSelector);
        shopList.allChildrenStream()
                .filter(AccessibleButton.class::isInstance)
                .map(AccessibleButton.class::cast)
                .filter(AccessibleButton::isActive)
                .forEach(targets::add);
        targets.add(refreshButton);
        targets.add(closeButton);
        return List.copyOf(targets);
    }

    private UIElement shopRow(EditableShopInfo shop) {
        AccessibleButton row = new AccessibleButton();
        row.setId("fpsmatch.shop_config.row." + shop.gameType() + "." + shop.mapName() + "." + shop.teamName());
        row.setText(Component.literal(shop.displayName() + "  /  " + shop.teamName()));
        row.setAccessibleName(Component.literal(shop.displayName()));
        row.setAccessibleState(() -> Component.literal(shop.teamName()));
        row.setAccessibleHint(() -> Component.translatable("gui.fpsm.shop_config.edit"));
        row.setOnClick(event -> openEditor(shop));
        row.layout(layout -> layout.widthPercent(100).height(30).marginBottom(4));
        FPSMLdlib2Theme.button(row, FPSMLdlib2Theme.ButtonKind.SECONDARY);
        row.textStyle(style -> style.fontSize(9));
        row.setActive(!openingEditor);
        return row;
    }

    private void openEditor(EditableShopInfo shop) {
        if (openingEditor || !data.shops().contains(shop)) {
            return;
        }
        openingEditor = true;
        openingTicks = 0;
        statusLabel.setValue(Component.translatable("gui.fpsm.shop_editor.state.opening"));
        FPSMLdlib2Theme.status(statusLabel, FPSMLdlib2Theme.WARNING);
        refreshButtons();
        ShopEditorNavigation.beginConfigTool(shop.gameType(), shop.mapName(), shop.teamName());
        FPSMatch.sendToServer(new OpenShopEditorC2SPacket(
                shop.gameType(), shop.mapName(), shop.teamName()));
    }

    private void selectType(String type) {
        if (type == null || type.isBlank() || openingEditor) {
            return;
        }
        String map = mapsFor(type).stream().findFirst().orElse("");
        sendSelection(type, map);
    }

    private void selectMap(String map) {
        if (map == null || map.isBlank() || openingEditor) {
            return;
        }
        sendSelection(typeSelector.getValue(), map);
    }

    private void sendSelection(String type, String map) {
        if (type == null || type.isBlank() || map == null || map.isBlank()) {
            return;
        }
        FPSMatch.sendToServer(new ShopConfigToolActionC2SPacket(
                ShopConfigToolActionC2SPacket.Action.SELECT, type, map));
    }

    private void refresh() {
        if (openingEditor) {
            return;
        }
        FPSMatch.sendToServer(new ShopConfigToolActionC2SPacket(
                ShopConfigToolActionC2SPacket.Action.REFRESH,
                typeSelector.getValue() == null ? "" : typeSelector.getValue(),
                mapSelector.getValue() == null ? "" : mapSelector.getValue()));
    }

    private List<String> mapsFor(String type) {
        if (type == null || type.isBlank()) {
            return List.of();
        }
        return data.maps().stream().filter(map -> type.equals(map.gameType()))
                .map(OpenShopConfigToolScreenS2CPacket.MapEntry::mapName).toList();
    }

    private void refreshButtons() {
        boolean enabled = !openingEditor;
        FPSMLdlib2Theme.buttonState(refreshButton, FPSMLdlib2Theme.ButtonKind.SECONDARY, enabled);
        FPSMLdlib2Theme.buttonState(closeButton, FPSMLdlib2Theme.ButtonKind.QUIET, enabled);
        typeSelector.setActive(enabled && data.maps().stream()
                .anyMatch(map -> !map.gameType().isBlank()));
        mapSelector.setActive(enabled && data.maps().stream()
                .anyMatch(map -> map.gameType().equals(typeSelector.getValue())));
        shopList.allChildrenStream().filter(AccessibleButton.class::isInstance)
                .map(AccessibleButton.class::cast).forEach(row -> row.setActive(enabled));
    }

    private void applyResponsiveLayout() {
        int margin = Math.min(16, Math.max(8, width / 32));
        int headerHeight = 54;
        int footerHeight = 38;
        absolute(filters, margin, headerHeight, width - margin * 2,
                Math.min(52, Math.max(44, height / 5)));
        int listTop = headerHeight + 60;
        absolute(shopList, margin + 6, listTop + 6, width - margin * 2 - 12,
                Math.max(1, height - listTop - footerHeight - 12));
        absolute(emptyLabel, margin + 14, listTop + 18, width - margin * 2 - 28, 18);
        absolute(statusLabel, margin + 4, height - footerHeight + 10,
                Math.max(1, width - margin * 2 - 210), 16);
        absolute(refreshButton, width - margin - 196, height - footerHeight + 6, 92, 24);
        absolute(closeButton, width - margin - 96, height - footerHeight + 6, 96, 24);
        absolute(typeSelector, 8, 22, Math.max(1, width / 2 - 16), 22);
        absolute(mapSelector, width / 2 + 8, 22, Math.max(1, width / 2 - 16), 22);
        shopList.virtualScrollerViewStyle(style -> style.estimatedItemHeight(34f));
        shopList.refreshVisibleItems();
    }

    private static void absolute(UIElement element, int left, int top, int width, int height) {
        element.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto().left(left).top(top)
                .width(Math.max(1, width)).height(Math.max(1, height)));
    }

    private static Parts build() {
        UIElement root = new UIElement().setId("fpsmatch.shop_config.root");
        root.layout(layout -> layout.widthPercent(100).heightPercent(100));
        FPSMLdlib2Theme.root(root);
        Label system = label("fpsmatch.shop_config.system",
                Component.literal("FPSM // MAP SYSTEM  ·  SHOP INDEX"));
        FPSMLdlib2Theme.systemLabel(system);
        system.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(18).right(18).top(2).height(10));
        Label title = label("fpsmatch.shop_config.title",
                Component.translatable("gui.fpsm.shop_config.title"));
        FPSMLdlib2Theme.title(title);
        title.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(18).right(18).top(13).height(20));
        UIElement filters = new UIElement().setId("fpsmatch.shop_config.filters");
        FPSMLdlib2Theme.panel(filters);
        Label typeLabel = label("fpsmatch.shop_config.type.label",
                Component.translatable("gui.fpsm.shop_config.type"));
        Label mapLabel = label("fpsmatch.shop_config.map.label",
                Component.translatable("gui.fpsm.shop_config.map"));
        FPSMLdlib2Theme.muted(typeLabel);
        FPSMLdlib2Theme.muted(mapLabel);
        typeLabel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(8).top(5).width(140).height(12));
        mapLabel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .leftPercent(50).top(5).width(140).height(12));
        AccessibleSelector<String> typeSelector = selector("fpsmatch.shop_config.type");
        AccessibleSelector<String> mapSelector = selector("fpsmatch.shop_config.map");
        filters.addChildren(typeLabel, mapLabel, typeSelector, mapSelector);
        VirtualScrollerView<EditableShopInfo> list = new VirtualScrollerView<>();
        list.setId("fpsmatch.shop_config.list");
        FPSMLdlib2Theme.virtualScroller(list);
        Label empty = label("fpsmatch.shop_config.empty", Component.empty());
        FPSMLdlib2Theme.muted(empty);
        Label status = label("fpsmatch.shop_config.status", Component.empty());
        FPSMLdlib2Theme.status(status, FPSMLdlib2Theme.MUTED);
        AccessibleButton refresh = button("fpsmatch.shop_config.refresh", "gui.fpsm.shop_config.refresh",
                FPSMLdlib2Theme.ButtonKind.SECONDARY);
        AccessibleButton close = button("fpsmatch.shop_config.close", "gui.back",
                FPSMLdlib2Theme.ButtonKind.QUIET);
        root.addChildren(system, title, filters, list, empty, status, refresh, close);
        return new Parts(ModularUI.of(UI.of(root,
                size -> Size.of(Math.max(280, size.getWidth()), Math.max(200, size.getHeight())))),
                filters, typeSelector, mapSelector, list, empty, status, refresh, close);
    }

    private static AccessibleSelector<String> selector(String id) {
        AccessibleSelector<String> selector = new AccessibleSelector<>();
        selector.setId(id);
        selector.setCandidateUIProvider(value -> label(id + ".option." + value,
                Component.literal(value == null || value.isBlank() ? "-" : value)));
        selector.setAccessibleName(Component.literal(id));
        FPSMLdlib2Theme.selector(selector);
        return selector;
    }

    private static AccessibleButton button(String id, String key, FPSMLdlib2Theme.ButtonKind kind) {
        AccessibleButton button = new AccessibleButton();
        button.setId(id);
        button.setText(Component.translatable(key));
        FPSMLdlib2Theme.button(button, kind);
        return button;
    }

    private static Label label(String id, Component text) {
        Label label = new Label();
        label.setId(id);
        label.setValue(text);
        label.setAllowHitTest(false);
        label.setFocusable(false);
        return label;
    }

    private record Parts(
                         ModularUI ui, UIElement filters, AccessibleSelector<String> typeSelector,
                         AccessibleSelector<String> mapSelector, VirtualScrollerView<EditableShopInfo> shopList,
                         Label emptyLabel, Label statusLabel, AccessibleButton refreshButton,
                         AccessibleButton closeButton) {}
}
