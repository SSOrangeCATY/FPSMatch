package com.phasetranscrystal.fpsmatch.common.client.minimap.ui.ldlib2.editor;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.phasetranscrystal.fpsmatch.common.client.minimap.editor.CloseChoice;
import com.phasetranscrystal.fpsmatch.common.client.minimap.editor.CloseDecision;
import com.phasetranscrystal.fpsmatch.common.client.minimap.editor.EditorStatus;
import com.phasetranscrystal.fpsmatch.common.client.minimap.editor.EditorTool;
import com.phasetranscrystal.fpsmatch.common.client.minimap.editor.LocalEditorSessionGateway;
import com.phasetranscrystal.fpsmatch.common.client.minimap.editor.MinimapEditorController;
import com.phasetranscrystal.fpsmatch.common.client.net.FPSMClientPacketRegistrar;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.Objects;

/**
 * LDLib2 presentation shell for the minimap editor controller.
 * Widgets bind by stable catalog ids; editor logic stays in {@link MinimapEditorController}.
 */
public final class Ldlib2MinimapEditorScreen extends ModularUIScreen {
    private final MinimapEditorController controller;
    private final LocalEditorSessionGateway gateway;
    private final Screen parent;
    private final MapKey mapKey;
    private final Label statusLabel;
    private final MinimapEditorLdlibAdapter adapter;
    private boolean closed;
    private boolean pendingDirtyClose;
    private String statusOverride;

    public Ldlib2MinimapEditorScreen(
            MinimapEditorController controller,
            LocalEditorSessionGateway gateway,
            Screen parent,
            MapKey mapKey
    ) {
        this(build(controller), controller, gateway, parent, mapKey);
    }

    private Ldlib2MinimapEditorScreen(
            Parts parts,
            MinimapEditorController controller,
            LocalEditorSessionGateway gateway,
            Screen parent,
            MapKey mapKey
    ) {
        super(parts.ui(), Component.translatable("gui.fpsm.minimap.editor.title"));
        this.controller = Objects.requireNonNull(controller, "controller");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.parent = Objects.requireNonNull(parent, "parent");
        this.mapKey = Objects.requireNonNull(mapKey, "mapKey");
        this.statusLabel = parts.statusLabel();
        this.adapter = new MinimapEditorLdlibAdapter(controller);
        parts.saveTrigger().bind(this::saveDraft);
        parts.publishTrigger().bind(this::publish);
        parts.closeTrigger().bind(this::requestClose);
        parts.undoTrigger().bind(controller::undo);
        parts.redoTrigger().bind(controller::redo);
        parts.toolTrigger().bind(controller::selectTool);
        gateway.setPublishCompletionListener(this::onPublishCompleted);
        bindRequiredWidgets();
    }

    @Override
    public void init() {
        super.init();
        refreshStatus();
    }

    @Override
    public void tick() {
        super.tick();
        refreshStatus();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshStatus();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        requestClose();
    }

    @Override
    public void removed() {
        if (!closed) {
            // Fail closed: never auto-save/publish; discard remote session lease if still open.
            if (!controller.isClosed()) {
                controller.chooseClose(CloseChoice.DISCARD);
            }
            gateway.requestClose(WireEditor.CloseMode.DISCARD_DRAFT);
            detachListener();
            closed = true;
        }
        super.removed();
    }

    private void saveDraft() {
        try {
            controller.saveDraft();
            pendingDirtyClose = false;
            statusOverride = null;
        } catch (RuntimeException exception) {
            // Status bar reflects ERROR via refreshStatus.
        }
        refreshStatus();
    }

    private void publish() {
        try {
            statusOverride = null;
            controller.publish();
            if (controller.status() == EditorStatus.PUBLISHING) {
                statusOverride = Component.translatable(
                        "gui.fpsm.minimap.editor.publish.pending"
                ).getString();
            } else if (controller.status() == EditorStatus.READY) {
                statusOverride = Component.translatable(
                        "gui.fpsm.minimap.editor.publish.local_ok"
                ).getString();
            }
        } catch (RuntimeException exception) {
            statusOverride = Component.translatable(
                    "gui.fpsm.minimap.editor.publish.aborted",
                    exception.getMessage() == null ? "error" : exception.getMessage()
            ).getString();
        }
        refreshStatus();
    }

    private void onPublishCompleted(boolean committed, long publishRevision, String detail) {
        controller.completePublish(committed, detail);
        if (committed) {
            statusOverride = Component.translatable(
                    "gui.fpsm.minimap.editor.publish.committed",
                    publishRevision
            ).getString();
        } else {
            statusOverride = Component.translatable(
                    "gui.fpsm.minimap.editor.publish.aborted",
                    detail == null || detail.isBlank() ? "aborted" : detail
            ).getString();
        }
        refreshStatus();
    }

    private void requestClose() {
        if (closed || controller.isClosed()) {
            leaveToParent();
            return;
        }
        CloseDecision decision = controller.requestClose();
        if (decision == CloseDecision.CLOSED) {
            gateway.requestClose(WireEditor.CloseMode.KEEP_DRAFT);
            leaveToParent();
            return;
        }
        // Never swap to another Screen for the dirty prompt: ModularUI removed() would discard state.
        if (!pendingDirtyClose) {
            pendingDirtyClose = true;
            statusOverride = Component.translatable(
                    "gui.fpsm.minimap.editor.unsaved.message"
            ).getString();
            refreshStatus();
            return;
        }
        controller.chooseClose(CloseChoice.DISCARD);
        gateway.requestClose(WireEditor.CloseMode.DISCARD_DRAFT);
        leaveToParent();
    }

    private void leaveToParent() {
        detachListener();
        closed = true;
        Minecraft.getInstance().setScreen(parent);
    }

    private void detachListener() {
        gateway.clearPublishCompletionListener();
        var services = FPSMClientPacketRegistrar.minimapServices();
        if (services != null) {
            services.clearEditorSessionListener();
            services.clearPublishResultListener();
        }
    }

    private void refreshStatus() {
        if (statusOverride != null) {
            statusLabel.setText(Component.literal(statusOverride));
            return;
        }
        EditorStatus status = controller.status();
        String sessionHint = gateway.isServerSessionReady()
                ? Component.translatable("gui.fpsm.minimap.editor.status.server_ready").getString()
                : Component.translatable("gui.fpsm.minimap.editor.status.local").getString();
        Component text = Component.translatable(
                "gui.fpsm.minimap.editor.status.line",
                mapKey.gameType(),
                mapKey.mapName(),
                status.name(),
                controller.isDirty()
                        ? Component.translatable("gui.fpsm.minimap.editor.status.dirty").getString()
                        : Component.translatable("gui.fpsm.minimap.editor.status.clean").getString(),
                sessionHint
        );
        statusLabel.setText(text);
    }

    private void bindRequiredWidgets() {
        for (String id : adapter.catalog().ids()) {
            if (modularUI.getElementsById(id).size() != 1) {
                throw new IllegalStateException(
                        "Missing or duplicate editor widget binding: " + id
                );
            }
        }
    }

    private static Parts build(MinimapEditorController controller) {
        Objects.requireNonNull(controller, "controller");

        UIElement root = new UIElement();
        root.layout(layout -> layout.widthPercent(100).heightPercent(100));

        UIElement toolbar = new UIElement().setId(EditorLdlibWidgetCatalog.TOOLBAR);
        toolbar.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(0).top(0).right(0).height(40));

        UIElement canvas = new UIElement().setId(EditorLdlibWidgetCatalog.CANVAS);
        canvas.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(8).top(48).right(220).bottom(68));

        UIElement layerPanel = new UIElement().setId(EditorLdlibWidgetCatalog.LAYER_PANEL);
        layerPanel.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .width(200).top(48).right(8).heightPercent(35));

        UIElement properties = new UIElement().setId(EditorLdlibWidgetCatalog.PROPERTIES);
        properties.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .width(200).bottom(68).right(8).heightPercent(35));

        UIElement floorStrip = new UIElement().setId(EditorLdlibWidgetCatalog.FLOOR_STRIP);
        floorStrip.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(8).right(220).bottom(36).height(28));

        UIElement statusBar = new UIElement().setId(EditorLdlibWidgetCatalog.STATUS_BAR);
        statusBar.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(0).right(0).bottom(0).height(28));

        Label canvasHint = label(Component.translatable("gui.fpsm.minimap.editor.canvas.hint"));
        canvasHint.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(12).top(12).right(12).height(20));
        canvas.addChildren(canvasHint);

        Label layerTitle = label(Component.translatable("gui.fpsm.minimap.editor.layers"));
        layerTitle.layout(layout -> layout.widthPercent(100).height(18));
        Label layerBody = label(Component.translatable("gui.fpsm.minimap.editor.layers.empty"));
        layerBody.layout(layout -> layout.widthPercent(100).height(18));
        layerPanel.addChildren(layerTitle, layerBody);

        Label propsTitle = label(Component.translatable("gui.fpsm.minimap.editor.properties"));
        propsTitle.layout(layout -> layout.widthPercent(100).height(18));
        Label propsBody = label(Component.translatable("gui.fpsm.minimap.editor.properties.empty"));
        propsBody.layout(layout -> layout.widthPercent(100).height(18));
        properties.addChildren(propsTitle, propsBody);

        Label floorTitle = label(Component.translatable("gui.fpsm.minimap.editor.floor", "ground"));
        floorTitle.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(4).top(4).right(4).height(18));
        floorStrip.addChildren(floorTitle);

        Label statusLabel = label(Component.literal(""));
        statusLabel.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(8).top(6).right(8).height(16));
        statusBar.addChildren(statusLabel);

        SaveTrigger saveTrigger = new SaveTrigger();
        PublishTrigger publishTrigger = new PublishTrigger();
        CloseTrigger closeTrigger = new CloseTrigger();
        UndoTrigger undoTrigger = new UndoTrigger();
        RedoTrigger redoTrigger = new RedoTrigger();
        ToolTrigger toolTrigger = new ToolTrigger();

        int x = 8;
        toolbar.addChildren(toolButton(x, "gui.fpsm.minimap.editor.tool.pan",
                () -> toolTrigger.select(EditorTool.PAN)));
        x += 72;
        toolbar.addChildren(toolButton(x, "gui.fpsm.minimap.editor.tool.brush",
                () -> toolTrigger.select(EditorTool.BRUSH)));
        x += 72;
        toolbar.addChildren(toolButton(x, "gui.fpsm.minimap.editor.tool.eraser",
                () -> toolTrigger.select(EditorTool.ERASER)));
        x += 72;
        toolbar.addChildren(toolButton(x, "gui.fpsm.minimap.editor.tool.selection",
                () -> toolTrigger.select(EditorTool.SELECTION)));
        x += 84;
        toolbar.addChildren(actionButton(x, "gui.fpsm.minimap.editor.undo", undoTrigger::run));
        x += 72;
        toolbar.addChildren(actionButton(x, "gui.fpsm.minimap.editor.redo", redoTrigger::run));
        x += 72;
        toolbar.addChildren(actionButton(x, "gui.fpsm.minimap.editor.save", saveTrigger::run));
        x += 84;
        toolbar.addChildren(actionButton(x, "gui.fpsm.minimap.editor.publish", publishTrigger::run));
        x += 84;
        toolbar.addChildren(actionButton(x, "gui.fpsm.minimap.editor.close", closeTrigger::run));

        root.addChildren(toolbar, canvas, layerPanel, properties, floorStrip, statusBar);
        return new Parts(
                ModularUI.of(UI.of(root)),
                statusLabel,
                saveTrigger,
                publishTrigger,
                closeTrigger,
                undoTrigger,
                redoTrigger,
                toolTrigger
        );
    }

    private static Button toolButton(int left, String key, Runnable action) {
        Button button = new Button();
        button.setText(Component.translatable(key));
        button.setOnClick(event -> action.run());
        button.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(left).top(8).width(68).height(24));
        return button;
    }

    private static Button actionButton(int left, String key, Runnable action) {
        Button button = new Button();
        button.setText(Component.translatable(key));
        button.setOnClick(event -> action.run());
        button.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(left).top(8).width(80).height(24));
        return button;
    }

    private static Label label(Component text) {
        Label label = new Label();
        label.setText(text);
        return label;
    }

    private record Parts(
            ModularUI ui,
            Label statusLabel,
            SaveTrigger saveTrigger,
            PublishTrigger publishTrigger,
            CloseTrigger closeTrigger,
            UndoTrigger undoTrigger,
            RedoTrigger redoTrigger,
            ToolTrigger toolTrigger
    ) {
    }

    private static final class SaveTrigger {
        private Runnable action = () -> {
        };

        void bind(Runnable action) {
            this.action = Objects.requireNonNull(action, "action");
        }

        void run() {
            action.run();
        }
    }

    private static final class PublishTrigger {
        private Runnable action = () -> {
        };

        void bind(Runnable action) {
            this.action = Objects.requireNonNull(action, "action");
        }

        void run() {
            action.run();
        }
    }

    private static final class CloseTrigger {
        private Runnable action = () -> {
        };

        void bind(Runnable action) {
            this.action = Objects.requireNonNull(action, "action");
        }

        void run() {
            action.run();
        }
    }

    private static final class UndoTrigger {
        private Runnable action = () -> {
        };

        void bind(Runnable action) {
            this.action = Objects.requireNonNull(action, "action");
        }

        void run() {
            action.run();
        }
    }

    private static final class RedoTrigger {
        private Runnable action = () -> {
        };

        void bind(Runnable action) {
            this.action = Objects.requireNonNull(action, "action");
        }

        void run() {
            action.run();
        }
    }

    private static final class ToolTrigger {
        private java.util.function.Consumer<EditorTool> action = tool -> {
        };

        void bind(java.util.function.Consumer<EditorTool> action) {
            this.action = Objects.requireNonNull(action, "action");
        }

        void select(EditorTool tool) {
            action.accept(tool);
        }
    }
}
