package com.ptcrys.fpsmatch.common.client.screen.mapselect.ldlib2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.math.Size;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleButton;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.Ldlib2AccessibilityController;
import com.ptcrys.fpsmatch.common.client.screen.mapselect.FPSMMapSelectScreens;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomDetail;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.List;
import java.util.UUID;

public final class Ldlib2MapManageScreen extends Ldlib2MapChildScreen {

    private final UIElement panel;
    private final Label debugTitle;
    private final Label subtitleLabel;
    private final Label permissionLabel;
    private final AccessibleButton startButton;
    private final AccessibleButton resetButton;
    private final AccessibleButton newRoundButton;
    private final AccessibleButton cleanupButton;
    private final AccessibleButton switchButton;
    private final AccessibleButton settingsButton;
    private final AccessibleButton shopButton;
    private final AccessibleButton backButton;
    private final List<AccessibleButton> debugButtons;
    private final List<AccessibleButton> toolButtons;
    private Component transientStatus;
    private int transientStatusTicks;

    public Ldlib2MapManageScreen(MapRoomDetail detail, Screen parent) {
        this(build(), detail, parent);
    }

    private Ldlib2MapManageScreen(Parts parts, MapRoomDetail detail, Screen parent) {
        super(parts.ui(), Component.translatable("gui.fpsm.map_select.manage.title"), detail, parent);
        this.panel = parts.panel();
        this.debugTitle = parts.debugTitle();
        this.subtitleLabel = parts.subtitle();
        this.permissionLabel = parts.permission();
        this.startButton = parts.start();
        this.resetButton = parts.reset();
        this.newRoundButton = parts.newRound();
        this.cleanupButton = parts.cleanup();
        this.switchButton = parts.switchBtn();
        this.settingsButton = parts.settings();
        this.shopButton = parts.shop();
        this.backButton = parts.back();
        this.debugButtons = List.of(startButton, resetButton, newRoundButton, cleanupButton, switchButton);
        this.toolButtons = List.of(settingsButton, shopButton);
        startButton.setOnClick(e -> send(MapRoomActionC2SPacket.Action.DEBUG_START));
        resetButton.setOnClick(e -> send(MapRoomActionC2SPacket.Action.DEBUG_RESET));
        newRoundButton.setOnClick(e -> send(MapRoomActionC2SPacket.Action.DEBUG_NEW_ROUND));
        cleanupButton.setOnClick(e -> send(MapRoomActionC2SPacket.Action.DEBUG_CLEANUP));
        switchButton.setOnClick(e -> send(MapRoomActionC2SPacket.Action.DEBUG_SWITCH));
        settingsButton.setOnClick(e -> FPSMMapSelectScreens.openChild(new Ldlib2MapSettingsScreen(this.detail, this)));
        shopButton.setOnClick(e -> FPSMMapSelectScreens.openChild(new Ldlib2MapShopScreen(this.detail, this)));
        parts.back().setOnClick(e -> onClose());
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
        if (transientStatusTicks <= 0) {
            return;
        }
        transientStatusTicks--;
        if (transientStatusTicks == 0) {
            transientStatus = null;
            refreshContent();
            applyResponsiveLayout();
        }
    }

    public void showSettingsSaveSuccess(int changeCount) {
        transientStatus = Component.translatable(
                "gui.fpsm.map_select.settings.save_success",
                Math.max(1, changeCount));
        transientStatusTicks = 100;
        refreshContent();
        if (width > 0 && height > 0) {
            applyResponsiveLayout();
        }
    }

    @Override
    public void applyDetail(MapRoomDetail detail) {
        super.applyDetail(detail);
    }

    @Override
    protected void onDetailApplied() {
        refreshContent();
        if (width > 0 && height > 0) {
            applyResponsiveLayout();
        }
    }

    private void applyResponsiveLayout() {
        int margin = Math.min(16, Math.max(8, width / 32));
        int panelTop = Math.min(56, Math.max(44, height / 5));
        int bottom = Math.min(48, Math.max(38, height / 8));
        int panelWidth = Math.max(1, width - margin * 2);
        int panelHeight = Math.max(1, height - panelTop - bottom);
        panel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto().left(margin).top(panelTop)
                .width(panelWidth).height(panelHeight));

        int padding = Math.min(12, Math.max(6, panelWidth / 24));
        int gap = Math.min(6, Math.max(3, panelWidth / 80));
        int columns = panelWidth >= 520 ? 5 : panelWidth >= 300 ? 3 : 2;
        int buttonWidth = Math.max(1,
                (panelWidth - padding * 2 - gap * (columns - 1)) / columns);
        int debugRows = (debugButtons.size() + columns - 1) / columns;
        int toolRows = (toolButtons.size() + columns - 1) / columns;
        int totalRows = debugRows + toolRows;
        int groupGap = 6;
        int permissionReserve = detail.summary().currentPlayerOp() && transientStatus == null ? 0 : 22;
        int interRowGaps = Math.max(0, debugRows - 1) + Math.max(0, toolRows - 1);
        int availableGridHeight = Math.max(1,
                panelHeight - 26 - padding - permissionReserve - groupGap - interRowGaps * gap);
        int buttonHeight = Math.min(22, Math.max(10, availableGridHeight / totalRows));
        debugTitle.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto().left(padding).top(6)
                .width(Math.max(1, panelWidth - padding * 2)).height(14));

        int gridTop = 24;
        layoutButtonGrid(debugButtons, padding, gridTop, columns, buttonWidth, buttonHeight, gap);
        int toolsTop = gridTop + debugRows * buttonHeight + Math.max(0, debugRows - 1) * gap + groupGap;
        layoutButtonGrid(toolButtons, padding, toolsTop, columns, buttonWidth, buttonHeight, gap);

        int permissionTop = Math.max(0, panelHeight - 20);
        permissionLabel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto().left(padding).top(permissionTop)
                .width(Math.max(1, panelWidth - padding * 2)).height(18));
        permissionLabel.textStyle(style -> style.fontSize(8));

        int backWidth = Math.min(96, Math.max(64, width / 5));
        backButton.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().topAuto().left(margin).bottom(12)
                .width(backWidth).height(22));
    }

    private static void layoutButtonGrid(List<AccessibleButton> buttons, int padding, int top,
                                         int columns, int buttonWidth, int buttonHeight, int gap) {
        for (int i = 0; i < buttons.size(); i++) {
            int index = i;
            int column = index % columns;
            int row = index / columns;
            buttons.get(i).layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .rightAuto().bottomAuto()
                    .left(padding + column * (buttonWidth + gap))
                    .top(top + row * (buttonHeight + gap))
                    .width(buttonWidth).height(buttonHeight));
        }
    }

    private void refreshContent() {
        subtitleLabel.setValue(Component.literal(detail.summary().gameType() + " / " + detail.summary().mapName()));
        boolean op = detail.summary().currentPlayerOp();
        boolean showTransientStatus = op && transientStatus != null;
        permissionLabel.setVisible(!op || showTransientStatus);
        if (!op) {
            permissionLabel.setValue(Component.translatable("gui.fpsm.map_select.manage.no_permission"));
        } else if (showTransientStatus) {
            permissionLabel.setValue(transientStatus);
        }
        permissionLabel.textStyle(style -> style
                .fontSize(8)
                .textColor(showTransientStatus ? FPSMLdlib2Theme.SUCCESS : FPSMLdlib2Theme.WARNING));
        FPSMLdlib2Theme.buttonState(startButton, FPSMLdlib2Theme.ButtonKind.PRIMARY, op);
        FPSMLdlib2Theme.buttonState(resetButton, FPSMLdlib2Theme.ButtonKind.SECONDARY, op);
        FPSMLdlib2Theme.buttonState(newRoundButton, FPSMLdlib2Theme.ButtonKind.SECONDARY, op);
        FPSMLdlib2Theme.buttonState(cleanupButton, FPSMLdlib2Theme.ButtonKind.SECONDARY, op);
        FPSMLdlib2Theme.buttonState(switchButton, FPSMLdlib2Theme.ButtonKind.SECONDARY, op);
        FPSMLdlib2Theme.buttonState(settingsButton, FPSMLdlib2Theme.ButtonKind.SECONDARY, op);
        FPSMLdlib2Theme.buttonState(
                shopButton, FPSMLdlib2Theme.ButtonKind.SECONDARY,
                op && !detail.editableShops().isEmpty());
        FPSMLdlib2Theme.buttonState(backButton, FPSMLdlib2Theme.ButtonKind.QUIET, true);
        if (!op) {
            fallbackFocusAfterPermissionLoss();
        }
    }

    private List<Ldlib2AccessibilityController.FocusTarget> focusTargets() {
        List<Ldlib2AccessibilityController.FocusTarget> targets = new java.util.ArrayList<>();
        for (Ldlib2AccessibilityController.FocusTarget target : List.of(
                startButton, resetButton, newRoundButton, cleanupButton, switchButton,
                settingsButton, shopButton, backButton)) {
            if (target.element().isVisible() && target.element().isActive()) {
                targets.add(target);
            }
        }
        return List.copyOf(targets);
    }

    private void fallbackFocusAfterPermissionLoss() {
        if (Minecraft.getInstance().screen != this) {
            return;
        }
        if (backButton.isActive()) {
            modularUI.requestFocus(backButton);
        } else {
            focusTargets().stream()
                    .map(Ldlib2AccessibilityController.FocusTarget::element)
                    .findFirst()
                    .ifPresent(modularUI::requestFocus);
        }
        accessibility().reconcileFocus();
    }

    private void send(MapRoomActionC2SPacket.Action action) {
        FPSMatch.sendToServer(new MapRoomActionC2SPacket(action, detail.summary().gameType(), detail.summary().mapName(), new UUID(0L, 0L)));
    }

    private static Parts build() {
        UIElement root = new UIElement().setId("fpsmatch.map_manage.root");
        root.layout(layout -> layout.widthPercent(100).heightPercent(100));
        FPSMLdlib2Theme.root(root);
        Label system = label("fpsmatch.map_manage.system", Component.literal("FPSM // MAP SYSTEM  ·  CONTROL DECK"));
        system.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).right(18).top(2).height(10));
        FPSMLdlib2Theme.systemLabel(system);
        Label header = label("fpsmatch.map_manage.header", Component.translatable("gui.fpsm.map_select.manage.title"));
        header.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).right(18).top(12).height(20));
        FPSMLdlib2Theme.title(header);
        Label subtitle = label("fpsmatch.map_manage.subtitle", Component.empty());
        subtitle.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).right(18).top(34).height(16));
        FPSMLdlib2Theme.mapIdentity(subtitle);
        UIElement panel = new UIElement().setId("fpsmatch.map_manage.panel");
        panel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(16).right(16).top(56).bottom(56));
        FPSMLdlib2Theme.panel(panel);
        Label debugTitle = label("fpsmatch.map_manage.debug_title", Component.translatable("gui.fpsm.map_select.manage.title"));
        debugTitle.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(14).top(12).height(16));
        FPSMLdlib2Theme.sectionTitle(debugTitle);
        AccessibleButton start = medium("fpsmatch.map_manage.start", "gui.fpsm.map_select.debug.start", 14, 36);
        AccessibleButton reset = medium("fpsmatch.map_manage.reset", "gui.fpsm.map_select.debug.reset", 118, 36);
        AccessibleButton newRound = medium("fpsmatch.map_manage.new_round", "gui.fpsm.map_select.debug.new_round", 222, 36);
        AccessibleButton cleanup = medium("fpsmatch.map_manage.cleanup", "gui.fpsm.map_select.debug.cleanup", 326, 36);
        AccessibleButton switchBtn = medium("fpsmatch.map_manage.switch", "gui.fpsm.map_select.debug.switch", 430, 36);
        AccessibleButton settings = medium("fpsmatch.map_manage.settings", "gui.fpsm.map_select.settings", 14, 72);
        AccessibleButton shop = medium("fpsmatch.map_manage.shop", "gui.fpsm.map_detail.edit_shop", 118, 72);
        FPSMLdlib2Theme.button(start, FPSMLdlib2Theme.ButtonKind.PRIMARY);
        for (AccessibleButton b : new AccessibleButton[] { reset, newRound, cleanup, switchBtn, settings, shop }) {
            FPSMLdlib2Theme.button(b, FPSMLdlib2Theme.ButtonKind.SECONDARY);
        }
        for (AccessibleButton b : new AccessibleButton[] { start, reset, newRound, cleanup, switchBtn, settings, shop }) {
            b.textStyle(style -> style.fontSize(8));
        }
        Label permission = label("fpsmatch.map_manage.permission", Component.empty());
        permission.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(14).right(14).top(112).height(20));
        FPSMLdlib2Theme.status(permission, FPSMLdlib2Theme.WARNING);
        panel.addChildren(debugTitle, start, reset, newRound, cleanup, switchBtn, settings, shop, permission);
        AccessibleButton back = medium("fpsmatch.map_manage.back", "gui.back", 18, 0);
        back.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).bottom(16).width(96).height(24));
        FPSMLdlib2Theme.button(back, FPSMLdlib2Theme.ButtonKind.QUIET);
        back.textStyle(style -> style.fontSize(8));
        root.addChildren(system, header, subtitle, panel, back);
        return new Parts(ModularUI.of(UI.of(root, size -> Size.of(size.getWidth(), size.getHeight()))),
                panel, debugTitle, subtitle, permission, start, reset, newRound, cleanup,
                switchBtn, settings, shop, back);
    }

    private static Label label(String id, Component text) {
        Label label = new Label();
        label.setId(id);
        label.setValue(text);
        return label;
    }

    private static AccessibleButton medium(String id, String key, int left, int top) {
        AccessibleButton button = new AccessibleButton();
        button.setId(id);
        button.setText(Component.translatable(key));
        button.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(left).top(top).width(96).height(24));
        return button;
    }

    private record Parts(ModularUI ui, UIElement panel, Label debugTitle, Label subtitle, Label permission,
                         AccessibleButton start, AccessibleButton reset, AccessibleButton newRound,
                         AccessibleButton cleanup, AccessibleButton switchBtn, AccessibleButton settings,
                         AccessibleButton shop, AccessibleButton back) {}
}
