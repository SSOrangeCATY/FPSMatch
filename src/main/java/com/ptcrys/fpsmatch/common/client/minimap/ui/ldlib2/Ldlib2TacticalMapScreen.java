package com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.ptcrys.fpsmatch.common.client.minimap.render.ForgeMinimapClientSettings;
import com.ptcrys.fpsmatch.common.client.minimap.render.MinimapClientSettings;
import com.ptcrys.fpsmatch.common.client.minimap.render.MinimapViewerPose;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.TacticalMapController;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.TacticalMapPresentation;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.TacticalMapState;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleButton;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleModularUIScreen;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.Ldlib2RenderGuard;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleSelector;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleToggle;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.Ldlib2AccessibilityController;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme;
import com.ptcrys.fpsmatch.config.FPSMConfig;
import com.ptcrys.fpsmatch.core.minimap.model.DisplayLabel;
import com.ptcrys.fpsmatch.core.minimap.view.FloorViewMode;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaPositionType;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;

import static com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2.TacticalMapUiView.AUTOMATIC_FLOOR;
import static com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2.TacticalMapUiView.component;
import static com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2.TacticalMapUiView.label;

public final class Ldlib2TacticalMapScreen extends AccessibleModularUIScreen {
    private static final int KEYBOARD_PAN_PIXELS = 24;

    private enum Availability {
        UNKNOWN,
        AVAILABLE,
        UNAVAILABLE
    }

    private final TacticalMapController controller;
    private final Runnable closeAction;
    private final Ldlib2MinimapHudPresentation presentation;
    private final Ldlib2MinimapCanvasElement canvas;
    private final UIElement sidebar;
    private final AccessibleButton controlsToggle;
    private final AccessibleToggle autoManual;
    private final AccessibleSelector<String> floorSelector;
    private final UIElement filterList;
    private final UIElement legend;
    private final UIElement regionDetail;
    private final Label stateLabel;
    private final AccessibleButton fitFloor;
    private final AccessibleButton fitAll;
    private final AccessibleButton close;
    private final Map<String, Component> floorLabels;

    private List<TacticalMapPresentation.FloorOption> displayedFloors = List.of();
    private List<TacticalMapPresentation.LegendEntry> displayedLegend = List.of();
    private Set<String> displayedHiddenTypes = Set.of();
    /** Keep the legend order stable so Tab traversal does not jump after a refresh. */
    private final Map<String, AccessibleToggle> markerControls = new LinkedHashMap<>();
    private TacticalMapPresentation currentPresentation;
    private TacticalMapLayoutModel currentLayout;
    private boolean controlsDrawerOpen;
    private Availability availability = Availability.UNKNOWN;
    private int appliedLayoutWidth = -1;
    private int appliedLayoutHeight = -1;
    private boolean closed;

    public Ldlib2TacticalMapScreen(
            TacticalMapController controller,
            Runnable closeAction,
            Ldlib2MinimapHudPresentation presentation
    ) {
        this(TacticalMapUiView.build(controller, presentation), controller, closeAction, presentation);
    }

    private Ldlib2TacticalMapScreen(
            TacticalMapUiView.Parts parts,
            TacticalMapController controller,
            Runnable closeAction,
            Ldlib2MinimapHudPresentation presentation
    ) {
        super(parts.ui(), Component.translatable(
                "gui.fpsm.minimap.tactical.title"
        ));
        this.controller = Objects.requireNonNull(controller, "controller");
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction");
        this.presentation = Objects.requireNonNull(presentation, "presentation");
        this.canvas = parts.canvas();
        this.sidebar = parts.sidebar();
        this.controlsToggle = parts.controlsToggle();
        this.autoManual = parts.autoManual();
        this.floorSelector = parts.floorSelector();
        this.filterList = parts.filterList();
        this.legend = parts.legend();
        this.regionDetail = parts.regionDetail();
        this.stateLabel = parts.stateLabel();
        this.fitFloor = parts.fitFloor();
        this.fitAll = parts.fitAll();
        this.close = parts.close();
        this.floorLabels = parts.floorLabels();
        parts.closeTrigger().bind(this::onClose);
        parts.regionTrigger().bind(this::selectRegion);
        parts.drawerTrigger().bind(this::toggleControlsDrawer);
        controlsToggle.setAccessibleState(() -> Component.translatable(
                controlsDrawerOpen
                        ? "gui.fpsm.minimap.tactical.controls.expanded"
                        : "gui.fpsm.minimap.tactical.controls.collapsed"
        ));
        registerFocusGroup(this::focusTargets);
    }

    @Override
    public void init() {
        super.init();
        // Element IDs are registered only after ModularUI.setScreenAndInit (super.init).
        bindRequiredWidgets();
        applyResponsiveLayout();
        refreshFrame();
    }

    @Override
    public void tick() {
        super.tick();
        controller.tick(1);
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        ensureResponsiveLayout();
        resizeController();
        refreshFrame();
        try {
            super.render(graphics, mouseX, mouseY, partialTick);
        } catch (ConcurrentModificationException failure) {
            if (!Ldlib2RenderGuard.ignoreConcurrentModification(this, failure)) {
                throw failure;
            }
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        modularUI.getWidget().mouseMoved(mouseX, mouseY);
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        ensureResponsiveLayout();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_M) {
            return handleCanvasKey(keyCode);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (floorSelector.isOpen()) {
                closeFloorSelector();
                return true;
            }
            if (controlsDrawerOpen) {
                setControlsDrawerOpen(false);
                return true;
            }
            return handleCanvasKey(keyCode);
        }
        if (hasFocusedControl()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (isCanvasKey(keyCode)) {
            return handleCanvasKey(keyCode);
        }
        if (handleCanvasKey(keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static boolean isCanvasKey(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT,
                    GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN,
                    GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_A,
                    GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D,
                    GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD,
                    GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT,
                    GLFW.GLFW_KEY_HOME -> true;
            default -> false;
        };
    }

    private boolean hasFocusedControl() {
        UIElement focused = modularUI.getFocusedElement();
        return focused != null && focused != canvas && focused.isFocusable();
    }

    private boolean handleCanvasKey(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> {
                controller.panByPixels(-KEYBOARD_PAN_PIXELS, 0);
                yield true;
            }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> {
                controller.panByPixels(KEYBOARD_PAN_PIXELS, 0);
                yield true;
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> {
                controller.panByPixels(0, -KEYBOARD_PAN_PIXELS);
                yield true;
            }
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> {
                controller.panByPixels(0, KEYBOARD_PAN_PIXELS);
                yield true;
            }
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> {
                controller.zoomByWheel(1, 0, 0);
                yield true;
            }
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> {
                controller.zoomByWheel(-1, 0, 0);
                yield true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                controller.fitFloor();
                yield true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER,
                    GLFW.GLFW_KEY_SPACE -> {
                selectRegion(
                        canvas.getPositionX() + canvas.getSizeWidth() * 0.5,
                        canvas.getPositionY() + canvas.getSizeHeight() * 0.5
                );
                yield true;
            }
            case GLFW.GLFW_KEY_M -> {
                onClose();
                yield true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (floorSelector.isOpen()) {
                    closeFloorSelector();
                } else if (controlsDrawerOpen) {
                    setControlsDrawerOpen(false);
                } else {
                    onClose();
                }
                yield true;
            }
            default -> false;
        };
    }

    private void closeFloorSelector() {
        floorSelector.hide();
        // Keep keyboard traversal anchored on the control whose popup closed.
        floorSelector.focus();
        accessibility().reconcileFocus();
    }

    @Override
    public void onClose() {
        RuntimeException failure = null;
        try {
            releaseTacticalLease();
        } catch (RuntimeException closeFailure) {
            failure = closeFailure;
        } finally {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.tell(() -> {
                if (minecraft.screen == this) {
                    minecraft.setScreen(null);
                }
            });
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void removed() {
        RuntimeException teardownFailure = null;
        try {
            releaseTacticalLease();
        } catch (RuntimeException failure) {
            teardownFailure = mergeFailure(teardownFailure, failure);
        }
        try {
            super.removed();
        } catch (RuntimeException failure) {
            teardownFailure = mergeFailure(teardownFailure, failure);
        }
        if (teardownFailure != null) {
            throw teardownFailure;
        }
    }

    private static RuntimeException mergeFailure(
            RuntimeException current,
            RuntimeException next
    ) {
        if (current == null) {
            return next;
        }
        if (current != next) {
            current.addSuppressed(next);
        }
        return current;
    }

    private void releaseTacticalLease() {
        if (closed) {
            return;
        }
        closed = true;
        closeAction.run();
    }

    private List<Ldlib2AccessibilityController.FocusTarget> focusTargets() {
        List<Ldlib2AccessibilityController.FocusTarget> targets =
                new ArrayList<>();
        targets.add(canvas);
        targets.add(controlsToggle);
        targets.add(floorSelector);
        targets.add(autoManual);
        for (UIElement element : filterList.getChildren()) {
            if (element instanceof Ldlib2AccessibilityController.FocusTarget target) {
                targets.add(target);
            }
        }
        targets.add(fitFloor);
        targets.add(fitAll);
        targets.add(close);
        return targets;
    }

    private void ensureResponsiveLayout() {
        if (currentLayout == null
                || appliedLayoutWidth != width
                || appliedLayoutHeight != height) {
            applyResponsiveLayout();
        }
    }

    private void toggleControlsDrawer() {
        setControlsDrawerOpen(!controlsDrawerOpen);
    }

    private void setControlsDrawerOpen(boolean open) {
        if (controlsDrawerOpen == open) {
            return;
        }
        controlsDrawerOpen = open;
        if (!open) {
            // Closing a drawer must leave keyboard users on its owning toggle;
            // reconciliation alone would otherwise keep a now-hidden child.
            controlsToggle.focus();
        }
        applyResponsiveLayout();
    }

    private void applyResponsiveLayout() {
        TacticalMapLayoutModel layout = TacticalMapLayoutModel.responsive(
                width,
                height,
                controlsDrawerOpen
        );
        if (layout.mode() != TacticalMapLayoutModel.Mode.DRAWER
                && controlsDrawerOpen) {
            controlsDrawerOpen = false;
            layout = TacticalMapLayoutModel.responsive(width, height, false);
        }
        currentLayout = layout;
        appliedLayoutWidth = width;
        appliedLayoutHeight = height;
        applyRect(canvas, layout.canvas());
        applyRect(stateLabel, new TacticalMapLayoutModel.Rect(
                layout.canvas().x(),
                layout.canvas().y() + Math.max(0, layout.canvas().height() / 2 - 12),
                layout.canvas().width(),
                Math.min(24, layout.canvas().height())
        ));
        applyRect(sidebar, layout.sidebar());
        applyRect(controlsToggle, layout.controlsToggle());

        boolean drawer = layout.mode() == TacticalMapLayoutModel.Mode.DRAWER;
        setSidebarAvailable(!drawer || controlsDrawerOpen);
        setControlsToggleAvailable(drawer);
        layoutSidebarButtons(layout.sidebar());
        accessibility().reconcileFocus();
        resizeController();
    }

    private static void applyRect(
            UIElement element,
            TacticalMapLayoutModel.Rect rect
    ) {
        element.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(rect.x())
                .top(rect.y())
                .width(rect.width())
                .height(rect.height()));
    }

    private void setSidebarAvailable(boolean available) {
        sidebar.setDisplay(available);
        sidebar.setVisible(available);
        sidebar.setActive(available);
        sidebar.setAllowHitTest(available);
    }

    private void setControlsToggleAvailable(boolean available) {
        controlsToggle.setDisplay(available);
        controlsToggle.setVisible(available);
        controlsToggle.setActive(available);
        controlsToggle.setAllowHitTest(available);
    }

    private void layoutSidebarButtons(TacticalMapLayoutModel.Rect sidebarRect) {
        int padding = Math.min(12, sidebarRect.width() / 4);
        int availableWidth = Math.max(0, sidebarRect.width() - padding * 2);
        int gap = Math.min(8, availableWidth / 3);
        int buttonWidth = Math.max(0, (availableWidth - gap) / 2);
        fitFloor.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(padding)
                .bottom(38)
                .width(buttonWidth)
                .height(24));
        fitAll.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(padding + buttonWidth + gap)
                .bottom(38)
                .width(buttonWidth)
                .height(24));
    }

    private void resizeController() {
        if (currentLayout == null) {
            return;
        }
        controller.resize(
                currentLayout.canvas().width(),
                currentLayout.canvas().height()
        );
    }

    private void refreshFrame() {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        var position = camera.getPosition();
        MinimapClientSettings settings = ForgeMinimapClientSettings.read(
                FPSMConfig.client
        );
        controller.setManualTimeoutTicks(settings.manualFloorTimeoutTicks());
        Optional<TacticalMapPresentation> prepared = presentation.prepareTactical(
                new MinimapViewerPose(
                        position.x,
                        position.y,
                        position.z,
                        camera.getYRot()
                ),
                settings,
                controller.state()
        );
        if (prepared.isEmpty()) {
            currentPresentation = null;
            canvas.clearFrame();
            stateLabel.setText(Component.translatable(
                    "gui.fpsm.minimap.placeholder.error"
            ));
            stateLabel.setDisplay(true);
            stateLabel.setVisible(true);
            stateLabel.setActive(true);
            filterList.clearAllChildren();
            legend.clearAllChildren();
            markerControls.clear();
            rebuildRegionDetail(Optional.empty());
            displayedFloors = List.of();
            displayedLegend = List.of();
            displayedHiddenTypes = Set.of();
            floorSelector.setCandidates(List.of(AUTOMATIC_FLOOR));
            floorSelector.setSelected(AUTOMATIC_FLOOR, false);
            accessibility().reconcileFocus();
            transitionAvailability(Availability.UNAVAILABLE);
            return;
        }
        stateLabel.setDisplay(false);
        stateLabel.setVisible(false);
        stateLabel.setActive(false);
        prepared.ifPresent(current -> {
            currentPresentation = current;
            controller.applyViewport(
                    current.viewport(),
                    current.frame().camera(),
                    current.frame().floor()
            );
            canvas.present(current.frame());
            refreshControls(current);
        });
        transitionAvailability(Availability.AVAILABLE);
    }

    private void transitionAvailability(Availability next) {
        Availability previous = availability;
        if (previous != next) {
            availability = next;
            if (next == Availability.UNAVAILABLE) {
                Component message = Component.translatable(
                        "gui.fpsm.minimap.placeholder.error"
                );
                announce(message, true);
            } else if (previous == Availability.UNAVAILABLE) {
                // Recovery clears the old error and rearms the next outage edge.
                accessibility().clearAnnouncement();
            }
        }
    }

    private void selectRegion(double mouseX, double mouseY) {
        TacticalMapPresentation current = currentPresentation;
        if (current == null) {
            rebuildRegionDetail(Optional.empty());
            return;
        }
        TacticalCanvasInput.Point point = TacticalCanvasInput.canvasPoint(
                mouseX,
                mouseY,
                canvas.getPositionX(),
                canvas.getPositionY(),
                canvas.getSizeWidth(),
                canvas.getSizeHeight(),
                current.frame().camera()
        );
        Optional<TacticalMapPresentation.RegionDetail> selected = current.regionAt(
                point.x(), point.y()
        );
        rebuildRegionDetail(selected);
        announceRegionDetail(selected);
    }

    private void announceRegionDetail(
            Optional<TacticalMapPresentation.RegionDetail> selected
    ) {
        List<Component> parts = new ArrayList<>();
        parts.add(Component.translatable(
                "gui.fpsm.minimap.tactical.region_detail"
        ));
        if (selected.isEmpty()) {
            parts.add(Component.translatable(
                    "gui.fpsm.minimap.tactical.none"
            ));
        } else {
            TacticalMapPresentation.RegionDetail detail = selected.orElseThrow();
            parts.add(component(detail.label()));
            parts.add(Component.translatable(
                    "gui.fpsm.minimap.tactical.region_semantic",
                    detail.semanticType().toString()
            ));
            if (!detail.tags().isEmpty()) {
                parts.add(Component.translatable(
                        "gui.fpsm.minimap.tactical.region_tags",
                        detail.tags().stream()
                                .map(Object::toString)
                                .collect(java.util.stream.Collectors.joining(", "))
                ));
            }
            detail.gameplayReference().ifPresent(reference ->
                    parts.add(Component.translatable(
                            "gui.fpsm.minimap.tactical.region_reference",
                            reference.toString()
                    ))
            );
        }
        Component summary = CommonComponents.joinForNarration(
                parts.toArray(Component[]::new)
        );
        announce(summary, false);
    }

    private void rebuildRegionDetail(
            Optional<TacticalMapPresentation.RegionDetail> selected
    ) {
        regionDetail.clearAllChildren();
        Label heading = label(Component.translatable(
                "gui.fpsm.minimap.tactical.region_detail"
        ));
        heading.layout(layout -> layout.widthPercent(100).height(20));
        regionDetail.addChild(heading);
        if (selected.isEmpty()) {
            regionDetail.addChild(emptyLabel());
            return;
        }
        TacticalMapPresentation.RegionDetail detail = selected.orElseThrow();
        regionDetail.addChild(detailLine(component(detail.label())));
        regionDetail.addChild(detailLine(Component.translatable(
                "gui.fpsm.minimap.tactical.region_semantic",
                detail.semanticType().toString()
        )));
        if (!detail.tags().isEmpty()) {
            regionDetail.addChild(detailLine(Component.translatable(
                    "gui.fpsm.minimap.tactical.region_tags",
                    detail.tags().stream()
                            .map(Object::toString)
                            .collect(java.util.stream.Collectors.joining(", "))
            )));
        }
        detail.gameplayReference().ifPresent(reference ->
                regionDetail.addChild(detailLine(Component.translatable(
                        "gui.fpsm.minimap.tactical.region_reference",
                        reference.toString()
                )))
        );
    }

    private static Label detailLine(Component text) {
        Label line = label(text);
        line.layout(layout -> layout.widthPercent(100).height(18));
        return line;
    }

    private void refreshControls(TacticalMapPresentation current) {
        TacticalMapState state = controller.state();
        boolean automatic = state.floor().mode() == FloorViewMode.AUTOMATIC;
        autoManual.setOn(automatic, false);
        autoManual.setText(Component.translatable(automatic
                ? "gui.fpsm.minimap.tactical.automatic"
                : "gui.fpsm.minimap.tactical.manual"));

        if (!displayedFloors.equals(current.floors())) {
            displayedFloors = current.floors();
            floorLabels.clear();
            floorLabels.put(
                    AUTOMATIC_FLOOR,
                    Component.translatable(
                            "gui.fpsm.minimap.tactical.follow_player"
                    )
            );
            List<String> floorCandidates = new ArrayList<>();
            floorCandidates.add(AUTOMATIC_FLOOR);
            for (TacticalMapPresentation.FloorOption floor : displayedFloors) {
                floorCandidates.add(floor.id());
                floorLabels.put(floor.id(), component(floor.label()));
            }
            floorSelector.setCandidates(floorCandidates);
        }
        String selectedFloor = automatic
                ? AUTOMATIC_FLOOR
                : state.floor().effectiveFloorId().orElse(AUTOMATIC_FLOOR);
        floorSelector.setSelected(selectedFloor, false);

        Set<String> hiddenTypes = state.hiddenMarkerTypes();
        if (!displayedLegend.equals(current.legend())
                || !displayedHiddenTypes.equals(hiddenTypes)) {
            displayedLegend = current.legend();
            displayedHiddenTypes = Set.copyOf(hiddenTypes);
            rebuildMarkerControls();
        }
    }

    private void rebuildMarkerControls() {
        String focusedType = null;
        UIElement focused = modularUI.getFocusedElement();
        for (Map.Entry<String, AccessibleToggle> entry : markerControls.entrySet()) {
            if (entry.getValue() == focused) {
                focusedType = entry.getKey();
                break;
            }
        }
        markerControls.clear();
        filterList.clearAllChildren();
        legend.clearAllChildren();

        Label filterHeading = label(Component.translatable(
                "gui.fpsm.minimap.tactical.filters"
        ));
        filterHeading.layout(layout -> layout.widthPercent(100).height(20));
        filterList.addChild(filterHeading);

        Map<String, DisplayLabel> markerTypes = new java.util.LinkedHashMap<>();
        for (TacticalMapPresentation.LegendEntry entry : displayedLegend) {
            markerTypes.putIfAbsent(entry.typeId().toString(), entry.label());
        }
        if (markerTypes.isEmpty()) {
            filterList.addChild(emptyLabel());
        } else {
            for (Map.Entry<String, DisplayLabel> entry : markerTypes.entrySet()) {
                String typeId = entry.getKey();
                AccessibleToggle toggle = new AccessibleToggle();
                toggle.setText(component(entry.getValue()));
                // The explicit name keeps narration stable even if LDLib2 changes
                // how Toggle derives its default label from the child element.
                toggle.setAccessibleName(component(entry.getValue()));
                toggle.setOn(!displayedHiddenTypes.contains(typeId), false);
                toggle.setOnToggleChanged(visible -> {
                    Set<String> hidden = new HashSet<>(
                            controller.state().hiddenMarkerTypes()
                    );
                    if (visible) {
                        hidden.remove(typeId);
                    } else {
                        hidden.add(typeId);
                    }
                    controller.setHiddenMarkerTypes(hidden);
                });
                toggle.setAccessibleHint(() -> Component.translatable(
                        "gui.fpsm.minimap.tactical.filter_hint"
                ));
                FPSMLdlib2Theme.settingsCategoryToggle(toggle);
                toggle.layout(layout -> layout.widthPercent(100).height(20));
                filterList.addChild(toggle);
                markerControls.put(typeId, toggle);
            }
        }

        if (focusedType != null) {
            AccessibleToggle replacement = markerControls.get(focusedType);
            if (replacement != null) {
                replacement.focus();
            }
        }

        Label legendHeading = label(Component.translatable(
                "gui.fpsm.minimap.tactical.legend"
        ));
        legendHeading.layout(layout -> layout.widthPercent(100).height(20));
        legend.addChild(legendHeading);
        if (displayedLegend.isEmpty()) {
            legend.addChild(emptyLabel());
        } else {
            for (TacticalMapPresentation.LegendEntry entry : displayedLegend) {
                Label type = label(component(entry.label()));
                type.layout(layout -> layout.widthPercent(100).height(18));
                legend.addChild(type);
            }
        }
    }

    private Label emptyLabel() {
        Label empty = label(Component.translatable(
                "gui.fpsm.minimap.tactical.none"
        ));
        empty.layout(layout -> layout.widthPercent(100).height(20));
        return empty;
    }

    private void bindRequiredWidgets() {
        for (String id : TacticalMapWidgetCatalog.defaultCatalog().ids()) {
            if (modularUI.getElementsById(id).size() != 1) {
                throw new IllegalStateException(
                        "Missing or duplicate tactical widget binding: " + id
                );
            }
        }
    }

}
