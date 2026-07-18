package com.phasetranscrystal.fpsmatch.common.client.minimap.ui.ldlib2;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.ForgeMinimapClientSettings;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.MinimapClientSettings;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.MinimapViewerPose;
import com.phasetranscrystal.fpsmatch.common.client.minimap.tactical.TacticalMapController;
import com.phasetranscrystal.fpsmatch.common.client.minimap.tactical.TacticalMapPresentation;
import com.phasetranscrystal.fpsmatch.common.client.minimap.tactical.TacticalMapState;
import com.phasetranscrystal.fpsmatch.config.FPSMConfig;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.view.FloorViewMode;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;

public final class Ldlib2TacticalMapScreen extends ModularUIScreen {
    private static final int SIDEBAR_WIDTH = 220;
    private static final String AUTOMATIC_FLOOR = "AUTO";

    private final TacticalMapController controller;
    private final Runnable closeAction;
    private final Ldlib2MinimapHudPresentation presentation;
    private final Ldlib2MinimapCanvasElement canvas;
    private final Toggle autoManual;
    private final Selector<String> floorSelector;
    private final UIElement filterList;
    private final UIElement legend;
    private final UIElement regionDetail;
    private final Map<String, Component> floorLabels;

    private List<TacticalMapPresentation.FloorOption> displayedFloors = List.of();
    private List<TacticalMapPresentation.LegendEntry> displayedLegend = List.of();
    private Set<String> displayedHiddenTypes = Set.of();
    private TacticalMapPresentation currentPresentation;
    private boolean closed;

    public Ldlib2TacticalMapScreen(
            TacticalMapController controller,
            Runnable closeAction,
            Ldlib2MinimapHudPresentation presentation
    ) {
        this(build(controller, presentation), controller, closeAction, presentation);
    }

    private Ldlib2TacticalMapScreen(
            Parts parts,
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
        this.autoManual = parts.autoManual();
        this.floorSelector = parts.floorSelector();
        this.filterList = parts.filterList();
        this.legend = parts.legend();
        this.regionDetail = parts.regionDetail();
        this.floorLabels = parts.floorLabels();
        parts.closeTrigger().bind(this::onClose);
        parts.regionTrigger().bind(this::selectRegion);
        bindRequiredWidgets();
    }

    @Override
    public void init() {
        super.init();
        resizeController();
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
        resizeController();
        refreshFrame();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        releaseTacticalLease();
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void removed() {
        releaseTacticalLease();
        super.removed();
    }

    private void releaseTacticalLease() {
        if (closed) {
            return;
        }
        closed = true;
        closeAction.run();
    }

    private void resizeController() {
        controller.resize(
                Math.max(1, Math.round(canvas.getSizeWidth())),
                Math.max(1, Math.round(canvas.getSizeHeight()))
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
        presentation.prepareTactical(
                new MinimapViewerPose(
                        position.x,
                        position.y,
                        position.z,
                        camera.getYRot()
                ),
                settings,
                controller.state()
        ).ifPresent(current -> {
            currentPresentation = current;
            controller.applyViewport(
                    current.viewport(),
                    current.frame().camera(),
                    current.frame().floor()
            );
            canvas.present(current.frame());
            refreshControls(current);
        });
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
        rebuildRegionDetail(current.regionAt(point.x(), point.y()));
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
        filterList.clearAllChildren();
        legend.clearAllChildren();

        Label filterHeading = label(Component.translatable(
                "gui.fpsm.minimap.tactical.filters"
        ));
        filterHeading.layout(layout -> layout.widthPercent(100).height(20));
        filterList.addChild(filterHeading);

        List<String> markerTypes = displayedLegend.stream()
                .map(entry -> entry.typeId().toString())
                .distinct()
                .toList();
        if (markerTypes.isEmpty()) {
            filterList.addChild(emptyLabel());
        } else {
            for (String typeId : markerTypes) {
                Toggle toggle = new Toggle();
                toggle.setText(compact(typeId));
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
                toggle.layout(layout -> layout.widthPercent(100).height(20));
                filterList.addChild(toggle);
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
                Label style = label(Component.literal(compact(
                        entry.typeId() + " / " + entry.styleId()
                )));
                style.layout(layout -> layout.widthPercent(100).height(16));
                legend.addChildren(type, style);
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

    private static Parts build(
            TacticalMapController controller,
            Ldlib2MinimapHudPresentation presentation
    ) {
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(presentation, "presentation");

        UIElement root = new UIElement().setId(TacticalMapWidgetCatalog.ROOT);
        root.layout(layout -> layout.widthPercent(100).heightPercent(100));

        Ldlib2MinimapCanvasElement canvas = new Ldlib2MinimapCanvasElement(
                TacticalMapWidgetCatalog.CANVAS,
                presentation.textureResolver(),
                presentation.markerPresentationResolver(),
                true
        );
        canvas.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(SIDEBAR_WIDTH)
                .right(0)
                .top(0)
                .bottom(0));

        UIElement sidebar = new UIElement();
        sidebar.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(0)
                .top(0)
                .bottom(0)
                .width(SIDEBAR_WIDTH));

        Label title = label(Component.translatable(
                "gui.fpsm.minimap.tactical.title"
        ));
        title.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(12).right(12).top(10).height(20));

        Map<String, Component> floorLabels = new HashMap<>();
        Selector<String> floorSelector = new Selector<>();
        floorSelector.setId(TacticalMapWidgetCatalog.FLOOR_SELECTOR);
        floorSelector.setCandidateUIProvider(candidate -> {
            Label candidateLabel = new Label();
            candidateLabel.setText(floorLabels.getOrDefault(
                    candidate, Component.literal(candidate)
            ));
            return candidateLabel;
        });
        floorSelector.setCandidates(List.of(AUTOMATIC_FLOOR));
        floorSelector.setSelected(AUTOMATIC_FLOOR, false);
        floorSelector.setOnValueChanged(value -> {
            if (AUTOMATIC_FLOOR.equals(value)) {
                controller.resumeAutomaticFloor();
            } else if (value != null) {
                controller.selectFloor(value);
            }
        });
        floorSelector.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(12).right(12).top(36).height(24));

        Toggle autoManual = new Toggle();
        autoManual.setId(TacticalMapWidgetCatalog.AUTO_MANUAL);
        autoManual.setText(Component.translatable(
                "gui.fpsm.minimap.tactical.automatic"
        ));
        autoManual.setOn(true, false);
        autoManual.setOnToggleChanged(automatic -> {
            if (automatic) {
                controller.resumeAutomaticFloor();
            } else {
                controller.state().floor().effectiveFloorId()
                        .ifPresent(controller::selectFloor);
            }
        });
        autoManual.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(12).right(12).top(66).height(22));

        UIElement filterList = new UIElement()
                .setId(TacticalMapWidgetCatalog.FILTER_LIST);
        filterList.layout(layout -> layout.widthPercent(100));
        UIElement legend = new UIElement()
                .setId(TacticalMapWidgetCatalog.LEGEND);
        legend.layout(layout -> layout.widthPercent(100));
        UIElement regionDetail = new UIElement()
                .setId(TacticalMapWidgetCatalog.REGION_DETAIL);
        regionDetail.layout(layout -> layout.widthPercent(100));
        ScrollerView details = new ScrollerView();
        details.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL));
        details.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(8).right(8).top(96).bottom(68));
        details.addScrollViewChildren(regionDetail, filterList, legend);

        Button fitFloor = new Button();
        fitFloor.setId(TacticalMapWidgetCatalog.FIT);
        fitFloor.setText(Component.translatable(
                "gui.fpsm.minimap.tactical.fit_floor"
        ));
        fitFloor.setOnClick(event -> controller.fitFloor());
        fitFloor.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(12).bottom(38).width(94).height(24));

        Button fitAll = new Button();
        fitAll.setText(Component.translatable(
                "gui.fpsm.minimap.tactical.fit_all"
        ));
        fitAll.setOnClick(event -> controller.fitAll());
        fitAll.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(114).bottom(38).width(94).height(24));

        CloseTrigger closeTrigger = new CloseTrigger();
        RegionTrigger regionTrigger = new RegionTrigger();
        CanvasGesture gesture = new CanvasGesture();
        Button close = new Button();
        close.setId(TacticalMapWidgetCatalog.CLOSE);
        close.setText(Component.translatable(
                "gui.fpsm.minimap.tactical.close"
        ));
        close.setOnClick(event -> closeTrigger.close());
        close.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(12).right(12).bottom(8).height(24));

        canvas.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            TacticalCanvasInput.Point cursor = TacticalCanvasInput.centered(
                    event.x,
                    event.y,
                    canvas.getPositionX(),
                    canvas.getPositionY(),
                    canvas.getSizeWidth(),
                    canvas.getSizeHeight()
            );
            controller.zoomByWheel(event.deltaY, cursor.x(), cursor.y());
            event.stopPropagation();
        });
        canvas.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 0) {
                gesture.begin();
                canvas.startDrag(null, null);
                event.stopPropagation();
            }
        });
        canvas.addEventListener(UIEvents.DRAG_UPDATE, event -> {
            gesture.drag(event.deltaX, event.deltaY);
            controller.panByPixels(event.deltaX, event.deltaY);
            event.stopPropagation();
        });
        canvas.addEventListener(UIEvents.CLICK, event -> {
            if (event.button == 0 && !gesture.wasDragged()) {
                regionTrigger.select(event.x, event.y);
                event.stopPropagation();
            }
        });

        sidebar.addChildren(
                title,
                floorSelector,
                autoManual,
                details,
                fitFloor,
                fitAll,
                close
        );
        root.addChildren(canvas, sidebar);
        return new Parts(
                ModularUI.of(UI.of(root)),
                canvas,
                autoManual,
                floorSelector,
                filterList,
                legend,
                regionDetail,
                floorLabels,
                closeTrigger,
                regionTrigger
        );
    }

    private static Component component(DisplayLabel label) {
        return label.type() == DisplayLabel.Type.TRANSLATION
                ? Component.translatable(label.value())
                : Component.literal(label.value());
    }

    private static Label label(Component text) {
        Label label = new Label();
        label.setText(text);
        return label;
    }

    private static String compact(String value) {
        return value.length() <= 30
                ? value
                : value.substring(0, 27) + "...";
    }

    private record Parts(
            ModularUI ui,
            Ldlib2MinimapCanvasElement canvas,
            Toggle autoManual,
            Selector<String> floorSelector,
            UIElement filterList,
            UIElement legend,
            UIElement regionDetail,
            Map<String, Component> floorLabels,
            CloseTrigger closeTrigger,
            RegionTrigger regionTrigger
    ) {
    }

    private static final class CloseTrigger {
        private Runnable action = () -> {
        };

        private void bind(Runnable action) {
            this.action = Objects.requireNonNull(action, "action");
        }

        private void close() {
            action.run();
        }
    }

    private static final class RegionTrigger {
        private java.util.function.BiConsumer<Double, Double> action =
                (mouseX, mouseY) -> {
                };

        private void bind(java.util.function.BiConsumer<Double, Double> action) {
            this.action = Objects.requireNonNull(action, "action");
        }

        private void select(double mouseX, double mouseY) {
            action.accept(mouseX, mouseY);
        }
    }

    private static final class CanvasGesture {
        private boolean dragged;

        private void begin() {
            dragged = false;
        }

        private void drag(double deltaX, double deltaY) {
            dragged |= deltaX != 0 || deltaY != 0;
        }

        private boolean wasDragged() {
            return dragged;
        }
    }
}
