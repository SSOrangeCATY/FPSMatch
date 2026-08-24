package com.ptcrys.fpsmatch.common.client.minimap.editor;

import com.ptcrys.fpsmatch.core.minimap.editor.command.DraftSnapshot;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorCommand;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorCommandException;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorCommandLog;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorDocumentMutator;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorEdit;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorOperation;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorSessionGateway;
import com.ptcrys.fpsmatch.core.minimap.editor.command.PendingOperationQueue;
import com.ptcrys.fpsmatch.core.minimap.editor.command.PendingOperationAck;
import com.ptcrys.fpsmatch.core.minimap.editor.command.RebaseResult;
import com.ptcrys.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.ptcrys.fpsmatch.core.minimap.editor.document.EditorSourceCodec;
import com.ptcrys.fpsmatch.core.minimap.editor.document.EditorSourceSnapshot;
import com.ptcrys.fpsmatch.core.minimap.editor.document.EditableLayer;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapOpcode;
import com.ptcrys.fpsmatch.core.minimap.model.LayerType;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.util.Objects;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Platform-neutral editor controller. LDLib2 widgets bind to this state and never leak into core packages.
 */
public final class MinimapEditorController implements AutoCloseable {
    private final UUID sessionId;
    private UUID draftId;
    private final UUID actorId;
    private EditorDocument document;
    private EditorSourceSnapshot sourceSnapshot;
    private boolean preserveResumeSource;
    private EditorCommandLog commandLog;
    private final EditorSessionGateway gateway;
    private PendingOperationQueue pending = new PendingOperationQueue();
    private final EditorCanvasState canvasState = new EditorCanvasState();
    private final EditorToolState toolState = new EditorToolState();
    private final EditorTaskScheduler taskScheduler = new EditorTaskScheduler();
    private final EditorDocumentMutator documentMutator = new EditorDocumentMutator();

    private boolean serverAuthorized;
    private boolean textFieldFocused;
    private boolean closed;
    private boolean dirty;
    private long ackCursor;
    private EditorStatus status = EditorStatus.READY;
    private String selectedFloorId;
    private String selectedLayerId;
    private Sha256 lastKnownRootHash;
    private final AtomicReference<AcceptanceErrorObservationArm>
            acceptanceErrorObservation = new AtomicReference<>();

    private MinimapEditorController(
            UUID sessionId,
            UUID draftId,
            UUID actorId,
            EditorDocument document,
            EditorCommandLog commandLog,
            EditorSessionGateway gateway,
            boolean serverAuthorized
    ) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.draftId = Objects.requireNonNull(draftId, "draftId");
        if (sessionId.equals(draftId)) {
            throw new IllegalArgumentException("Editor draft ID must be distinct from the session ID");
        }
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.document = Objects.requireNonNull(document, "document");
        this.sourceSnapshot = null;
        this.preserveResumeSource = false;
        this.commandLog = Objects.requireNonNull(commandLog, "commandLog");
        if (!commandLog.outboundJournal().isEmpty()) {
            throw new IllegalArgumentException(
                    "Editor controllers require a fresh command log at the acknowledged draft root");
        }
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.serverAuthorized = serverAuthorized;
        this.lastKnownRootHash = commandLog.rootHash();
        this.selectedFloorId = document.floorIds().isEmpty() ? null : document.floorIds().get(0);
        selectFirstEditableLayerIfNeeded();
        if (gateway instanceof LocalEditorSessionGateway localGateway) {
            status = EditorStatus.LOADING;
            this.serverAuthorized = false;
            localGateway.setSessionListener(session -> {
                adoptAuthoritativeSession(
                        session.context().draftId(), session.context().ackCursor(),
                        session.context().draftRootHash());
                if (session.sourceAvailability() ==
                        com.ptcrys.fpsmatch.core.minimap.wire.WireEditor
                                .SourceAvailability.NONE) {
                    this.serverAuthorized = true;
                    status = EditorStatus.READY;
                } else if (this.preserveResumeSource && localGateway.isServerSessionReady()) {
                    this.serverAuthorized = true;
                    status = EditorStatus.READY;
                }
            });
            localGateway.setSourceReadyListener(this::hydrateSource);
            localGateway.setSnapshotListener(this::acceptGatewaySnapshot);
            localGateway.setRebaseListener((result, wire) -> completeRebase(result));
            localGateway.setTransportListener(new EditorTransportListener() {
                @Override
                public void detached() {
                    onTransportDetached();
                }

                @Override
                public void resuming() {
                    onTransportResuming();
                }

                @Override
                public void restored() {
                    onTransportRestored(localGateway);
                }
            });
            localGateway.setErrorListener(error -> {
                if (!closed) {
                    status = EditorStatus.ERROR;
                }
                observeAcceptanceError(localGateway, error);
            });
        }
    }

    public static MinimapEditorController open(
            UUID sessionId,
            UUID draftId,
            UUID actorId,
            EditorDocument document,
            EditorCommandLog commandLog,
            EditorSessionGateway gateway,
            boolean serverAuthorized
    ) {
        return new MinimapEditorController(
                sessionId, draftId, actorId, document, commandLog, gateway, serverAuthorized
        );
    }

    public void setInitialSource(EditorSourceSnapshot snapshot, boolean preserveOnResume) {
        if (closed) {
            throw new EditorCommandException("Editor is closed");
        }
        this.sourceSnapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.preserveResumeSource = preserveOnResume;
    }

    /** Encodes the acknowledged model; pending local edits are rolled back in the copy. */
    public Optional<byte[]> captureDraftSource() {
        if (closed || sourceSnapshot == null) {
            return Optional.empty();
        }
        try {
            EditorSourceSnapshot copy = EditorSourceCodec.decode(
                    sourceSnapshot.originalSourceBytes()
            );
            EditorDocument acknowledged = copy.document();
            for (EditorEdit edit : commandLog.currentState().edits()) {
                documentMutator.apply(acknowledged, edit);
            }
            List<EditorCommand> pendingCommands = pending.snapshot(draftId);
            for (int index = pendingCommands.size() - 1; index >= 0; index--) {
                documentMutator.apply(acknowledged, pendingCommands.get(index).edit().reversed());
            }
            return Optional.of(EditorSourceCodec.encode(
                    EditorSourceCodec.withDocument(copy, acknowledged),
                    copy.definition().manifest().revision()
            ));
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
    }

    /** Compatibility overload for non-network controller tests and older callers. */
    public static MinimapEditorController open(
            UUID sessionId,
            UUID actorId,
            EditorDocument document,
            EditorCommandLog commandLog,
            EditorSessionGateway gateway,
            boolean serverAuthorized
    ) {
        UUID draftId = UUID.randomUUID();
        if (draftId.equals(sessionId)) {
            draftId = UUID.randomUUID();
        }
        return open(
                sessionId, draftId, actorId, document, commandLog, gateway, serverAuthorized
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

    public UUID sessionId() {
        return sessionId;
    }

    public UUID actorId() {
        return actorId;
    }

    public UUID draftId() {
        return draftId;
    }

    public List<Long> pendingSequences() {
        return pending.pendingSequences(draftId);
    }

    public long ackCursor() {
        return ackCursor;
    }

    public String selectedFloorId() {
        return selectedFloorId;
    }

    public String selectedLayerId() {
        return selectedLayerId;
    }

    public void setServerAuthorized(boolean serverAuthorized) {
        this.serverAuthorized = serverAuthorized;
    }

    public void setTextFieldFocused(boolean textFieldFocused) {
        this.textFieldFocused = textFieldFocused;
    }

    /**
     * Arms the acceptance-only diagnostic without changing the ordinary gateway error lifecycle.
     * The gateway remains the authority for request/lease/binding correlation; this seam only
     * retains the first correlated REBASE error long enough for the acceptance projector to read it.
     */
    public boolean armAcceptanceErrorObservation() {
        if (closed || status != EditorStatus.READY
                || !(gateway instanceof LocalEditorSessionGateway localGateway)
                || localGateway.state() != LocalEditorSessionGateway.TransportState.READY
                || !localGateway.isServerSessionReady()) {
            return false;
        }
        WireIdentity.EditorContext context;
        try {
            context = localGateway.context();
        } catch (RuntimeException failure) {
            return false;
        }
        if (!validAcceptanceContext(context)) {
            return false;
        }
        clearAcceptanceErrorObservation();
        acceptanceErrorObservation.set(new AcceptanceErrorObservationArm(
                AcceptanceErrorObservationIdentity.from(context)
        ));
        return true;
    }

    /** Returns the first correlated acceptance error while its identity is still current. */
    public Optional<LocalEditorSessionGateway.GatewayError>
    acceptanceErrorObservation() {
        AcceptanceErrorObservationArm arm = acceptanceErrorObservation.get();
        if (arm == null || !arm.armed) {
            return Optional.empty();
        }
        if (!(gateway instanceof LocalEditorSessionGateway localGateway)) {
            disarmAcceptanceErrorObservation(arm);
            return Optional.empty();
        }
        try {
            if (!arm.identity.matches(localGateway.context())) {
                disarmAcceptanceErrorObservation(arm);
                return Optional.empty();
            }
        } catch (RuntimeException failure) {
            disarmAcceptanceErrorObservation(arm);
            return Optional.empty();
        }
        return Optional.ofNullable(arm.error.get());
    }

    /** Disarms before clearing so a late transport callback cannot publish a stale observation. */
    public boolean clearAcceptanceErrorObservation() {
        AcceptanceErrorObservationArm arm = acceptanceErrorObservation.get();
        if (arm == null) {
            return false;
        }
        disarmAcceptanceErrorObservation(arm);
        return true;
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

    /** Restores the neutral document viewport without changing the draft. */
    public void resetCanvasView() {
        ensureOpen();
        canvasState.setViewport(0.0, 0.0, 1.0);
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
        EditableLayer layer = document.layer(selectedFloorId, selectedLayerId);
        double before = layer.opacity();
        EditorEdit edit = new EditorEdit(
                java.util.List.of(EditorOperation.setOpacity(selectedFloorId, selectedLayerId, opacity)),
                java.util.List.of(EditorOperation.setOpacity(selectedFloorId, selectedLayerId, before)),
                java.util.Map.of()
        );
        applyLocalEdit(edit);
    }

    /** Applies one already-staged canvas gesture and records it in the outbound journal. */
    public void applyLocalEdit(EditorEdit edit) {
        ensureOpen();
        Objects.requireNonNull(edit, "edit");
        documentMutator.apply(document, edit);
        appendJournal(edit);
    }

    /** Records an edit whose document mutation was already committed by a canvas interactor. */
    public void recordAppliedEdit(EditorEdit edit) {
        ensureOpen();
        Objects.requireNonNull(edit, "edit");
        appendJournal(edit);
    }

    public boolean canUndo() {
        return commandLog.canUndo();
    }

    public boolean canRedo() {
        return commandLog.canRedo();
    }

    public void undo() {
        ensureOpen();
        Optional<EditorEdit> edit = commandLog.nextUndo();
        if (edit.isEmpty()) {
            return;
        }
        documentMutator.apply(document, edit.orElseThrow());
        EditorCommand command = commandLog.commitUndo();
        appendJournal(command);
    }

    public void redo() {
        ensureOpen();
        Optional<EditorEdit> edit = commandLog.nextRedo();
        if (edit.isEmpty()) {
            return;
        }
        documentMutator.apply(document, edit.orElseThrow());
        EditorCommand command = commandLog.commitRedo();
        appendJournal(command);
    }

    public boolean handleShortcut(EditorShortcut shortcut) {
        return handleShortcut(shortcut, this::publish);
    }

    public boolean handleShortcut(EditorShortcut shortcut, Runnable publishAction) {
        ensureOpen();
        if (textFieldFocused) {
            return false;
        }
        Objects.requireNonNull(shortcut, "shortcut");
        Objects.requireNonNull(publishAction, "publishAction");
        switch (shortcut) {
            case UNDO -> undo();
            case REDO -> redo();
            case SAVE_DRAFT -> saveDraft();
            case PUBLISH -> publishAction.run();
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
            if (!dirty && !pending.hasPending(draftId)) {
                status = EditorStatus.READY;
                return;
            }
            while (true) {
                java.util.List<EditorCommand> commands = pending.snapshot(draftId);
            if (commands.isEmpty()) {
                    break;
                }
                EditorCommand command = commands.get(0);
                pending.markSent(draftId, command.sequence());
                DraftSnapshot snapshot = Objects.requireNonNull(
                        gateway.apply(sessionId, actorId, command, serverAuthorized),
                        "Editor gateway returned no draft snapshot");
                validateSnapshot(snapshot, command);
                PendingOperationAck acknowledged = pending.acknowledge(
                        draftId, snapshot.ackCursor(), snapshot.draftRootHash());
                ackCursor = acknowledged.ackCursor();
                lastKnownRootHash = acknowledged.draftRootHash();
                if (snapshot.ackCursor() < command.sequence()) {
                    break;
                }
            }
            dirty = pending.hasPending(draftId);
            status = dirty ? EditorStatus.DIRTY : EditorStatus.READY;
        } catch (RuntimeException exception) {
            dirty = pending.hasPending(draftId) || dirty;
            status = EditorStatus.ERROR;
            throw exception;
        }
    }

    public void onReconnect() {
        ensureOpen();
        requireAuthorized();
        status = EditorStatus.REBASING;
        boolean asynchronous = gateway instanceof LocalEditorSessionGateway local
                && local.isServerSessionReady();
        try {
            RebaseResult result = gateway.rebase(sessionId, actorId, commandLog.baseRootHash(), serverAuthorized);
            if (!asynchronous) {
                completeRebase(result);
            }
        } catch (RuntimeException exception) {
            if (status == EditorStatus.REBASING) status = EditorStatus.ERROR;
            throw exception;
        }
    }

    public void publish() {
        ensureOpen();
        requireAuthorized();
        status = EditorStatus.PUBLISHING;
        boolean hadDirty = dirty;
        try {
            if (dirty || pending.hasPending(draftId)) {
                saveDraft();
            }
            if (pending.hasPending(draftId)) {
                throw new EditorCommandException(
                        "Publish is waiting for the server to acknowledge the draft");
            }
            gateway.publish(sessionId, actorId, lastKnownRootHash, serverAuthorized);
            dirty = gateway.isPublishInFlight();
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
        if (committed && !pending.hasPending(draftId)) {
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
        clearAcceptanceErrorObservation();
        taskScheduler.close();
        closed = true;
        status = EditorStatus.CLOSED;
    }

    private void observeAcceptanceError(
            LocalEditorSessionGateway localGateway,
            LocalEditorSessionGateway.GatewayError error
    ) {
        try {
            if (closed || error == null
                    || error.failedOpcode().orElse(-1)
                    != MinimapOpcode.C2S_EDITOR_REBASE.code()) {
                return;
            }
            AcceptanceErrorObservationArm arm = acceptanceErrorObservation.get();
            if (arm == null || !arm.armed
                    || !arm.identity.matches(localGateway.context())) {
                return;
            }
            // first-wins prevents duplicate or delayed callbacks from changing the diagnostic.
            arm.error.compareAndSet(null, error);
        } catch (RuntimeException ignored) {
            // Observation is diagnostic-only; it must never interrupt gateway recovery.
        }
    }

    private boolean validAcceptanceContext(WireIdentity.EditorContext context) {
        return context != null
                && context.lease().scope() == WireIdentity.Scope.EDITOR
                && context.sessionId() != null
                && draftId.equals(context.draftId())
                && context.baseRevision() >= 0L
                && context.baseSourceHash() != null;
    }

    private void disarmAcceptanceErrorObservation(AcceptanceErrorObservationArm arm) {
        arm.armed = false;
        acceptanceErrorObservation.compareAndSet(arm, null);
    }

    private void ensureOpen() {
        if (closed) {
            throw new EditorCommandException("Editor is closed");
        }
        if (status == EditorStatus.LOADING) {
            throw new EditorCommandException("Editor source is still loading");
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

    private void appendJournal(EditorEdit edit) {
        EditorCommand command = commandLog.append(edit);
        appendJournal(command);
    }

    private void appendJournal(EditorCommand command) {
        pending.enqueue(draftId, command);
        dirty = true;
        status = EditorStatus.DIRTY;
        lastKnownRootHash = command.resultingRootHash();
    }

    private void adoptAuthoritativeSession(UUID authoritativeDraftId, long authoritativeAck,
                                           Sha256 authoritativeRoot) {
        if (closed || authoritativeDraftId == null) {
            return;
        }
        UUID previous = draftId;
        if (!previous.equals(authoritativeDraftId)) {
            pending.rebind(previous, authoritativeDraftId);
            draftId = authoritativeDraftId;
        }
        if (!pending.hasPending(draftId)) {
            ackCursor = authoritativeAck;
            lastKnownRootHash = authoritativeRoot;
        }
    }

    private void hydrateSource(EditorSourceSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (preserveResumeSource && gateway instanceof LocalEditorSessionGateway local
                && local.context().ackCursor() == ackCursor
                && local.context().draftRootHash().equals(lastKnownRootHash)) {
            serverAuthorized = true;
            status = EditorStatus.READY;
            return;
        }
        if (closed || dirty || pending.hasPending(draftId)) {
            status = EditorStatus.ERROR;
            return;
        }
        Sha256 root = gateway instanceof LocalEditorSessionGateway local
                ? local.context().draftRootHash() : lastKnownRootHash;
        long authoritativeAck = gateway instanceof LocalEditorSessionGateway local
                ? local.context().ackCursor() : ackCursor;
        EditorDocument hydrated = snapshot.document();
        try {
            // Source transfer contains the published base. Reapply the clean
            // acknowledged history so a reconnect reconstructs the draft view
            // before moving the next command onto the server checkpoint.
            for (EditorEdit edit : commandLog.currentState().edits()) {
                documentMutator.apply(hydrated, edit);
            }
            PendingOperationQueue reanchoredPending = new PendingOperationQueue();
            reanchoredPending.reanchor(draftId, authoritativeAck, root);
            commandLog.reanchor(root, authoritativeAck);
            document = hydrated;
            sourceSnapshot = snapshot;
            pending = reanchoredPending;
            ackCursor = authoritativeAck;
            selectFirstEditableLayerIfNeeded();
            lastKnownRootHash = root;
            serverAuthorized = true;
            status = EditorStatus.READY;
        } catch (RuntimeException failure) {
            // Keep the previous in-memory draft intact when the downloaded
            // base cannot replay its acknowledged history.
            status = EditorStatus.ERROR;
        }
    }

    private void acceptGatewaySnapshot(DraftSnapshot snapshot) {
        if (closed || !draftId.equals(snapshot.draftId())) {
            return;
        }
        if (!pending.hasPending(draftId)) {
            ackCursor = Math.max(ackCursor, snapshot.ackCursor());
            if (snapshot.ackCursor() == ackCursor) {
                lastKnownRootHash = snapshot.draftRootHash();
            }
            dirty = false;
            status = EditorStatus.READY;
            return;
        }
        try {
            PendingOperationAck acknowledged = pending.acknowledge(
                    draftId, snapshot.ackCursor(), snapshot.draftRootHash());
            ackCursor = acknowledged.ackCursor();
            lastKnownRootHash = acknowledged.draftRootHash();
            dirty = pending.hasPending(draftId);
            status = dirty ? EditorStatus.DIRTY : EditorStatus.READY;
        } catch (RuntimeException failure) {
            status = EditorStatus.ERROR;
        }
    }

    private void selectFirstEditableLayerIfNeeded() {
        if (isEditableSelection(selectedFloorId, selectedLayerId)) {
            return;
        }
        selectedFloorId = null;
        selectedLayerId = null;
        for (String floorId : document.floorIds()) {
            for (EditableLayer layer : document.floor(floorId).layers()) {
                if (layer.type() == LayerType.RASTER_PAINT
                        && layer.visible() && !layer.locked()) {
                    selectedFloorId = floorId;
                    selectedLayerId = layer.id();
                    return;
                }
            }
        }
    }

    private boolean isEditableSelection(String floorId, String layerId) {
        if (floorId == null || layerId == null) {
            return false;
        }
        try {
            EditableLayer layer = document.layer(floorId, layerId);
            return layer.type() == LayerType.RASTER_PAINT
                    && layer.visible() && !layer.locked();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private void onTransportDetached() {
        if (closed) return;
        serverAuthorized = false;
        status = EditorStatus.DETACHED;
    }

    private void onTransportResuming() {
        if (closed) return;
        serverAuthorized = false;
        status = EditorStatus.RESUMING;
    }

    private void onTransportRestored(LocalEditorSessionGateway localGateway) {
        if (closed) return;
        WireIdentityCheckpoint checkpoint = new WireIdentityCheckpoint(
                localGateway.context().ackCursor(),
                localGateway.context().draftRootHash()
        );
        List<EditorCommand> retained = pending.snapshot(draftId).stream()
                .filter(command -> command.sequence() > checkpoint.ackCursor()).toList();
        PendingOperationQueue rebuilt = new PendingOperationQueue();
        rebuilt.reanchor(draftId, checkpoint.ackCursor(), checkpoint.rootHash());
        retained.forEach(command -> rebuilt.enqueue(draftId, command));
        localGateway.pendingCommandSequences().stream()
                .filter(sequence -> retained.stream().anyMatch(command ->
                        command.sequence() == sequence))
                .findFirst().ifPresent(sequence -> rebuilt.markSent(draftId, sequence));
        pending = rebuilt;
        ackCursor = checkpoint.ackCursor();
        if (retained.isEmpty()) lastKnownRootHash = checkpoint.rootHash();
        dirty = !retained.isEmpty() || localGateway.hasRetainedPublishIntent();
        serverAuthorized = true;
        status = dirty ? EditorStatus.DIRTY : EditorStatus.READY;
    }

    private void completeRebase(RebaseResult result) {
        if (closed || status != EditorStatus.REBASING) return;
        boolean conflicts = !result.conflicts().isEmpty() || result.mergePlan().stream()
                .anyMatch(RebaseResult.ConflictSlot.class::isInstance);
        if (conflicts) {
            status = EditorStatus.ERROR;
        } else if (dirty || pending.hasPending(draftId)) {
            status = EditorStatus.DIRTY;
        } else {
            lastKnownRootHash = result.mergedRootHash();
            status = EditorStatus.READY;
        }
    }

    private void validateSnapshot(DraftSnapshot snapshot, EditorCommand sentCommand) {
        if (!draftId.equals(snapshot.draftId())) {
            throw new EditorCommandException("Editor gateway returned a different draft ID");
        }
        if (snapshot.ackCursor() < ackCursor
                || snapshot.ackCursor() > sentCommand.sequence()) {
            throw new EditorCommandException("Editor gateway returned an invalid ACK cursor");
        }
        if (snapshot.ackCursor() == sentCommand.sequence()
                && !sentCommand.resultingRootHash().equals(snapshot.draftRootHash())) {
            throw new EditorCommandException("Editor gateway returned an invalid ACK root");
        }
    }

    private record WireIdentityCheckpoint(long ackCursor, Sha256 rootHash) {
        private WireIdentityCheckpoint {
            Objects.requireNonNull(rootHash, "rootHash");
        }
    }

    private record AcceptanceErrorObservationIdentity(
            WireIdentity.ScopeLease lease,
            WireIdentity.DocumentBinding binding,
            long baseRevision,
            Sha256 baseSourceHash
    ) {
        private AcceptanceErrorObservationIdentity {
            Objects.requireNonNull(lease, "lease");
            Objects.requireNonNull(binding, "binding");
            Objects.requireNonNull(baseSourceHash, "baseSourceHash");
        }

        private static AcceptanceErrorObservationIdentity from(
                WireIdentity.EditorContext context
        ) {
            return new AcceptanceErrorObservationIdentity(
                    context.lease(), context.binding(),
                    context.baseRevision(), context.baseSourceHash()
            );
        }

        private boolean matches(WireIdentity.EditorContext context) {
            return context != null
                    && lease.equals(context.lease())
                    && binding.equals(context.binding())
                    && baseRevision == context.baseRevision()
                    && baseSourceHash.equals(context.baseSourceHash());
        }
    }

    private static final class AcceptanceErrorObservationArm {
        private final AcceptanceErrorObservationIdentity identity;
        private final AtomicReference<LocalEditorSessionGateway.GatewayError> error =
                new AtomicReference<>();
        private volatile boolean armed = true;

        private AcceptanceErrorObservationArm(AcceptanceErrorObservationIdentity identity) {
            this.identity = Objects.requireNonNull(identity, "identity");
        }
    }
}
