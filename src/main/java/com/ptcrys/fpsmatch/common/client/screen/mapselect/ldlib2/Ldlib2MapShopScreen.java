package com.ptcrys.fpsmatch.common.client.screen.mapselect.ldlib2;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.math.Size;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleButton;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessiblePanel;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.Ldlib2AccessibilityController;
import com.ptcrys.fpsmatch.common.client.screen.shop.ShopEditorNavigation;
import com.ptcrys.fpsmatch.common.packet.mapselect.EditableShopInfo;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.ptcrys.fpsmatch.common.packet.shop.OpenShopEditorC2SPacket;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.ArrayList;
import java.util.List;

/** Accessible, responsive shop picker for opening the server-owned editor menu. */
public final class Ldlib2MapShopScreen extends Ldlib2MapChildScreen {

    private static final int OPEN_TIMEOUT_TICKS = 200;

    private final Label system;
    private final Label header;
    private final Label subtitleLabel;
    private final UIElement panel;
    private final VirtualScrollerView<EditableShopInfo> list;
    private final Label emptyLabel;
    private final Label statusLabel;
    private final AccessibleButton backButton;
    private boolean compact;
    private boolean openingEditor;
    private int openingTicks;

    public Ldlib2MapShopScreen(MapRoomDetail detail, Screen parent) {
        this(build(), detail, parent);
    }

    private Ldlib2MapShopScreen(Parts parts, MapRoomDetail detail, Screen parent) {
        super(parts.ui(), Component.translatable("gui.fpsm.map_shop.title"), detail, parent);
        this.system = parts.system();
        this.header = parts.header();
        this.subtitleLabel = parts.subtitle();
        this.panel = parts.panel();
        this.list = parts.list();
        this.emptyLabel = parts.empty();
        this.statusLabel = parts.status();
        this.backButton = parts.back();
        list.setItemUIProvider(this::shopRow);
        backButton.setOnClick(event -> onClose());
        registerFocusGroup(this::focusTargets);
        refreshContent();
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
            applyEditorOpenFailure(Component.translatable(
                    "gui.fpsm.shop_editor.open.timeout"));
        }
    }

    @Override
    protected void onDetailApplied() {
        refreshContent();
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
        backButton.setActive(true);
        list.refreshVisibleItems();
        announce(message, true);
        accessibility().reconcileFocus();
    }

    private void refreshContent() {
        subtitleLabel.setValue(Component.literal(
                detail.summary().gameType() + " / " + detail.summary().mapName()));
        list.setItems(detail.editableShops());
        list.refreshVisibleItems();
        boolean empty = detail.editableShops().isEmpty();
        emptyLabel.setVisible(empty);
        if (!openingEditor) {
            statusLabel.setValue(Component.translatable(empty ? "gui.fpsm.map_shop.unsupported" : "gui.fpsm.map_shop.selection.ready"));
            FPSMLdlib2Theme.status(statusLabel,
                    empty ? FPSMLdlib2Theme.MUTED : FPSMLdlib2Theme.SUCCESS);
        }
    }

    private UIElement shopRow(EditableShopInfo shop) {
        AccessiblePanel row = new AccessiblePanel();
        row.setId(rowId(shop));
        row.setAccessibleName(Component.literal(shop.displayName()));
        row.setAccessibleState(() -> Component.literal(shop.teamName()));
        row.setAccessibleHint(() -> Component.translatable("gui.fpsm.map_shop.edit.hint"));
        row.setOnActivate(() -> openEditor(shop));
        row.setActive(!openingEditor);
        row.layout(layout -> layout.widthPercent(100).height(compact ? 42 : 30));
        FPSMLdlib2Theme.elevated(row);

        Label name = label(row.getId() + ".name", Component.literal(shop.displayName()));
        FPSMLdlib2Theme.body(name);
        Label team = label(row.getId() + ".team", Component.literal(shop.teamName()));
        FPSMLdlib2Theme.status(team, FPSMLdlib2Theme.SUCCESS);
        Label action = label(row.getId() + ".action",
                Component.translatable("gui.fpsm.map_shop.edit"));
        FPSMLdlib2Theme.status(action, FPSMLdlib2Theme.ACCENT);
        if (compact) {
            inset(name, 8, 88, 5, 14);
            inset(team, 8, 88, 22, 12);
            right(action, 8, 13, 72, 14);
        } else {
            inset(name, 10, 190, 8, 14);
            right(team, 92, 8, 92, 14);
            right(action, 8, 8, 76, 14);
        }
        row.addChildren(name, team, action);
        return row;
    }

    private void openEditor(EditableShopInfo shop) {
        if (openingEditor || !detail.editableShops().contains(shop)) {
            return;
        }
        openingEditor = true;
        openingTicks = 0;
        statusLabel.setValue(Component.translatable("gui.fpsm.shop_editor.state.opening"));
        FPSMLdlib2Theme.status(statusLabel, FPSMLdlib2Theme.WARNING);
        backButton.setActive(false);
        list.refreshVisibleItems();
        MapRoomDetail capturedDetail = detail;
        Screen capturedParent = parent;
        ShopEditorNavigation.beginMapRoom(
                () -> new Ldlib2MapShopScreen(capturedDetail, capturedParent),
                shop.gameType(), shop.mapName(), shop.teamName());
        FPSMatch.sendToServer(new OpenShopEditorC2SPacket(
                shop.gameType(), shop.mapName(), shop.teamName()));
    }

    private List<Ldlib2AccessibilityController.FocusTarget> focusTargets() {
        List<Ldlib2AccessibilityController.FocusTarget> targets = new ArrayList<>();
        list.allChildrenStream()
                .filter(AccessiblePanel.class::isInstance)
                .map(AccessiblePanel.class::cast)
                .filter(UIElement::isActive)
                .forEach(targets::add);
        if (backButton.isActive()) {
            targets.add(backButton);
        }
        return List.copyOf(targets);
    }

    private void applyResponsiveLayout() {
        compact = width < 420 || height < 280;
        int margin = compact ? 8 : 16;
        int headerHeight = compact ? 44 : 52;
        int actionHeight = compact ? 44 : 38;
        absolute(system, margin + 2, 2, width - margin * 2 - 4, 10);
        absolute(header, margin + 2, 13, width - margin * 2 - 4, 18);
        absolute(subtitleLabel, margin + 2, 31, width - margin * 2 - 4, 14);
        absolute(panel, margin, headerHeight, width - margin * 2,
                Math.max(1, height - headerHeight - actionHeight));
        absolute(list, 6, 6, Math.max(1, width - margin * 2 - 12),
                Math.max(1, height - headerHeight - actionHeight - 12));
        absolute(emptyLabel, 12, 16, Math.max(1, width - margin * 2 - 24), 18);
        absolute(statusLabel, margin + 2, height - actionHeight + 10,
                Math.max(1, width - margin * 2 - 112), 16);
        absolute(backButton, Math.max(margin, width - margin - 96),
                height - actionHeight + 6, 96, 24);
        list.virtualScrollerViewStyle(style -> style.estimatedItemHeight(compact ? 44f : 32f));
        list.refreshVisibleItems();
    }

    @Override
    public void onClose() {
        if (openingEditor) {
            return;
        }
        ShopEditorNavigation.clear();
        super.onClose();
    }

    private static String rowId(EditableShopInfo shop) {
        return "fpsmatch.map_shop.row." + shop.gameType() + "." + shop.mapName() + "." + shop.teamName();
    }

    private static Parts build() {
        UIElement root = new UIElement().setId("fpsmatch.map_shop.root");
        root.layout(layout -> layout.widthPercent(100).heightPercent(100));
        FPSMLdlib2Theme.root(root);
        Label system = label("fpsmatch.map_shop.system",
                Component.literal("FPSM // MAP SYSTEM  ·  LOADOUT ARCHIVE"));
        FPSMLdlib2Theme.systemLabel(system);
        Label header = label("fpsmatch.map_shop.header",
                Component.translatable("gui.fpsm.map_shop.title"));
        FPSMLdlib2Theme.title(header);
        Label subtitle = label("fpsmatch.map_shop.subtitle", Component.empty());
        FPSMLdlib2Theme.mapIdentity(subtitle);
        UIElement panel = new UIElement().setId("fpsmatch.map_shop.panel");
        FPSMLdlib2Theme.panel(panel);
        Label empty = label("fpsmatch.map_shop.empty",
                Component.translatable("gui.fpsm.map_shop.unsupported"));
        FPSMLdlib2Theme.muted(empty);
        VirtualScrollerView<EditableShopInfo> list = new VirtualScrollerView<>();
        list.setId("fpsmatch.map_shop.list");
        FPSMLdlib2Theme.virtualScroller(list);
        panel.addChildren(empty, list);
        Label status = label("fpsmatch.map_shop.status",
                Component.translatable("gui.fpsm.map_shop.selection.ready"));
        AccessibleButton back = new AccessibleButton();
        back.setId("fpsmatch.map_shop.back");
        back.setText(Component.translatable("gui.back"));
        FPSMLdlib2Theme.button(back, FPSMLdlib2Theme.ButtonKind.QUIET);
        root.addChildren(system, header, subtitle, panel, status, back);
        return new Parts(ModularUI.of(UI.of(root,
                size -> Size.of(size.getWidth(), size.getHeight()))),
                system, header, subtitle, panel, list, empty, status, back);
    }

    private static Label label(String id, Component text) {
        Label label = new Label();
        label.setId(id);
        label.setValue(text);
        label.setAllowHitTest(false);
        label.setFocusable(false);
        return label;
    }

    private static void absolute(UIElement element, int left, int top, int width, int height) {
        element.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto().left(left).top(top)
                .width(Math.max(1, width)).height(Math.max(1, height)));
    }

    private static void inset(UIElement element, int left, int right, int top, int height) {
        element.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .bottomAuto().left(left).right(right).top(top).height(height));
    }

    private static void right(UIElement element, int right, int top, int width, int height) {
        element.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .leftAuto().bottomAuto().right(right).top(top).width(width).height(height));
    }

    private record Parts(
                         ModularUI ui,
                         Label system,
                         Label header,
                         Label subtitle,
                         UIElement panel,
                         VirtualScrollerView<EditableShopInfo> list,
                         Label empty,
                         Label status,
                         AccessibleButton back) {}
}
