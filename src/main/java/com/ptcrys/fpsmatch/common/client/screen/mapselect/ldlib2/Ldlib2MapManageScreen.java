package com.ptcrys.fpsmatch.common.client.screen.mapselect.ldlib2;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.math.Size;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleButton;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Backdrop;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.Ldlib2AccessibilityController;
import com.ptcrys.fpsmatch.common.client.screen.mapselect.FPSMMapSelectScreens;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomDetail;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.List;
import java.util.UUID;

public final class Ldlib2MapManageScreen extends Ldlib2MapChildScreen {
    private final UIElement commandPanel;
    private final UIElement configurationPanel;
    private final Label commandTitle;
    private final Label configurationTitle;
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
    private final List<AccessibleButton> roundButtons;
    private final List<AccessibleButton> toolButtons;
    private Component transientStatus;
    private int transientStatusTicks;

    public Ldlib2MapManageScreen(MapRoomDetail detail, Screen parent) {
        this(build(), detail, parent);
    }

    private Ldlib2MapManageScreen(Parts parts, MapRoomDetail detail, Screen parent) {
        super(parts.ui(), Component.translatable("gui.fpsm.map_select.manage.title"), detail, parent);
        this.commandPanel = parts.commandPanel();
        this.configurationPanel = parts.configurationPanel();
        this.commandTitle = parts.commandTitle();
        this.configurationTitle = parts.configurationTitle();
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
        this.roundButtons = List.of(resetButton, newRoundButton, cleanupButton, switchButton);
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
    public void renderBackground(GuiGraphics graphics) {
        FPSMLdlib2Backdrop.drawMapIndex(graphics, width, height);
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
                Math.max(1, changeCount)
        );
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
        boolean narrow = width < 440;
        int margin = Math.min(18, Math.max(8, width / 28));
        int headerTop = 8;
        int panelTop = Math.min(58, Math.max(48, height / 6));
        int footerHeight = 42;
        int availableWidth = Math.max(1, width - margin * 2);
        int availableHeight = Math.max(1, height - panelTop - footerHeight);
        int gap = narrow ? 6 : 10;

        int commandWidth = narrow ? availableWidth : Math.max(1, availableWidth * 63 / 100);
        int configurationWidth = narrow ? availableWidth : Math.max(1, availableWidth - commandWidth - gap);
        int commandHeight = narrow
                ? Math.max(82, (availableHeight - gap) * 64 / 100)
                : availableHeight;
        commandHeight = Math.min(commandHeight, availableHeight);
        int configurationHeight = narrow
                ? Math.max(1, availableHeight - commandHeight - gap)
                : availableHeight;

        absolute(commandPanel, margin, panelTop, commandWidth, commandHeight);
        absolute(configurationPanel,
                narrow ? margin : margin + commandWidth + gap,
                narrow ? panelTop + commandHeight + gap : panelTop,
                configurationWidth, configurationHeight);

        int padding = narrow ? 7 : 10;
        commandTitle.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto().left(padding).top(7)
                .width(Math.max(1, commandWidth - padding * 2)).height(16));
        configurationTitle.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto().left(padding).top(7)
                .width(Math.max(1, configurationWidth - padding * 2)).height(16));

        int buttonGap = 5;
        int gridTop = 29;
        int commandInnerWidth = Math.max(1, commandWidth - padding * 2);
        int startHeight = Math.min(30, Math.max(22, commandHeight / 5));
        absolute(startButton, padding, gridTop, commandInnerWidth, startHeight);
        int roundTop = gridTop + startHeight + buttonGap;
        int roundColumns = narrow || commandWidth < 360 ? 2 : 4;
        int roundWidth = Math.max(1,
                (commandInnerWidth - buttonGap * (roundColumns - 1)) / roundColumns);
        int roundRows = (roundButtons.size() + roundColumns - 1) / roundColumns;
        int statusReserve = detail.summary().currentPlayerOp() && transientStatus == null ? 5 : 24;
        int roundHeight = Math.min(26, Math.max(18,
                (commandHeight - roundTop - padding - statusReserve
                        - buttonGap * Math.max(0, roundRows - 1)) / Math.max(1, roundRows)));
        layoutButtonGrid(roundButtons, padding, roundTop, roundColumns,
                roundWidth, roundHeight, buttonGap);

        int toolTop = 29;
        int toolInnerWidth = Math.max(1, configurationWidth - padding * 2);
        int toolColumns = narrow && configurationWidth >= 220 ? 2 : 1;
        int toolWidth = Math.max(1,
                (toolInnerWidth - buttonGap * (toolColumns - 1)) / toolColumns);
        int toolHeight = Math.min(30, Math.max(22,
                (configurationHeight - toolTop - padding
                        - buttonGap * ((toolButtons.size() + toolColumns - 1) / toolColumns - 1))
                        / Math.max(1, (toolButtons.size() + toolColumns - 1) / toolColumns)));
        layoutButtonGrid(toolButtons, padding, toolTop, toolColumns,
                toolWidth, toolHeight, buttonGap);

        int permissionTop = Math.max(0, commandHeight - 21);
        permissionLabel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto().left(padding).top(permissionTop)
                .width(Math.max(1, commandWidth - padding * 2)).height(16));
        permissionLabel.textStyle(style -> style.fontSize(9));

        int backWidth = Math.min(112, Math.max(76, width / 5));
        backButton.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().topAuto().left(margin).bottom(10)
                .width(backWidth).height(26));
    }

    private static void absolute(UIElement element, int left, int top, int width, int height) {
        element.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto().left(left).top(top)
                .width(Math.max(1, width)).height(Math.max(1, height)));
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
                .fontSize(9)
                .textColor(showTransientStatus ? FPSMMapSelectTheme.SUCCESS : FPSMMapSelectTheme.WARNING));
        FPSMMapSelectTheme.buttonState(startButton, FPSMMapSelectTheme.ButtonKind.PRIMARY, op);
        FPSMMapSelectTheme.buttonState(resetButton, FPSMMapSelectTheme.ButtonKind.SECONDARY, op);
        FPSMMapSelectTheme.buttonState(newRoundButton, FPSMMapSelectTheme.ButtonKind.SECONDARY, op);
        FPSMMapSelectTheme.buttonState(cleanupButton, FPSMMapSelectTheme.ButtonKind.SECONDARY, op);
        FPSMMapSelectTheme.buttonState(switchButton, FPSMMapSelectTheme.ButtonKind.SECONDARY, op);
        FPSMMapSelectTheme.buttonState(settingsButton, FPSMMapSelectTheme.ButtonKind.SECONDARY, op);
        FPSMMapSelectTheme.buttonState(
                shopButton, FPSMMapSelectTheme.ButtonKind.SECONDARY,
                op && !detail.editableShops().isEmpty()
        );
        FPSMMapSelectTheme.buttonState(backButton, FPSMMapSelectTheme.ButtonKind.QUIET, true);
        if (!op) {
            fallbackFocusAfterPermissionLoss();
        }
    }

    private List<Ldlib2AccessibilityController.FocusTarget> focusTargets() {
        List<Ldlib2AccessibilityController.FocusTarget> targets = new java.util.ArrayList<>();
        for (Ldlib2AccessibilityController.FocusTarget target : List.of(
                startButton, resetButton, newRoundButton, cleanupButton, switchButton,
                settingsButton, shopButton, backButton
        )) {
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
        FPSMMapSelectTheme.root(root);
        Label header = label("fpsmatch.map_manage.header", Component.translatable("gui.fpsm.map_select.manage.title"));
        header.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).right(18).top(8).height(22));
        FPSMMapSelectTheme.title(header);
        header.textStyle(style -> style.fontSize(16));
        Label subtitle = label("fpsmatch.map_manage.subtitle", Component.empty());
        subtitle.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).right(18).top(32).height(14));
        FPSMMapSelectTheme.mapIdentity(subtitle);
        subtitle.textStyle(style -> style.fontSize(9));

        UIElement commandPanel = new UIElement().setId("fpsmatch.map_manage.commands");
        FPSMMapSelectTheme.panel(commandPanel);
        Label commandTitle = label("fpsmatch.map_manage.commands.title",
                Component.translatable("gui.fpsm.map_select.manage.match_actions"));
        FPSMMapSelectTheme.sectionTitle(commandTitle);

        UIElement configurationPanel = new UIElement().setId("fpsmatch.map_manage.configuration");
        FPSMMapSelectTheme.panel(configurationPanel);
        Label configurationTitle = label("fpsmatch.map_manage.configuration.title",
                Component.translatable("gui.fpsm.map_select.manage.configuration"));
        FPSMMapSelectTheme.sectionTitle(configurationTitle);

        AccessibleButton start = medium("fpsmatch.map_manage.start", "gui.fpsm.map_select.debug.start", 14, 36);
        AccessibleButton reset = medium("fpsmatch.map_manage.reset", "gui.fpsm.map_select.debug.reset", 118, 36);
        AccessibleButton newRound = medium("fpsmatch.map_manage.new_round", "gui.fpsm.map_select.debug.new_round", 222, 36);
        AccessibleButton cleanup = medium("fpsmatch.map_manage.cleanup", "gui.fpsm.map_select.debug.cleanup", 326, 36);
        AccessibleButton switchBtn = medium("fpsmatch.map_manage.switch", "gui.fpsm.map_select.debug.switch", 430, 36);
        AccessibleButton settings = medium("fpsmatch.map_manage.settings", "gui.fpsm.map_select.settings", 14, 72);
        AccessibleButton shop = medium("fpsmatch.map_manage.shop", "gui.fpsm.map_detail.edit_shop", 118, 72);
        FPSMMapSelectTheme.button(start, FPSMMapSelectTheme.ButtonKind.PRIMARY);
        for (AccessibleButton b : new AccessibleButton[]{reset, newRound, cleanup, switchBtn, settings, shop}) {
            FPSMMapSelectTheme.button(b, FPSMMapSelectTheme.ButtonKind.SECONDARY);
        }
        for (AccessibleButton b : new AccessibleButton[]{start, reset, newRound, cleanup, switchBtn, settings, shop}) {
            b.textStyle(style -> style.fontSize(10));
        }
        Label permission = label("fpsmatch.map_manage.permission", Component.empty());
        permission.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(14).right(14).top(112).height(20));
        FPSMMapSelectTheme.status(permission, FPSMMapSelectTheme.WARNING);
        commandPanel.addChildren(commandTitle, start, reset, newRound, cleanup, switchBtn, permission);
        configurationPanel.addChildren(configurationTitle, settings, shop);
        AccessibleButton back = medium("fpsmatch.map_manage.back", "gui.back", 18, 0);
        back.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).bottom(16).width(96).height(24));
        FPSMMapSelectTheme.button(back, FPSMMapSelectTheme.ButtonKind.QUIET);
        back.textStyle(style -> style.fontSize(10));
        root.addChildren(header, subtitle, commandPanel, configurationPanel, back);
        return new Parts(ModularUI.of(UI.of(root, size -> Size.of(size.getWidth(), size.getHeight()))),
                commandPanel, configurationPanel, commandTitle, configurationTitle,
                subtitle, permission, start, reset, newRound, cleanup,
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

    private record Parts(ModularUI ui, UIElement commandPanel, UIElement configurationPanel,
                         Label commandTitle, Label configurationTitle, Label subtitle, Label permission,
                         AccessibleButton start, AccessibleButton reset, AccessibleButton newRound,
                         AccessibleButton cleanup, AccessibleButton switchBtn, AccessibleButton settings,
                         AccessibleButton shop, AccessibleButton back) {}
}
