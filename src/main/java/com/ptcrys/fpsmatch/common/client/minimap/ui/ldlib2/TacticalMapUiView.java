package com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.TacticalMapController;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleButton;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleSelector;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleToggle;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme;
import com.ptcrys.fpsmatch.core.minimap.model.DisplayLabel;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owns the tactical screen's LDLib2 composition while the screen owns state
 * and lifecycle. Keeping this boundary small prevents focus/input fixes from
 * growing the lifecycle shell past the helper size limit.
 */
final class TacticalMapUiView {
    static final String AUTOMATIC_FLOOR = "AUTO";

    private TacticalMapUiView() {
    }

    static Parts build(
            TacticalMapController controller,
            Ldlib2MinimapHudPresentation presentation
    ) {
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(presentation, "presentation");

        UIElement root = new UIElement().setId(TacticalMapWidgetCatalog.ROOT);
        root.layout(layout -> layout.widthPercent(100).heightPercent(100));
        FPSMLdlib2Theme.root(root);

        Ldlib2MinimapCanvasElement canvas = new Ldlib2MinimapCanvasElement(
                TacticalMapWidgetCatalog.CANVAS,
                presentation.textureResolver(),
                presentation.markerPresentationResolver(),
                true
        );
        canvas.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE));

        UIElement sidebar = new UIElement();
        sidebar.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE));

        Label title = label(Component.translatable(
                "gui.fpsm.minimap.tactical.title"
        ));
        FPSMLdlib2Theme.title(title);
        title.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(12).right(12).top(10).height(20));

        Map<String, Component> floorLabels = new HashMap<>();
        AccessibleSelector<String> floorSelector = new AccessibleSelector<>();
        floorSelector.setId(TacticalMapWidgetCatalog.FLOOR_SELECTOR);
        floorSelector.setCandidateUIProvider(candidate -> {
            Label candidateLabel = new Label();
            Component fallback = candidate == null
                    ? Component.empty()
                    : Component.literal(candidate);
            candidateLabel.setText(floorLabels.getOrDefault(candidate, fallback));
            return candidateLabel;
        });
        floorSelector.setCandidates(List.of(AUTOMATIC_FLOOR));
        floorSelector.setSelected(AUTOMATIC_FLOOR, false);
        floorSelector.setAccessibleName(Component.translatable(
                "gui.fpsm.minimap.tactical.floor_selector.name"
        ));
        floorSelector.setAccessibleHint(() -> Component.translatable(
                "gui.fpsm.minimap.tactical.floor_selector.hint"
        ));
        floorSelector.setValueNarration(value -> {
            if (value == null) {
                return Component.empty();
            }
            Component floor = floorLabels.getOrDefault(
                    value,
                    Component.literal(value)
            );
            return Component.translatable(
                    "gui.fpsm.minimap.tactical.floor_selector.value",
                    floor
            );
        });
        FPSMLdlib2Theme.selector(floorSelector);
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

        AccessibleToggle autoManual = new AccessibleToggle();
        autoManual.setId(TacticalMapWidgetCatalog.AUTO_MANUAL);
        autoManual.setText(Component.translatable(
                "gui.fpsm.minimap.tactical.automatic"
        ));
        autoManual.setOn(true, false);
        autoManual.setAccessibleName(Component.translatable(
                "gui.fpsm.minimap.tactical.floor_mode.name"
        ));
        autoManual.setAccessibleHint(() -> Component.translatable(
                "gui.fpsm.minimap.tactical.floor_mode.hint"
        ));
        FPSMLdlib2Theme.settingsCategoryToggle(autoManual);
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

        Label stateLabel = label(Component.empty());
        stateLabel.setId(TacticalMapWidgetCatalog.STATE);
        stateLabel.setDisplay(false);
        stateLabel.setVisible(false);
        stateLabel.setActive(false);
        stateLabel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE));

        AccessibleButton fitFloor = new AccessibleButton();
        fitFloor.setId(TacticalMapWidgetCatalog.FIT);
        fitFloor.setText(Component.translatable(
                "gui.fpsm.minimap.tactical.fit_floor"
        ));
        fitFloor.setAccessibleName(Component.translatable(
                "gui.fpsm.minimap.tactical.fit_floor"
        ));
        fitFloor.setAccessibleHint(() -> Component.translatable(
                "gui.fpsm.minimap.tactical.fit_floor.hint"
        ));
        fitFloor.setOnClick(event -> controller.fitFloor());
        FPSMLdlib2Theme.button(
                fitFloor,
                FPSMLdlib2Theme.ButtonKind.SECONDARY
        );
        fitFloor.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(12).bottom(38).width(94).height(24));

        AccessibleButton fitAll = new AccessibleButton();
        fitAll.setText(Component.translatable(
                "gui.fpsm.minimap.tactical.fit_all"
        ));
        fitAll.setAccessibleName(Component.translatable(
                "gui.fpsm.minimap.tactical.fit_all"
        ));
        fitAll.setAccessibleHint(() -> Component.translatable(
                "gui.fpsm.minimap.tactical.fit_all.hint"
        ));
        fitAll.setOnClick(event -> controller.fitAll());
        FPSMLdlib2Theme.button(
                fitAll,
                FPSMLdlib2Theme.ButtonKind.SECONDARY
        );
        fitAll.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(114).bottom(38).width(94).height(24));

        CloseTrigger closeTrigger = new CloseTrigger();
        RegionTrigger regionTrigger = new RegionTrigger();
        DrawerTrigger drawerTrigger = new DrawerTrigger();
        CanvasGesture gesture = new CanvasGesture();
        AccessibleButton close = new AccessibleButton();
        close.setId(TacticalMapWidgetCatalog.CLOSE);
        close.setText(Component.translatable(
                "gui.fpsm.minimap.tactical.close"
        ));
        close.setAccessibleName(Component.translatable(
                "gui.fpsm.minimap.tactical.close"
        ));
        close.setAccessibleHint(() -> Component.translatable(
                "gui.fpsm.minimap.tactical.close.hint"
        ));
        close.setOnClick(event -> closeTrigger.close());
        FPSMLdlib2Theme.button(close, FPSMLdlib2Theme.ButtonKind.DANGER);
        close.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(12).right(12).bottom(8).height(24));

        AccessibleButton controlsToggle = new AccessibleButton();
        controlsToggle.setId(TacticalMapWidgetCatalog.CONTROLS_TOGGLE);
        controlsToggle.setText(Component.translatable(
                "gui.fpsm.minimap.tactical.controls"
        ));
        controlsToggle.setAccessibleName(Component.translatable(
                "gui.fpsm.minimap.tactical.controls.name"
        ));
        controlsToggle.setAccessibleHint(() -> Component.translatable(
                "gui.fpsm.minimap.tactical.controls.hint"
        ));
        FPSMLdlib2Theme.button(
                controlsToggle,
                FPSMLdlib2Theme.ButtonKind.SECONDARY
        );
        controlsToggle.setOnClick(event -> drawerTrigger.toggle());
        controlsToggle.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE));

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
        FPSMLdlib2Theme.panel(sidebar);
        root.addChildren(canvas, stateLabel, sidebar, controlsToggle);
        return new Parts(
                ModularUI.of(UI.of(root)),
                canvas,
                sidebar,
                controlsToggle,
                autoManual,
                floorSelector,
                filterList,
                legend,
                regionDetail,
                stateLabel,
                floorLabels,
                fitFloor,
                fitAll,
                close,
                closeTrigger,
                regionTrigger,
                drawerTrigger
        );
    }

    static Component component(DisplayLabel label) {
        return label.type() == DisplayLabel.Type.TRANSLATION
                ? Component.translatable(label.value())
                : Component.literal(label.value());
    }

    static Label label(Component text) {
        Label label = new Label();
        label.setText(text);
        FPSMLdlib2Theme.body(label);
        return label;
    }

    record Parts(
            ModularUI ui,
            Ldlib2MinimapCanvasElement canvas,
            UIElement sidebar,
            AccessibleButton controlsToggle,
            AccessibleToggle autoManual,
            AccessibleSelector<String> floorSelector,
            UIElement filterList,
            UIElement legend,
            UIElement regionDetail,
            Label stateLabel,
            Map<String, Component> floorLabels,
            AccessibleButton fitFloor,
            AccessibleButton fitAll,
            AccessibleButton close,
            CloseTrigger closeTrigger,
            RegionTrigger regionTrigger,
            DrawerTrigger drawerTrigger
    ) {
    }

    static final class CloseTrigger {
        private Runnable action = () -> {
        };

        void bind(Runnable action) {
            this.action = Objects.requireNonNull(action, "action");
        }

        void close() {
            action.run();
        }
    }

    static final class DrawerTrigger {
        private Runnable action = () -> {
        };

        void bind(Runnable action) {
            this.action = Objects.requireNonNull(action, "action");
        }

        void toggle() {
            action.run();
        }
    }

    static final class RegionTrigger {
        private java.util.function.BiConsumer<Double, Double> action =
                (mouseX, mouseY) -> {
                };

        void bind(java.util.function.BiConsumer<Double, Double> action) {
            this.action = Objects.requireNonNull(action, "action");
        }

        void select(double mouseX, double mouseY) {
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
