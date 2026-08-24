package com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2.editor;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.ptcrys.fpsmatch.common.client.minimap.editor.EditorStatus;
import com.ptcrys.fpsmatch.common.client.minimap.editor.EditorTool;
import com.ptcrys.fpsmatch.common.client.minimap.editor.MinimapEditorController;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleButton;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Builds and styles the editor element tree without owning editor lifecycle semantics. */
final class MinimapEditorUiView {
    private MinimapEditorUiView() {
    }

    static Parts build(MinimapEditorController controller) {
        Objects.requireNonNull(controller, "controller");

        UIElement root = new UIElement();
        root.layout(layout -> layout.widthPercent(100).heightPercent(100));
        FPSMLdlib2Theme.root(root);

        UIElement toolbar = new UIElement().setId(EditorLdlibWidgetCatalog.TOOLBAR);
        toolbar.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(0).top(0).right(0).height(40));
        FPSMLdlib2Theme.panel(toolbar);

        Ldlib2EditorCanvasElement canvas = new Ldlib2EditorCanvasElement(
                EditorLdlibWidgetCatalog.CANVAS, controller
        );
        canvas.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(8).top(48).right(220).bottom(68));

        UIElement layerPanel = new UIElement().setId(EditorLdlibWidgetCatalog.LAYER_PANEL);
        layerPanel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .width(200).top(48).right(8).heightPercent(35));
        FPSMLdlib2Theme.panel(layerPanel);

        UIElement properties = new UIElement().setId(EditorLdlibWidgetCatalog.PROPERTIES);
        properties.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .width(200).bottom(68).right(8).heightPercent(35));
        FPSMLdlib2Theme.panel(properties);

        UIElement floorStrip = new UIElement().setId(EditorLdlibWidgetCatalog.FLOOR_STRIP);
        floorStrip.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(8).right(220).bottom(36).height(28));
        FPSMLdlib2Theme.elevated(floorStrip);

        UIElement statusBar = new UIElement().setId(EditorLdlibWidgetCatalog.STATUS_BAR);
        statusBar.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(0).right(0).bottom(0).height(28));
        FPSMLdlib2Theme.statusSurface(statusBar, FPSMLdlib2Theme.MUTED);

        Label layerTitle = label(Component.translatable("gui.fpsm.minimap.editor.layers"));
        layerTitle.layout(layout -> layout.widthPercent(100).height(18));
        FPSMLdlib2Theme.sectionTitle(layerTitle);
        Label layerBody = label(Component.translatable("gui.fpsm.minimap.editor.layers.empty"));
        layerBody.layout(layout -> layout.widthPercent(100).height(18));
        FPSMLdlib2Theme.body(layerBody);
        layerPanel.addChildren(layerTitle, layerBody);

        Label propsTitle = label(Component.translatable("gui.fpsm.minimap.editor.properties"));
        propsTitle.layout(layout -> layout.widthPercent(100).height(18));
        FPSMLdlib2Theme.sectionTitle(propsTitle);
        Label propsBody = label(Component.translatable("gui.fpsm.minimap.editor.properties.empty"));
        propsBody.layout(layout -> layout.widthPercent(100).height(18));
        FPSMLdlib2Theme.body(propsBody);
        properties.addChildren(propsTitle, propsBody);

        Label floorTitle = label(Component.translatable("gui.fpsm.minimap.editor.floor", "-"));
        floorTitle.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(4).top(4).right(4).height(18));
        FPSMLdlib2Theme.body(floorTitle);
        floorStrip.addChildren(floorTitle);

        Label statusLabel = label(Component.empty());
        statusLabel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(8).top(6).right(8).height(16));
        FPSMLdlib2Theme.status(statusLabel, FPSMLdlib2Theme.MUTED);
        statusBar.addChildren(statusLabel);

        SaveTrigger saveTrigger = new SaveTrigger();
        PublishTrigger publishTrigger = new PublishTrigger();
        CloseTrigger closeTrigger = new CloseTrigger();
        UndoTrigger undoTrigger = new UndoTrigger();
        RedoTrigger redoTrigger = new RedoTrigger();
        ToolTrigger toolTrigger = new ToolTrigger();

        Map<AccessibleButton, FPSMLdlib2Theme.ButtonKind> buttonKinds =
                new IdentityHashMap<>();
        Map<EditorTool, AccessibleButton> toolButtons = new LinkedHashMap<>();
        List<AccessibleButton> toolbarButtons = new ArrayList<>();
        AccessibleButton pan = button(
                "gui.fpsm.minimap.editor.tool.pan",
                FPSMLdlib2Theme.ButtonKind.SECONDARY,
                () -> toolTrigger.select(EditorTool.PAN), buttonKinds
        );
        AccessibleButton brush = button(
                "gui.fpsm.minimap.editor.tool.brush",
                FPSMLdlib2Theme.ButtonKind.SECONDARY,
                () -> toolTrigger.select(EditorTool.BRUSH), buttonKinds
        );
        AccessibleButton eraser = button(
                "gui.fpsm.minimap.editor.tool.eraser",
                FPSMLdlib2Theme.ButtonKind.SECONDARY,
                () -> toolTrigger.select(EditorTool.ERASER), buttonKinds
        );
        AccessibleButton selection = button(
                "gui.fpsm.minimap.editor.tool.selection",
                FPSMLdlib2Theme.ButtonKind.SECONDARY,
                () -> toolTrigger.select(EditorTool.SELECTION), buttonKinds
        );
        // Keep the tool registry semantic so the screen can project selected and
        // disabled state without relying on translated labels or button identity.
        toolButtons.put(EditorTool.PAN, pan);
        toolButtons.put(EditorTool.BRUSH, brush);
        toolButtons.put(EditorTool.ERASER, eraser);
        toolButtons.put(EditorTool.SELECTION, selection);
        AccessibleButton undo = button(
                "gui.fpsm.minimap.editor.undo",
                FPSMLdlib2Theme.ButtonKind.QUIET,
                undoTrigger::run, buttonKinds
        );
        AccessibleButton redo = button(
                "gui.fpsm.minimap.editor.redo",
                FPSMLdlib2Theme.ButtonKind.QUIET,
                redoTrigger::run, buttonKinds
        );
        AccessibleButton save = button(
                "gui.fpsm.minimap.editor.save",
                FPSMLdlib2Theme.ButtonKind.SECONDARY,
                saveTrigger::run, buttonKinds
        );
        AccessibleButton publish = button(
                "gui.fpsm.minimap.editor.publish",
                FPSMLdlib2Theme.ButtonKind.PRIMARY,
                publishTrigger::run, buttonKinds
        );
        AccessibleButton close = button(
                "gui.fpsm.minimap.editor.close",
                FPSMLdlib2Theme.ButtonKind.DANGER,
                closeTrigger::run, buttonKinds
        );
        AccessibleButton panels = button(
                "gui.fpsm.minimap.editor.panels",
                FPSMLdlib2Theme.ButtonKind.QUIET,
                () -> { }, buttonKinds
        );
        toolbarButtons.addAll(List.of(
                pan, brush, eraser, selection, undo, redo, save, publish, close, panels
        ));
        toolbar.addChildren(toolbarButtons.toArray(new UIElement[0]));

        AccessibleButton saveClose = button(
                "gui.fpsm.minimap.editor.close.save",
                FPSMLdlib2Theme.ButtonKind.PRIMARY,
                () -> { }, buttonKinds
        );
        AccessibleButton discardClose = button(
                "gui.fpsm.minimap.editor.close.discard",
                FPSMLdlib2Theme.ButtonKind.DANGER,
                () -> { }, buttonKinds
        );
        AccessibleButton cancelClose = button(
                "gui.fpsm.minimap.editor.close.cancel",
                FPSMLdlib2Theme.ButtonKind.SECONDARY,
                () -> { }, buttonKinds
        );
        statusBar.addChildren(saveClose, discardClose, cancelClose);

        root.addChildren(toolbar, canvas, layerPanel, properties, floorStrip, statusBar);
        return new Parts(
                ModularUI.of(UI.of(root)), toolbar, canvas, layerPanel, properties,
                floorStrip, statusBar, statusLabel, layerBody, propsBody, floorTitle,
                List.copyOf(toolbarButtons), Map.copyOf(toolButtons), buttonKinds, pan, panels, save, publish,
                close, saveClose, discardClose, cancelClose, saveTrigger,
                publishTrigger, closeTrigger, undoTrigger, redoTrigger, toolTrigger
        );
    }

    static void buttonState(
            Map<AccessibleButton, FPSMLdlib2Theme.ButtonKind> kinds,
            AccessibleButton button,
            boolean enabled
    ) {
        FPSMLdlib2Theme.ButtonKind kind = Objects.requireNonNull(
                kinds.get(button), "button kind"
        );
        FPSMLdlib2Theme.buttonState(button, kind, enabled);
    }

    static void toolButtonState(
            Map<AccessibleButton, FPSMLdlib2Theme.ButtonKind> kinds,
            AccessibleButton button,
            boolean selected,
            boolean enabled
    ) {
        Component label = button.accessibleName().get();
        button.setText(Component.literal(selected ? "[x] " : "[ ] ").append(label));
        button.setAccessibleState(() -> selected
                ? Component.translatable("gui.fpsm.minimap.editor.tool.selected")
                : Component.empty());
        // Apply the interaction state last: selected styling must never make a
        // busy/disabled tool focusable or clickable again.
        FPSMLdlib2Theme.ButtonKind kind = Objects.requireNonNull(
                kinds.get(button), "button kind"
        );
        FPSMLdlib2Theme.buttonState(button, kind, enabled);
    }

    static void statusTone(UIElement statusBar, Label statusLabel, int tone) {
        FPSMLdlib2Theme.statusSurface(statusBar, tone);
        FPSMLdlib2Theme.status(statusLabel, tone);
    }

    static int resolveStatusTone(
            EditorStatus status,
            boolean dirty,
            boolean unsaved,
            boolean busy,
            boolean acceptanceError,
            Integer noticeTone
    ) {
        Objects.requireNonNull(status, "status");
        if (acceptanceError || status == EditorStatus.ERROR
                || status == EditorStatus.DETACHED) {
            return FPSMLdlib2Theme.DANGER;
        }
        if (status == EditorStatus.CLOSED) {
            return FPSMLdlib2Theme.DISABLED;
        }
        if (noticeTone != null) {
            return noticeTone;
        }
        if (dirty || unsaved || status == EditorStatus.DIRTY) {
            return FPSMLdlib2Theme.WARNING;
        }
        if (busy || switch (status) {
            case LOADING, SAVING, REBASING, RESUMING, PUBLISHING -> true;
            default -> false;
        }) {
            return FPSMLdlib2Theme.ACCENT;
        }
        return status == EditorStatus.READY
                ? FPSMLdlib2Theme.SUCCESS : FPSMLdlib2Theme.MUTED;
    }

    private static AccessibleButton button(
            String key,
            FPSMLdlib2Theme.ButtonKind kind,
            Runnable action,
            Map<AccessibleButton, FPSMLdlib2Theme.ButtonKind> kinds
    ) {
        AccessibleButton button = new AccessibleButton();
        button.setText(Component.translatable(key));
        button.setAccessibleName(Component.translatable(key));
        button.setOnClick(event -> action.run());
        FPSMLdlib2Theme.button(button, kind);
        kinds.put(button, kind);
        return button;
    }

    private static Label label(Component text) {
        Label label = new Label();
        label.setText(text);
        label.setAllowHitTest(false);
        label.setFocusable(false);
        return label;
    }

    record StatusNotice(Component text, int tone, String code) {
        StatusNotice(Component text, int tone) {
            this(text, tone, "notice");
        }

        StatusNotice {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(code, "code");
        }
    }

    record StatusIdentity(String identity, String error) {
        StatusIdentity {
            Objects.requireNonNull(identity, "identity");
            error = error == null ? "" : error;
        }
    }

    record Parts(
            ModularUI ui,
            UIElement toolbar,
            Ldlib2EditorCanvasElement canvas,
            UIElement layerPanel,
            UIElement properties,
            UIElement floorStrip,
            UIElement statusBar,
            Label statusLabel,
            Label layerBody,
            Label propertiesBody,
            Label floorTitle,
            List<AccessibleButton> toolbarButtons,
            Map<EditorTool, AccessibleButton> toolButtons,
            Map<AccessibleButton, FPSMLdlib2Theme.ButtonKind> buttonKinds,
            AccessibleButton panButton,
            AccessibleButton panelToggle,
            AccessibleButton saveButton,
            AccessibleButton publishButton,
            AccessibleButton closeButton,
            AccessibleButton saveCloseButton,
            AccessibleButton discardCloseButton,
            AccessibleButton cancelCloseButton,
            SaveTrigger saveTrigger,
            PublishTrigger publishTrigger,
            CloseTrigger closeTrigger,
            UndoTrigger undoTrigger,
            RedoTrigger redoTrigger,
            ToolTrigger toolTrigger
    ) {
    }

    static final class SaveTrigger extends Trigger {
    }

    static final class PublishTrigger extends Trigger {
    }

    static final class CloseTrigger extends Trigger {
    }

    static final class UndoTrigger extends Trigger {
    }

    static final class RedoTrigger extends Trigger {
    }

    private abstract static class Trigger {
        private Runnable action = () -> { };

        final void bind(Runnable action) {
            this.action = Objects.requireNonNull(action, "action");
        }

        final void run() {
            action.run();
        }
    }

    static final class ToolTrigger {
        private Consumer<EditorTool> action = tool -> { };

        void bind(Consumer<EditorTool> action) {
            this.action = Objects.requireNonNull(action, "action");
        }

        void select(EditorTool tool) {
            action.accept(tool);
        }
    }
}
