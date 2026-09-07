package com.ptcrys.fpsmatch.common.client.screen.mapselect.ldlib2;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.math.Size;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleButton;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessiblePanel;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Backdrop;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.Ldlib2AccessibilityController;
import com.ptcrys.fpsmatch.common.client.screen.shop.ShopEditorNavigation;
import com.ptcrys.fpsmatch.common.packet.mapselect.EditableShopInfo;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.ptcrys.fpsmatch.common.packet.shop.OpenShopEditorC2SPacket;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.ArrayList;
import java.util.List;

/** Accessible, responsive shop picker for opening the server-owned editor menu. */
public final class Ldlib2MapShopScreen extends Ldlib2MapChildScreen {
    private static final int OPEN_TIMEOUT_TICKS = 200;

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
    public void renderBackground(GuiGraphics graphics) {
        FPSMLdlib2Backdrop.drawMapIndex(graphics, width, height);
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
        FPSMMapSelectTheme.status(statusLabel, FPSMMapSelectTheme.DANGER);
        FPSMMapSelectTheme.buttonState(backButton,
                FPSMMapSelectTheme.ButtonKind.QUIET, true);
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
            statusLabel.setValue(Component.translatable(empty
                    ? "gui.fpsm.map_shop.unsupported"
                    : "gui.fpsm.map_shop.selection.ready"));
            FPSMMapSelectTheme.status(statusLabel,
                    empty ? FPSMMapSelectTheme.MUTED : FPSMMapSelectTheme.SUCCESS);
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
        row.layout(layout -> layout.widthPercent(100).height(compact ? 50 : 40));
        FPSMMapSelectTheme.elevated(row);

        Label name = label(row.getId() + ".name", Component.literal(shop.displayName()));
        FPSMMapSelectTheme.body(name);
        name.textStyle(style -> style.fontSize(11));
        Label team = label(row.getId() + ".team", Component.literal(shop.teamName()));
        FPSMMapSelectTheme.status(team, FPSMMapSelectTheme.SUCCESS);
        team.textStyle(style -> style.fontSize(9));
        Label action = label(row.getId() + ".action",
                Component.translatable("gui.fpsm.map_shop.edit"));
        FPSMMapSelectTheme.status(action, FPSMMapSelectTheme.ACCENT);
        action.textStyle(style -> style.fontSize(10));
        if (compact) {
            inset(name, 9, 76, 7, 14);
            inset(team, 9, 76, 27, 12);
            right(action, 9, 18, 60, 14);
        } else {
            inset(name, 12, 214, 12, 14);
            right(team, 106, 13, 96, 14);
            right(action, 12, 12, 78, 14);
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
        FPSMMapSelectTheme.status(statusLabel, FPSMMapSelectTheme.WARNING);
        FPSMMapSelectTheme.buttonState(backButton,
                FPSMMapSelectTheme.ButtonKind.QUIET, false);
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
        compact = width < 460 || height < 300;
        int margin = compact ? 8 : 16;
        int headerHeight = compact ? 48 : 58;
        int actionHeight = compact ? 48 : 44;
        absolute(header, margin + 2, 8, width - margin * 2 - 4, 22);
        absolute(subtitleLabel, margin + 2, 32, width - margin * 2 - 4, 15);
        absolute(panel, margin, headerHeight, width - margin * 2,
                Math.max(1, height - headerHeight - actionHeight));
        absolute(list, 8, 8, Math.max(1, width - margin * 2 - 16),
                Math.max(1, height - headerHeight - actionHeight - 16));
        absolute(emptyLabel, 14, 18, Math.max(1, width - margin * 2 - 28), 20);
        int backWidth = compact ? 82 : 104;
        absolute(statusLabel, margin + 2, height - actionHeight + 13,
                Math.max(1, width - margin * 2 - backWidth - 12), 18);
        absolute(backButton, Math.max(margin, width - margin - backWidth),
                height - actionHeight + 8, backWidth, 28);
        header.textStyle(style -> style.fontSize(16));
        subtitleLabel.textStyle(style -> style.fontSize(9));
        statusLabel.textStyle(style -> style.fontSize(9));
        backButton.textStyle(style -> style.fontSize(10));
        list.virtualScrollerViewStyle(style -> style.estimatedItemHeight(compact ? 53f : 43f));
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
        return "fpsmatch.map_shop.row." + shop.gameType() + "." + shop.mapName()
                + "." + shop.teamName();
    }

    private static Parts build() {
        UIElement root = new UIElement().setId("fpsmatch.map_shop.root");
        root.layout(layout -> layout.widthPercent(100).heightPercent(100));
        FPSMMapSelectTheme.root(root);
        Label header = label("fpsmatch.map_shop.header",
                Component.translatable("gui.fpsm.map_shop.title"));
        FPSMMapSelectTheme.title(header);
        Label subtitle = label("fpsmatch.map_shop.subtitle", Component.empty());
        FPSMMapSelectTheme.mapIdentity(subtitle);
        UIElement panel = new UIElement().setId("fpsmatch.map_shop.panel");
        FPSMMapSelectTheme.panel(panel);
        Label empty = label("fpsmatch.map_shop.empty",
                Component.translatable("gui.fpsm.map_shop.unsupported"));
        FPSMMapSelectTheme.muted(empty);
        empty.textStyle(style -> style.fontSize(10));
        VirtualScrollerView<EditableShopInfo> list = new VirtualScrollerView<>();
        list.setId("fpsmatch.map_shop.list");
        FPSMMapSelectTheme.virtualScroller(list);
        panel.addChildren(empty, list);
        Label status = label("fpsmatch.map_shop.status",
                Component.translatable("gui.fpsm.map_shop.selection.ready"));
        AccessibleButton back = new AccessibleButton();
        back.setId("fpsmatch.map_shop.back");
        back.setText(Component.translatable("gui.back"));
        FPSMMapSelectTheme.button(back, FPSMMapSelectTheme.ButtonKind.QUIET);
        root.addChildren(header, subtitle, panel, status, back);
        return new Parts(ModularUI.of(UI.of(root,
                size -> Size.of(size.getWidth(), size.getHeight()))),
                header, subtitle, panel, list, empty, status, back);
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
            Label header,
            Label subtitle,
            UIElement panel,
            VirtualScrollerView<EditableShopInfo> list,
            Label empty,
            Label status,
            AccessibleButton back
    ) {
    }
}
