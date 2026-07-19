package com.phasetranscrystal.fpsmatch.common.client.minimap.editor;

import com.phasetranscrystal.fpsmatch.core.minimap.editor.command.DraftSnapshot;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.command.EditorCommand;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.command.EditorCommandException;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.command.EditorCommandLog;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.command.EditorOperation;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.command.EditorSessionGateway;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.command.PendingOperationQueue;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.command.RebaseResult;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Platform-neutral editor controller. LDLib2 widgets bind to this state and never leak into core packages.
 */
public final class MinimapEditorController implements AutoCloseable {
    private final UUID sessionId;
    private final UUID actorId;
    private final EditorDocument document;
    private final EditorCommandLog commandLog;
    private final EditorSessionGateway gateway;
    private final PendingOperationQueue pending = new PendingOperationQueue();
    private final EditorCanvasState canvasState = new EditorCanvasState();
    private final EditorToolState toolState = new EditorToolState();
    private final EditorTaskScheduler taskScheduler = new EditorTaskScheduler();

    private boolean serverAuthorized;
    private boolean textFieldFocused;
    private boolean closed;
    private boolean dirty;
    private long ackCursor;
    private EditorStatus status = EditorStatus.READY;
    private String selectedFloorId;
    private String selectedLayerId;
    private Sha256 lastKnownRootHash;

    private MinimapEditorController(
            UUID sessionId,
            UUID actorId,
            EditorDocument document,
            EditorCommandLog commandLog,
            EditorSessionGateway gateway,
            boolean serverAuthorized
    ) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.document = Objects.requireNonNull(document, "document");
        this.commandLog = Objects.requireNonNull(commandLog, "commandLog");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.serverAuthorized = serverAuthorized;
        this.lastKnownRootHash = commandLog.rootHash();
        this.selectedFloorId = document.floorIds().isEmpty() ? null : document.floorIds().get(0);
    }

    public static MinimapEditorController open(
            UUID sessionId,
            UUID actorId,
            EditorDocument document,
            EditorCommandLog commandLog,
            EditorSessionGateway gateway,
            boolean serverAuthorized
    ) {
        return new MinimapEditorController(
                sessionId, actorId, document, commandLog, gateway, serverAuthorized
        );
    }

    public EditorDocument document() {
        return document;
    }

    public EditorCanvasState canvasState() {
        return canvasState;
    }

    public EditorToolState toolState() {
        return toolState;
    }

    public EditorTaskScheduler taskScheduler() {
        return taskScheduler;
    }

    public EditorStatus status() {
        return status;
    }

    public boolean isDirty() {
        return dirty;
    }

    public boolean isClosed() {
        return closed;
    }

    public long ackCursor() {
        return ackCursor;
    }

    public void setServerAuthorized(boolean serverAuthorized) {
        this.serverAuthorized = serverAuthorized;
    }

    public void setTextFieldFocused(boolean textFieldFocused) {
        this.textFieldFocused = textFieldFocused;
    }

    public void selectTool(EditorTool tool) {
        ensureOpen();
        toolState.selectTool(tool);
    }

    public void setBrushSize(int size) {
        ensureOpen();
        toolState.setBrushSize(size);
    }

    public void setColorArgb(int colorArgb) {
        ensureOpen();
        toolState.setColorArgb(colorArgb);
    }

    public void setSelectionMode(SelectionMode mode) {
        ensureOpen();
        toolState.setSelectionMode(mode);
    }

    public void panBy(double dx, double dy) {
        ensureOpen();
        canvasState.panBy(dx, dy);
    }

    public void zoomBy(double delta) {
        ensureOpen();
        canvasState.zoomBy(delta);
    }

    public void selectLayer(String floorId, String layerId) {
        ensureOpen();
        document.layer(floorId, layerId);
        this.selectedFloorId = floorId;
        this.selectedLayerId = layerId;
    }

    public void setLayerOpacity(double opacity) {
        ensureOpen();
        requireSelectedLayer();
        document.setLayerOpacity(selectedFloorId, selectedLayerId, opacity);
        EditorCommand command = commandLog.append(
                EditorOperation.setOpacity(selectedLayerId, opacity)
        );
        dirty = true;
        status = EditorStatus.DIRTY;
        lastKnownRootHash = command.resultingRootHash();
    }

    public boolean canUndo() {
        return commandLog.canUndo();
    }

    public boolean canRedo() {
        return commandLog.canRedo();
    }

    public void undo() {
        ensureOpen();
        Optional<EditorCommand> undone = commandLog.undo();
        if (undone.isEmpty()) {
            return;
        }
        dirty = true;
        status = EditorStatus.DIRTY;
        lastKnownRootHash = commandLog.rootHash();
    }

    public void redo() {
        ensureOpen();
        Optional<EditorCommand> redone = commandLog.redo();
        if (redone.isEmpty()) {
            return;
        }
        dirty = true;
        status = EditorStatus.DIRTY;
        lastKnownRootHash = commandLog.rootHash();
    }

    public boolean handleShortcut(EditorShortcut shortcut) {
        ensureOpen();
        if (textFieldFocused) {
            return false;
        }
        Objects.requireNonNull(shortcut, "shortcut");
        switch (shortcut) {
            case UNDO -> undo();
            case REDO -> redo();
            case SAVE_DRAFT -> saveDraft();
            case PUBLISH -> publish();
            case CANCEL_TASK -> taskScheduler.cancelAll();
        }
        return true;
    }

    public EditorTaskHandle scheduleBackground(String taskId, Runnable work) {
        ensureOpen();
        return taskScheduler.schedule(taskId, work);
    }

    public void saveDraft() {
        ensureOpen();
        requireAuthorized();
        status = EditorStatus.SAVING;
        try {
            if (!dirty) {
                status = EditorStatus.READY;
                return;
            }
            EditorCommand command = requireLatestCommand();
            pending.enqueue(sessionId, command);
            DraftSnapshot snapshot = gateway.apply(sessionId, actorId, command, serverAuthorized);
            ackCursor = snapshot.ackCursor();
            lastKnownRootHash = snapshot.draftRootHash();
            dirty = false;
            status = EditorStatus.READY;
        } catch (RuntimeException exception) {
            status = EditorStatus.ERROR;
            throw exception;
        }
    }

    public void onReconnect() {
        ensureOpen();
        requireAuthorized();
        status = EditorStatus.REBASING;
        try {
            RebaseResult result = gateway.rebase(sessionId, actorId, commandLog.baseRootHash(), serverAuthorized);
            lastKnownRootHash = result.mergedRootHash();
            status = result.conflicts().isEmpty() ? (dirty ? EditorStatus.DIRTY : EditorStatus.READY) : EditorStatus.ERROR;
        } catch (RuntimeException exception) {
            status = EditorStatus.ERROR;
            throw exception;
        }
    }

    public void publish() {
        ensureOpen();
        requireAuthorized();
        status = EditorStatus.PUBLISHING;
        boolean hadDirty = dirty;
        try {
            if (dirty) {
                saveDraft();
            }
            gateway.publish(sessionId, actorId, lastKnownRootHash, serverAuthorized);
            dirty = false;
            // Server publish is async; keep PUBLISHING until completePublish().
            status = gateway.isPublishInFlight() ? EditorStatus.PUBLISHING : EditorStatus.READY;
        } catch (RuntimeException exception) {
            // Preserve author intent: failed publish leaves work pending.
            dirty = true;
            status = EditorStatus.ERROR;
            if (!hadDirty && ackCursor > 0) {
                dirty = true;
            }
            throw exception;
        }
    }

    /**
     * Completes an async server publish after {@code PublishResult} arrives.
     */
    public void completePublish(boolean committed, String detail) {
        if (closed) {
            return;
        }
        if (committed) {
            dirty = false;
            status = EditorStatus.READY;
        } else {
            dirty = true;
            status = EditorStatus.ERROR;
        }
    }

    public CloseDecision requestClose() {
        if (closed) {
            return CloseDecision.CLOSED;
        }
        // Never auto-save or publish on close.
        if (dirty) {
            return CloseDecision.NEED_CHOICE;
        }
        closeInternal();
        return CloseDecision.CLOSED;
    }

    public void chooseClose(CloseChoice choice) {
        Objects.requireNonNull(choice, "choice");
        if (closed) {
            return;
        }
        switch (choice) {
            case CANCEL -> {
                // keep open
            }
            case SAVE_DRAFT -> {
                saveDraft();
                closeInternal();
            }
            case DISCARD -> closeInternal();
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closeInternal();
        }
    }

    private void closeInternal() {
        taskScheduler.close();
        closed = true;
        status = EditorStatus.CLOSED;
    }

    private void ensureOpen() {
        if (closed) {
            throw new EditorCommandException("Editor is closed");
        }
    }

    private void requireAuthorized() {
        if (!serverAuthorized) {
            throw new EditorCommandException("Editor action rejected: not server-authorized");
        }
    }

    private void requireSelectedLayer() {
        if (selectedFloorId == null || selectedLayerId == null) {
            throw new EditorCommandException("No layer selected");
        }
    }

    private EditorCommand requireLatestCommand() {
        Optional<EditorCommand> undoneProbe = Optional.empty();
        // Walk log state: the current tip is the last applied-local operation.
        // EditorCommandLog does not expose history directly; re-derive via undo/redo tip.
        if (!commandLog.canUndo()) {
            throw new EditorCommandException("No local editor command available to save");
        }
        EditorCommand tip = commandLog.undo().orElseThrow();
        commandLog.redo();
        return tip;
    }
}