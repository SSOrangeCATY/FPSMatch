package com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2.editor;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.minimap.editor.CloseChoice;
import com.ptcrys.fpsmatch.common.client.minimap.editor.CloseDecision;
import com.ptcrys.fpsmatch.common.client.minimap.editor.ClientEditorBinding;
import com.ptcrys.fpsmatch.common.client.minimap.editor.EditorResumeCheckpointRegistry;
import com.ptcrys.fpsmatch.common.client.minimap.editor.EditorStatus;
import com.ptcrys.fpsmatch.common.client.minimap.editor.EditorShortcut;
import com.ptcrys.fpsmatch.common.client.minimap.editor.EditorTool;
import com.ptcrys.fpsmatch.common.client.minimap.editor.LocalEditorSessionGateway;
import com.ptcrys.fpsmatch.common.client.minimap.editor.MinimapEditorController;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleButton;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleModularUIScreen;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.Ldlib2RenderGuard;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.Ldlib2AccessibilityController;
import com.ptcrys.fpsmatch.common.client.screen.mapselect.ldlib2.Ldlib2MapManageScreen;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.wire.WireEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ConcurrentModificationException;
import java.util.Locale;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * LDLib2 presentation shell for the minimap editor controller.
 * Widgets bind by stable catalog ids; editor logic stays in {@link MinimapEditorController}.
 */
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_EQUAL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_HOME;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ADD;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_SUBTRACT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_MINUS;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_P;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Y;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Z;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_ALT;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL;

public final class Ldlib2MinimapEditorScreen extends AccessibleModularUIScreen {
    private static final long CLOSE_DRAIN_TIMEOUT_SECONDS = 10L;
    private final MinimapEditorController controller;
    private final LocalEditorSessionGateway gateway;
    private final ClientEditorBinding binding;
    private final Screen parent;
    private final MapKey mapKey;
    private final boolean acceptanceOnly;
    private final MinimapEditorScreens.AcceptanceTeardown acceptanceTeardown;
    private final BooleanSupplier acceptancePublish;
    private final Label statusLabel;
    private final Label layerBody;
    private final Label propertiesBody;
    private final Label floorTitle;
    private final MinimapEditorLdlibAdapter adapter;
    private final UIElement toolbar;
    private final Ldlib2EditorCanvasElement canvas;
    private final UIElement layerPanel;
    private final UIElement properties;
    private final UIElement floorStrip;
    private final UIElement statusBar;
    private final List<AccessibleButton> toolbarButtons;
    private final Map<EditorTool, AccessibleButton> toolButtons;
    private final Map<AccessibleButton, com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme.ButtonKind> buttonKinds;
    private final AccessibleButton panButton;
    private final AccessibleButton panelToggle;
    private final AccessibleButton saveButton;
    private final AccessibleButton publishButton;
    private final AccessibleButton closeButton;
    private final AccessibleButton saveCloseButton;
    private final AccessibleButton discardCloseButton;
    private final AccessibleButton cancelCloseButton;
    private boolean closed;
    private boolean pendingDirtyClose;
    private boolean resumeAttempt;
    private boolean panelsOpen;
    private boolean lastBusy;
    private int appliedLayoutWidth = -1;
    private int appliedLayoutHeight = -1;
    private int appliedStatusTone = Integer.MIN_VALUE;
    private MinimapEditorUiView.StatusNotice statusNotice;
    private MinimapEditorUiView.StatusIdentity lastAnnouncedStatus;

    public Ldlib2MinimapEditorScreen(
            MinimapEditorController controller,
            LocalEditorSessionGateway gateway,
            ClientEditorBinding binding,
            Screen parent,
            MapKey mapKey
    ) {
        this(MinimapEditorUiView.build(controller), controller, gateway, binding, parent, mapKey,
                false, null, null);
    }

    Ldlib2MinimapEditorScreen(
            MinimapEditorController controller,
            LocalEditorSessionGateway gateway,
            ClientEditorBinding binding,
            Screen parent,
            MapKey mapKey,
            boolean acceptanceOnly,
            MinimapEditorScreens.AcceptanceTeardown acceptanceTeardown,
            BooleanSupplier acceptancePublish
    ) {
        this(MinimapEditorUiView.build(controller), controller, gateway, binding, parent, mapKey,
                acceptanceOnly, acceptanceTeardown, acceptancePublish);
    }

    private Ldlib2MinimapEditorScreen(
            MinimapEditorUiView.Parts parts,
            MinimapEditorController controller,
            LocalEditorSessionGateway gateway,
            ClientEditorBinding binding,
            Screen parent,
            MapKey mapKey,
            boolean acceptanceOnly,
            MinimapEditorScreens.AcceptanceTeardown acceptanceTeardown,
            BooleanSupplier acceptancePublish
    ) {
        super(parts.ui(), Component.translatable("gui.fpsm.minimap.editor.title"));
        this.controller = Objects.requireNonNull(controller, "controller");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.binding = Objects.requireNonNull(binding, "binding");
        this.parent = parent;
        this.mapKey = Objects.requireNonNull(mapKey, "mapKey");
        this.acceptanceOnly = acceptanceOnly;
        this.acceptanceTeardown = acceptanceOnly
                ? Objects.requireNonNull(acceptanceTeardown, "acceptanceTeardown")
                : null;
        this.acceptancePublish = acceptanceOnly
                ? Objects.requireNonNull(acceptancePublish, "acceptancePublish")
                : null;
        this.statusLabel = parts.statusLabel();
        this.layerBody = parts.layerBody();
        this.propertiesBody = parts.propertiesBody();
        this.floorTitle = parts.floorTitle();
        this.toolbar = parts.toolbar();
        this.canvas = parts.canvas();
        this.layerPanel = parts.layerPanel();
        this.properties = parts.properties();
        this.floorStrip = parts.floorStrip();
        this.statusBar = parts.statusBar();
        this.toolbarButtons = parts.toolbarButtons();
        this.toolButtons = parts.toolButtons();
        this.buttonKinds = parts.buttonKinds();
        this.panButton = parts.panButton();
        this.panelToggle = parts.panelToggle();
        this.saveButton = parts.saveButton();
        this.publishButton = parts.publishButton();
        this.closeButton = parts.closeButton();
        this.saveCloseButton = parts.saveCloseButton();
        this.discardCloseButton = parts.discardCloseButton();
        this.cancelCloseButton = parts.cancelCloseButton();
        this.adapter = new MinimapEditorLdlibAdapter(controller);
        parts.canvas().setErrorListener(this::onCanvasError);
        parts.canvas().setEditListener(() -> {
            statusNotice = null;
            refreshStatus();
        });
        parts.saveTrigger().bind(this::saveDraft);
        parts.publishTrigger().bind(this::publish);
        parts.closeTrigger().bind(this::requestClose);
        parts.undoTrigger().bind(controller::undo);
        parts.redoTrigger().bind(controller::redo);
        parts.toolTrigger().bind(controller::selectTool);
        panelToggle.setOnClick(event -> togglePanels());
        panelToggle.setAccessibleState(() -> Component.translatable(
                panelsOpen
                        ? "gui.fpsm.minimap.editor.panels.close"
                        : "gui.fpsm.minimap.editor.panels"
        ));
        saveCloseButton.setOnClick(event -> saveAndClose());
        discardCloseButton.setOnClick(event -> discardAndClose());
        cancelCloseButton.setOnClick(event -> cancelDirtyClose());
        gateway.setPublishCompletionListener(this::onPublishCompleted);
        registerFocusGroup(this::focusTargets);
    }

    @Override
    public void init() {
        super.init();
        // LDLib2 registers element ids during setScreenAndInit, after construction.
        bindRequiredWidgets();
        applyResponsiveLayout();
        refreshStatus();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        applyResponsiveLayout();
    }

    @Override
    public void tick() {
        super.tick();
        if (resumeAttempt) {
            if (gateway.isServerSessionReady()
                    || gateway.state() == LocalEditorSessionGateway.TransportState.ERROR) {
                EditorResumeCheckpointRegistry.global().discard(
                        controller.actorId(), mapKey
                );
                resumeAttempt = false;
            }
        }
        refreshStatus();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ensureResponsiveLayout();
        refreshStatus();
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW_KEY_ESCAPE) {
            if (panelsOpen && adapter.layoutModel(width, height).compact()) {
                setPanelsOpen(false);
            } else {
                requestClose();
            }
            return true;
        }
        if ((modifiers & GLFW_MOD_CONTROL) != 0) {
            if (editorBusy()) {
                // Match the disabled toolbar: keyboard shortcuts must not mutate
                // a draft while an async save/publish/rebase is in flight.
                statusNotice = null;
                refreshStatus();
                return true;
            }
            try {
                if (keyCode == GLFW_KEY_Z) {
                    return controller.handleShortcut(EditorShortcut.UNDO);
                }
                if (keyCode == GLFW_KEY_Y) {
                    return controller.handleShortcut(EditorShortcut.REDO);
                }
                if (keyCode == GLFW_KEY_S) {
                    return controller.handleShortcut(EditorShortcut.SAVE_DRAFT);
                }
                if (keyCode == GLFW_KEY_P) {
                    return controller.handleShortcut(
                            EditorShortcut.PUBLISH, this::publish
                    );
                }
            } catch (RuntimeException exception) {
                statusNotice = new MinimapEditorUiView.StatusNotice(
                        Component.translatable("gui.fpsm.minimap.editor.shortcut.error"),
                        com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme.DANGER,
                        "shortcut.exception"
                );
                refreshStatus();
                return true;
            }
        }
        if (hasFocusedNonCanvasControl()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (isCanvasKey(keyCode) && (modifiers & (GLFW_MOD_CONTROL | GLFW_MOD_ALT)) == 0) {
            try {
                if (canvas.handleKeyboardKey(keyCode)) {
                    return true;
                }
            } catch (RuntimeException failure) {
                onCanvasError(failure.getMessage());
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static boolean isCanvasKey(int keyCode) {
        return switch (keyCode) {
            case GLFW_KEY_LEFT, GLFW_KEY_RIGHT, GLFW_KEY_UP, GLFW_KEY_DOWN,
                    GLFW_KEY_A, GLFW_KEY_D, GLFW_KEY_W, GLFW_KEY_S,
                    GLFW_KEY_EQUAL, GLFW_KEY_KP_ADD, GLFW_KEY_MINUS,
                    GLFW_KEY_KP_SUBTRACT, GLFW_KEY_HOME -> true;
            default -> false;
        };
    }

    private boolean hasFocusedNonCanvasControl() {
        UIElement focused = modularUI.getFocusedElement();
        return focused != null && focused != canvas && focused.isFocusable();
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
            MinimapEditorScreens.invalidatePresentationGeneration();
            if (!acceptanceOnly
                    && (gateway.state() == LocalEditorSessionGateway.TransportState.DETACHED
                    || gateway.state() == LocalEditorSessionGateway.TransportState.RESUMING)) {
                gateway.clearPublishCompletionListener();
                binding.presentationRemovedForTransport();
                closed = true;
                super.removed();
                return;
            }
            if (acceptanceOnly) {
                acceptanceTeardown.run();
            } else {
                rememberDraft();
                // Screen replacement and disconnect are not user consent to destroy a draft.
                gateway.requestClose(WireEditor.CloseMode.KEEP_DRAFT);
                detachBinding();
                controller.close();
            }
            closed = true;
        }
        super.removed();
    }

    private void saveDraft() {
        try {
            controller.saveDraft();
            pendingDirtyClose = false;
            statusNotice = null;
        } catch (RuntimeException exception) {
            // Status bar reflects ERROR via refreshStatus.
        }
        refreshStatus();
    }

    private void publish() {
        try {
            statusNotice = null;
            boolean started = acceptancePublish == null
                    || acceptancePublish.getAsBoolean();
            if (acceptancePublish == null) {
                controller.publish();
            } else if (!started) {
                statusNotice = new MinimapEditorUiView.StatusNotice(
                        Component.translatable(
                                "gui.fpsm.minimap.editor.error.publish", "not_started"
                        ),
                        com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme.DANGER,
                        "publish.not_started"
                );
                refreshStatus();
                return;
            }
            if (controller.status() == EditorStatus.PUBLISHING) {
                statusNotice = new MinimapEditorUiView.StatusNotice(
                        Component.translatable("gui.fpsm.minimap.editor.publish.pending"),
                        com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme.ACCENT,
                        "publish.pending"
                );
            } else if (controller.status() == EditorStatus.READY) {
                statusNotice = new MinimapEditorUiView.StatusNotice(
                        Component.translatable("gui.fpsm.minimap.editor.publish.local_ok"),
                        com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme.SUCCESS,
                        "publish.local_ok"
                );
            }
        } catch (RuntimeException exception) {
            statusNotice = new MinimapEditorUiView.StatusNotice(
                    Component.translatable("gui.fpsm.minimap.editor.error.publish", "exception"),
                    com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme.DANGER,
                    "publish.exception"
            );
        }
        refreshStatus();
    }

    private void onPublishCompleted(boolean committed, long publishRevision, String diagnostic) {
        if (committed) {
            finishCommittedPublish(publishRevision);
            return;
        }
        controller.completePublish(false, diagnostic);
        statusNotice = new MinimapEditorUiView.StatusNotice(
                Component.translatable(
                        "gui.fpsm.minimap.editor.error.publish", "aborted"
                ),
                com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme.DANGER,
                "publish.aborted"
        );
        refreshStatus();
    }

    private void finishCommittedPublish(long publishRevision) {
        controller.completePublish(true, null);
        controller.close();
        EditorResumeCheckpointRegistry.global().discard(controller.actorId(), mapKey);
        if (parent instanceof Ldlib2MapManageScreen manageScreen) {
            manageScreen.awaitMinimapRefresh(
                    mapKey,
                    gateway.context().binding().documentId(),
                    publishRevision
            );
        }
        leaveToParent();
        requestAuthoritativeDetail();
    }

    private void requestAuthoritativeDetail() {
        if (parent instanceof Ldlib2MapManageScreen manageScreen) {
            manageScreen.requestMinimapRefresh();
            return;
        }
        FPSMatch.sendToServer(new MapRoomActionC2SPacket(
                MapRoomActionC2SPacket.Action.REQUEST_DETAIL,
                mapKey.gameType(),
                mapKey.mapName(),
                new UUID(0L, 0L)
        ));
    }

    private void onCanvasError(String diagnostic) {
        statusNotice = new MinimapEditorUiView.StatusNotice(
                Component.translatable(
                        "gui.fpsm.minimap.editor.error.canvas", "operation"
                ),
                com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme.DANGER,
                "canvas.operation"
        );
        refreshStatus();
    }

    private void requestClose() {
        if (acceptanceOnly) {
            closeAcceptance();
            return;
        }
        if (closed || controller.isClosed()) {
            leaveToParent();
            return;
        }
        if (!controller.isDirty()) {
            rememberDraft();
        }
        CloseDecision decision = controller.requestClose();
        if (decision == CloseDecision.CLOSED) {
            gateway.requestClose(WireEditor.CloseMode.KEEP_DRAFT);
            leaveToParent();
            return;
        }
        // Keep the editor mounted while the explicit save/discard/cancel actions are visible.
        if (!pendingDirtyClose) {
            pendingDirtyClose = true;
            statusNotice = null;
        }
        refreshStatus();
    }

    private void saveAndClose() {
        if (editorBusy()) {
            refreshStatus();
            return;
        }
        try {
            controller.chooseClose(CloseChoice.SAVE_DRAFT);
            pendingDirtyClose = false;
            gateway.requestClose(WireEditor.CloseMode.KEEP_DRAFT);
            leaveToParent();
        } catch (RuntimeException exception) {
            statusNotice = new MinimapEditorUiView.StatusNotice(
                    Component.translatable("gui.fpsm.minimap.editor.save.error"),
                    com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme.DANGER,
                    "save.exception"
            );
            refreshStatus();
        }
    }

    private void discardAndClose() {
        controller.chooseClose(CloseChoice.DISCARD);
        pendingDirtyClose = false;
        EditorResumeCheckpointRegistry.global().discard(controller.actorId(), mapKey);
        gateway.requestClose(WireEditor.CloseMode.DISCARD_DRAFT);
        leaveToParent();
    }

    private void cancelDirtyClose() {
        controller.chooseClose(CloseChoice.CANCEL);
        pendingDirtyClose = false;
        statusNotice = null;
        refreshStatus();
    }

    private void togglePanels() {
        if (!adapter.layoutModel(width, height).compact()) {
            return;
        }
        setPanelsOpen(!panelsOpen);
    }

    private void setPanelsOpen(boolean open) {
        if (panelsOpen == open) {
            return;
        }
        panelsOpen = open;
        applyResponsiveLayout();
    }

    private void ensureResponsiveLayout() {
        if (appliedLayoutWidth != width || appliedLayoutHeight != height) {
            applyResponsiveLayout();
        }
    }

    private void applyResponsiveLayout() {
        EditorUiLayoutModel base = adapter == null
                ? EditorUiLayoutModel.responsive(Math.max(1, width), Math.max(1, height))
                : adapter.layoutModel(Math.max(1, width), Math.max(1, height));
        if (!base.compact()) {
            panelsOpen = true;
        }
        boolean compactDrawer = base.compact() && panelsOpen;
        int drawerWidth = compactDrawer
                ? Math.min(200, Math.max(132, Math.max(1, width) / 2))
                : base.layerPanel().width();
        int canvasWidth = compactDrawer
                ? Math.max(1, base.canvas().width() - drawerWidth)
                : base.canvas().width();
        EditorUiLayoutModel.Rect canvas = new EditorUiLayoutModel.Rect(
                base.canvas().x(), base.canvas().y(), canvasWidth, base.canvas().height());
        EditorUiLayoutModel.Rect layer = compactDrawer
                ? new EditorUiLayoutModel.Rect(
                        Math.max(0, width - drawerWidth), base.canvas().y(), drawerWidth,
                        Math.max(0, base.canvas().height() / 2))
                : base.layerPanel();
        EditorUiLayoutModel.Rect property = compactDrawer
                ? new EditorUiLayoutModel.Rect(
                        Math.max(0, width - drawerWidth),
                        base.canvas().y() + Math.max(0, base.canvas().height() / 2),
                        drawerWidth, Math.max(0, base.canvas().height()
                                - base.canvas().height() / 2))
                : base.properties();
        applyRect(toolbar, base.toolbar());
        applyRect(canvasElement(), canvas);
        applyRect(layerPanel, layer);
        applyRect(properties, property);
        applyRect(floorStrip, base.floorStrip());
        applyRect(statusBar, base.statusBar());
        setPanelAvailable(layerPanel, !base.compact() || panelsOpen);
        setPanelAvailable(properties, !base.compact() || panelsOpen);
        panelToggle.setDisplay(base.compact());
        panelToggle.setVisible(base.compact());
        panelToggle.setActive(base.compact());
        panelToggle.setAllowHitTest(base.compact());
        panelToggle.setText(Component.translatable(
                panelsOpen
                        ? "gui.fpsm.minimap.editor.panels.close"
                        : "gui.fpsm.minimap.editor.panels"
        ));
        layoutToolbar(base.toolbar());
        layoutCloseChoices(base.statusBar());
        appliedLayoutWidth = width;
        appliedLayoutHeight = height;
        accessibility().reconcileFocus();
    }

    private UIElement canvasElement() {
        return canvas;
    }

    private static void applyRect(UIElement element, EditorUiLayoutModel.Rect rect) {
        element.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(rect.x()).top(rect.y())
                .width(rect.width()).height(rect.height()));
    }

    private static void setPanelAvailable(UIElement panel, boolean available) {
        panel.setDisplay(available);
        panel.setVisible(available);
        panel.setActive(available);
        panel.setAllowHitTest(available);
    }

    private void layoutToolbar(EditorUiLayoutModel.Rect bounds) {
        int gap = 4;
        int columns = bounds.width() < 760 ? 5 : Math.max(1, toolbarButtons.size());
        int cellWidth = Math.max(1, (bounds.width() - gap * (columns + 1)) / columns);
        int rowHeight = 28;
        for (int index = 0; index < toolbarButtons.size(); index++) {
            AccessibleButton button = toolbarButtons.get(index);
            int column = index % columns;
            int row = index / columns;
            button.layout(layout -> layout
                    .positionType(YogaPositionType.ABSOLUTE)
                    .left(gap + column * (cellWidth + gap))
                    .top(4 + row * rowHeight)
                    .width(cellWidth)
                    .height(24));
        }
    }

    private void layoutCloseChoices(EditorUiLayoutModel.Rect bounds) {
        int buttonHeight = Math.min(20, Math.max(1, bounds.height() - 6));
        cancelCloseButton.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .right(4).top(3).width(68).height(buttonHeight));
        discardCloseButton.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .right(76).top(3).width(76).height(buttonHeight));
        saveCloseButton.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .right(156).top(3).width(76).height(buttonHeight));
        boolean visible = pendingDirtyClose;
        boolean busy = editorBusy();
        for (AccessibleButton button : List.of(saveCloseButton, discardCloseButton, cancelCloseButton)) {
            button.setDisplay(visible);
            button.setVisible(visible);
            MinimapEditorUiView.buttonState(
                    buttonKinds,
                    button,
                    visible && (button != saveCloseButton || !busy)
            );
        }
        statusLabel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(8).top(5).right(visible ? 244 : 8).height(16));
    }

    private List<Ldlib2AccessibilityController.FocusTarget> focusTargets() {
        List<Ldlib2AccessibilityController.FocusTarget> targets = new ArrayList<>();
        // Keep the compact drawer toggle first while making the document canvas
        // the first reachable work surface on wide layouts.
        if (adapter.layoutModel(width, height).compact()) {
            targets.add(panelToggle);
        }
        targets.add(canvas);
        toolbarButtons.stream()
                .filter(button -> button != panelToggle)
                .forEach(targets::add);
        targets.add(saveCloseButton);
        targets.add(discardCloseButton);
        targets.add(cancelCloseButton);
        return targets;
    }

    private void leaveToParent() {
        Minecraft minecraft = Minecraft.getInstance();
        if (closed && minecraft.screen != this) {
            return;
        }
        if (!closed) {
            closed = true;
            MinimapEditorScreens.invalidatePresentationGeneration();
            if (acceptanceOnly) {
                acceptanceTeardown.run();
            } else {
                detachBinding();
                MinimapEditorScreens.clearActiveGateway(gateway);
            }
        }
        Runnable restoreParent = () -> {
            if (minecraft.screen == this) {
                minecraft.setScreen(parent);
            }
        };
        if (minecraft.isSameThread()) {
            restoreParent.run();
        } else {
            minecraft.tell(restoreParent);
        }
    }

    private void detachBinding() {
        gateway.clearPublishCompletionListener();
        if (gateway.retainPendingCloseListener(binding::close)) {
            CompletableFuture.delayedExecutor(
                    CLOSE_DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS
            ).execute(() -> Minecraft.getInstance().execute(gateway::expirePendingClose));
            return;
        }
        binding.close();
    }

    boolean closeAcceptance() {
        if (closed) {
            if (acceptanceTeardown != null) {
                acceptanceTeardown.run();
            }
            return false;
        }
        if (acceptanceTeardown != null) {
            boolean first = acceptanceTeardown.run();
            leaveToParent();
            return first;
        }
        controller.close();
        EditorResumeCheckpointRegistry.global().discard(controller.actorId(), mapKey);
        gateway.requestClose(WireEditor.CloseMode.DISCARD_DRAFT);
        leaveToParent();
        return true;
    }

    boolean presents(LocalEditorSessionGateway candidate) {
        return gateway == candidate && !closed && !controller.isClosed();
    }

    void abandonFailedInstallation() {
        closed = true;
    }

    void markResumeAttempt() {
        resumeAttempt = true;
    }

    private void rememberDraft() {
        try {
            if (controller.isDirty() && gateway.isServerSessionReady()) {
                controller.saveDraft();
            }
        } catch (RuntimeException ignored) {
            // Capture the acknowledged model below; KEEP_DRAFT remains resumable.
        }
        // A command/upload sent during the save is not authoritative until its
        // ACK reaches the gateway. Never bind a checkpoint to the older root.
        if (gateway.hasPendingNetworkWork()) {
            return;
        }
        controller.captureDraftSource().ifPresent(bytes ->
                MinimapEditorScreens.rememberDraft(
                        controller.actorId(), gateway.context(), bytes
                ));
    }

    private void refreshStatus() {
        refreshProjection();
        boolean busy = editorBusy();
        canvas.setEditingEnabled(!busy);
        for (AccessibleButton button : toolbarButtons) {
            boolean enabled = !busy || button == panButton
                    || button == panelToggle || button == closeButton;
            MinimapEditorUiView.buttonState(buttonKinds, button, enabled);
        }
        EditorTool selectedTool = controller.toolState().tool();
        for (Map.Entry<EditorTool, AccessibleButton> entry : toolButtons.entrySet()) {
            boolean selected = entry.getKey() == selectedTool;
            AccessibleButton button = entry.getValue();
            boolean enabled = !busy || button == panButton;
            MinimapEditorUiView.toolButtonState(
                    buttonKinds, button, selected, enabled
            );
        }
        layoutCloseChoices(new EditorUiLayoutModel.Rect(0, 0, width, 26));
        if (busy != lastBusy) {
            lastBusy = busy;
            accessibility().reconcileFocus();
        }

        Optional<LocalEditorSessionGateway.GatewayError> acceptanceError = acceptanceOnly
                ? controller.acceptanceErrorObservation()
                : Optional.empty();
        Optional<LocalEditorSessionGateway.GatewayError> visibleError =
                acceptanceError.isPresent() ? acceptanceError : gateway.lastError();
        String acceptanceCode = visibleError
                .flatMap(LocalEditorSessionGateway.GatewayError::failedOpcode)
                .map(code -> "opcode-" + code)
                .orElse("unknown");
        EditorStatus status = controller.status();
        int statusTone = MinimapEditorUiView.resolveStatusTone(
                status,
                controller.isDirty(),
                pendingDirtyClose,
                busy,
                visibleError.isPresent(),
                statusNotice == null ? null : statusNotice.tone()
        );
        if (statusTone != appliedStatusTone) {
            MinimapEditorUiView.statusTone(statusBar, statusLabel, statusTone);
            appliedStatusTone = statusTone;
        }

        Component message;
        MinimapEditorUiView.StatusIdentity identity;
        boolean error;
        if (visibleError.isPresent()) {
            message = Component.translatable(
                    "gui.fpsm.minimap.editor.error.acceptance", acceptanceCode
            );
            identity = new MinimapEditorUiView.StatusIdentity(
                    acceptanceError.isPresent() ? "acceptance" : "gateway", acceptanceCode
            );
            error = true;
        } else if (pendingDirtyClose) {
            message = Component.translatable("gui.fpsm.minimap.editor.unsaved.message");
            identity = new MinimapEditorUiView.StatusIdentity("unsaved", "");
            error = false;
        } else if (statusNotice != null) {
            message = statusNotice.text();
            identity = new MinimapEditorUiView.StatusIdentity(
                    "notice", statusNotice.code()
            );
            error = statusNotice.tone() ==
                    com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme.DANGER;
        } else {
            message = defaultStatusText(status);
            identity = new MinimapEditorUiView.StatusIdentity(
                    status.name()
                            + (controller.isDirty() ? ":dirty" : ":clean")
                            + (gateway.isServerSessionReady() ? ":server" : ":local"),
                    status == EditorStatus.ERROR || status == EditorStatus.DETACHED
                            ? "status" : ""
            );
            error = status == EditorStatus.ERROR || status == EditorStatus.DETACHED;
        }
        statusLabel.setText(message);
        announceStatusTransition(identity, message, error);
    }

    private void announceStatusTransition(
            MinimapEditorUiView.StatusIdentity identity,
            Component message,
            boolean error
    ) {
        if (!Objects.equals(lastAnnouncedStatus, identity)) {
            lastAnnouncedStatus = identity;
            announce(message, error);
        }
    }

    private Component defaultStatusText(EditorStatus status) {
        String sessionHint = gateway.isServerSessionReady()
                ? Component.translatable("gui.fpsm.minimap.editor.status.server_ready").getString()
                : Component.translatable("gui.fpsm.minimap.editor.status.local").getString();
        return Component.translatable(
                "gui.fpsm.minimap.editor.status.line",
                mapKey.gameType(),
                mapKey.mapName(),
                Component.translatable(
                        "gui.fpsm.minimap.editor.status."
                                + (status == EditorStatus.DIRTY
                                ? "editing"
                                : status.name().toLowerCase(Locale.ROOT))
                ),
                controller.isDirty()
                        ? Component.translatable("gui.fpsm.minimap.editor.status.dirty").getString()
                        : Component.translatable("gui.fpsm.minimap.editor.status.clean").getString(),
                sessionHint
        );
    }

    private boolean editorBusy() {
        return switch (controller.status()) {
            case LOADING, SAVING, REBASING, RESUMING, DETACHED, PUBLISHING -> true;
            default -> gateway.hasPendingNetworkWork();
        };
    }

    private void refreshProjection() {
        String floorId = controller.selectedFloorId();
        if (floorId == null) {
            floorTitle.setText(Component.translatable(
                    "gui.fpsm.minimap.editor.floor", "-")
            );
            layerBody.setText(Component.translatable(
                    "gui.fpsm.minimap.editor.layers.empty"));
            propertiesBody.setText(Component.translatable(
                    "gui.fpsm.minimap.editor.properties.empty"));
            return;
        }
        var floor = controller.document().floor(floorId);
        floorTitle.setText(Component.translatable(
                "gui.fpsm.minimap.editor.floor", floor.label().value()));
        String layerId = controller.selectedLayerId();
        if (layerId == null) {
            layerBody.setText(Component.translatable(
                    "gui.fpsm.minimap.editor.layers.empty"));
            propertiesBody.setText(Component.translatable(
                    "gui.fpsm.minimap.editor.properties.empty"));
            return;
        }
        var layer = floor.layer(layerId);
        Component layerType = Component.translatable(
                "gui.fpsm.minimap.editor.layer.type."
                        + layer.type().name().toLowerCase(Locale.ROOT)
        );
        layerBody.setText(Component.translatable(
                "gui.fpsm.minimap.editor.layer.selected",
                layer.label().value(), layerType));
        Component visibility = Component.translatable(
                layer.visible()
                        ? "gui.fpsm.minimap.editor.layer.visible"
                        : "gui.fpsm.minimap.editor.layer.hidden"
        );
        Component lock = Component.translatable(
                layer.locked()
                        ? "gui.fpsm.minimap.editor.layer.locked"
                        : "gui.fpsm.minimap.editor.layer.unlocked"
        );
        propertiesBody.setText(Component.translatable(
                "gui.fpsm.minimap.editor.properties.selected",
                String.format(Locale.ROOT, "%.0f%%", layer.opacity() * 100.0),
                visibility,
                lock,
                layer.tiles().snapshot().size()));
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
}
