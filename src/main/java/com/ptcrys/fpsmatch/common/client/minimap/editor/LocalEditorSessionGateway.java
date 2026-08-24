package com.ptcrys.fpsmatch.common.client.minimap.editor;
import com.ptcrys.fpsmatch.core.minimap.editor.command.DraftSnapshot;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorCommand;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorCommandException;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorOperation;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorSessionGateway;
import com.ptcrys.fpsmatch.core.minimap.editor.command.RebaseResult;
import com.ptcrys.fpsmatch.core.minimap.editor.document.EditorSourceSnapshot;
import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.wire.EditorWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.PublishWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireEditor;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;
import com.ptcrys.fpsmatch.core.minimap.wire.WireStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
/** Client-side editor transport boundary and authoritative context owner. */
public final class LocalEditorSessionGateway implements EditorSessionGateway {
    private static final Sha256 EMPTY_HASH = Sha256Digest.of(new byte[0]);
    private final UUID actorId;
    private final MapKey mapKey;
    private final NamespacedId dimension;
    private final NamespacedId documentId;
    private UUID draftId;
    private final WireIdentity.ScopeLease lease;
    private final Consumer<MinimapWireMessage> sender;
    private final Supplier<UUID> requestIds;
    private final UUID sessionId;
    private final EditorOpenHandshake openHandshake;
    private final EditorReconnectCoordinator reconnect;
    private final EditorOperationTransport operationTransport;
    private final EditorPublishLifecycle publishLifecycle;
    private UUID serverSessionId;
    private long baseRevision;
    private Sha256 baseSourceHash = EMPTY_HASH;
    private Sha256 draftRootHash = EMPTY_HASH;
    private long ackCursor;
    private final List<EditorOperation> operations = new ArrayList<>();
    private final Map<Long, EditorCommand> pendingCommands = new HashMap<>();
    private final Map<UUID, Long> operationRequests = new HashMap<>();
    private final Map<UUID, EditorPendingUpload> uploads = new HashMap<>();
    private final Map<UUID, EditorRequestKind> requests = new HashMap<>();
    private WireEditor.SourceAvailability sourceAvailability = WireEditor.SourceAvailability.NONE;
    private UUID rebaseRequestId;
    private EditorSourceDownload sourceDownload;
    private final EditorCloseHandshake closeHandshake = new EditorCloseHandshake();
    private TransportState state = TransportState.LOCAL;
    private GatewayError lastError;
    private boolean closed;
    private Consumer<DraftSnapshot> snapshotListener = ignored -> { };
    private Consumer<GatewayError> errorListener = ignored -> { };
    private Consumer<EditorWireMessage.EditorSession> sessionListener = ignored -> { };
    private Consumer<EditorSourceSnapshot> sourceReadyListener = ignored -> { };
    private BiConsumer<RebaseResult, PublishWireMessage.EditorRebaseResult> rebaseListener =
            (result, wire) -> {
            };
    private PublishCompletionListener publishCompletionListener =
            (committed, revision, detail) -> { };
    private EditorTransportListener transportListener = EditorTransportListener.NONE;
    public LocalEditorSessionGateway(
            UUID actorId,
            MapKey mapKey,
            NamespacedId dimension,
            NamespacedId documentId,
            UUID sessionId,
            UUID draftId,
            WireIdentity.ScopeLease lease,
            Consumer<MinimapWireMessage> sender,
            Supplier<UUID> requestIds
    ) {
        this(actorId, mapKey, dimension, documentId, sessionId, draftId, lease,
                0L, Optional.empty(), Optional.empty(), sender, requestIds);
    }
    public LocalEditorSessionGateway(
            UUID actorId,
            MapKey mapKey,
            NamespacedId dimension,
            NamespacedId documentId,
            UUID sessionId,
            UUID draftId,
            WireIdentity.ScopeLease lease,
            long expectedRevision,
            Optional<Sha256> expectedSourceHash,
            Optional<Sha256> expectedRuntimeHash,
            Consumer<MinimapWireMessage> sender,
            Supplier<UUID> requestIds
    ) {
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.mapKey = Objects.requireNonNull(mapKey, "mapKey");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.documentId = Objects.requireNonNull(documentId, "documentId");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.draftId = Objects.requireNonNull(draftId, "draftId");
        this.lease = Objects.requireNonNull(lease, "lease");
        if (lease.scope() != WireIdentity.Scope.EDITOR) {
            throw new IllegalArgumentException("Editor gateway requires an editor lease");
        }
        this.sender = Objects.requireNonNull(sender, "sender");
        this.requestIds = Objects.requireNonNull(requestIds, "requestIds");
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("Expected editor revision must be non-negative");
        }
        this.baseRevision = expectedRevision;
        this.baseSourceHash = expectedSourceHash.orElse(EMPTY_HASH);
        WireIdentity.DocumentBinding binding = new WireIdentity.DocumentBinding(
                new WireIdentity.MapTarget(mapKey, dimension), documentId
        );
        this.openHandshake = new EditorOpenHandshake(
                lease, binding,
                expectedSourceHash,
                expectedRuntimeHash,
                sender,
                requestIds,
                requests
        );
        this.reconnect = new EditorReconnectCoordinator(new EditorResumeHandshake(
                lease, binding, sender, requestIds, requests
        ));
        this.operationTransport = new EditorOperationTransport(
                operationRequests, uploads, requests, sender, requestIds,
                this::context, this::isServerSessionReady
        );
        this.publishLifecycle = new EditorPublishLifecycle(
                lease, binding, sender, requestIds, requests
        );
    }

    public synchronized UUID sessionId() { return sessionId; }
    public synchronized UUID draftId() { return draftId; }
    public synchronized boolean isServerSessionReady() { return state == TransportState.READY; }
    public synchronized TransportState state() {
        return state;
    }
    public synchronized WireEditor.SourceAvailability sourceAvailability() {
        return sourceAvailability;
    }
    public synchronized Optional<GatewayError> lastError() {
        return Optional.ofNullable(lastError);
    }
    public synchronized boolean retainPendingCloseListener(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        if (!openHandshake.pending() || !closeHandshake.retain(listener)) {
            return false;
        }
        return true;
    }
    public synchronized void expirePendingClose() {
        if (!closeHandshake.pending()) {
            return;
        }
        openHandshake.cancelActive();
        requests.clear();
        closeHandshake.complete();
    }
    public synchronized void setSnapshotListener(Consumer<DraftSnapshot> listener) {
        snapshotListener = Objects.requireNonNull(listener, "listener");
    }
    public synchronized void setErrorListener(Consumer<GatewayError> listener) {
        errorListener = Objects.requireNonNull(listener, "listener");
    }
    public synchronized void setSessionListener(
            Consumer<EditorWireMessage.EditorSession> listener
    ) {
        sessionListener = Objects.requireNonNull(listener, "listener");
    }
    public synchronized void setSourceReadyListener(
            Consumer<EditorSourceSnapshot> listener
    ) {
        sourceReadyListener = Objects.requireNonNull(listener, "listener");
    }
    public synchronized void setRebaseListener(
            BiConsumer<RebaseResult, PublishWireMessage.EditorRebaseResult> listener
    ) {
        rebaseListener = Objects.requireNonNull(listener, "listener");
    }
    public synchronized void setPublishCompletionListener(PublishCompletionListener listener) {
        publishCompletionListener = Objects.requireNonNull(listener, "listener");
    }
    public synchronized void clearPublishCompletionListener() {
        publishCompletionListener = (committed, revision, detail) -> { };
    }
    synchronized void setTransportListener(EditorTransportListener listener) {
        transportListener = Objects.requireNonNull(listener, "listener");
    }
    public synchronized long transportEpoch() {
        return reconnect.epoch();
    }
    synchronized void acceptAtTransportEpoch(long epoch, Runnable dispatch) {
        Objects.requireNonNull(dispatch, "dispatch");
        if (reconnect.accepts(epoch)) dispatch.run();
    }
    public synchronized boolean hasRetainedPublishIntent() {
        return reconnect.hasPublishIntent();
    }
    public synchronized List<Long> pendingCommandSequences() {
        return pendingCommands.keySet().stream().sorted().toList();
    }
    public synchronized void transportDown(long epoch) {
        switch (reconnect.down(epoch, state, closed)) {
            case IGNORE -> { }
            case FAIL_OPENING -> {
                openHandshake.cancelActive();
                requests.clear();
                fail(new GatewayError(null,
                        "Editor connection closed before the session opened", Optional.empty()));
            }
            case DETACH -> {
                detachNetworkState();
                state = TransportState.DETACHED;
                transportListener.detached();
            }
        }
    }

    public synchronized void transportReady(long epoch, boolean localDirty) {
        EditorReconnectCoordinator.ReadyAction action = reconnect.prepareReady(
                epoch, state, closed, serverSessionId != null,
                localDirty, !pendingCommands.isEmpty());
        if (action != EditorReconnectCoordinator.ReadyAction.RESUME) return;
        state = TransportState.RESUMING;
        transportListener.resuming();
        try {
            reconnect.sendResume(context());
        } catch (RuntimeException failure) {
            fail(new GatewayError(
                    null,
                    failure.getMessage() == null ? "Editor resume failed" : failure.getMessage(),
                    Optional.of(EditorRequestKind.RESUME.opcode().code())
            ));
        }
    }

    public synchronized void transportReset() {
        if (closed) {
            return;
        }
        detachNetworkState();
        fail(new GatewayError(
                null, "Editor transport scope was reset", Optional.empty()
        ));
    }
    public synchronized boolean hasPendingNetworkWork() {
        return !pendingCommands.isEmpty() || !uploads.isEmpty()
                || rebaseRequestId != null || publishLifecycle.inFlight()
                || sourceDownload != null;
    }

    public synchronized WireIdentity.EditorContext context() {
        UUID wireSessionId = serverSessionId == null ? sessionId : serverSessionId;
        return new WireIdentity.EditorContext(
                lease,
                new WireIdentity.DocumentBinding(
                        new WireIdentity.MapTarget(mapKey, dimension), documentId
                ),
                wireSessionId,
                draftId,
                baseRevision,
                baseSourceHash,
                draftRootHash,
                ackCursor
        );
    }

    /** Opens an existing authoritative document. A missing document is reported by the server. */
    public synchronized void requestOpen() {
        requestOpen(WireEditor.OpenMode.OPEN_EXISTING);
    }
    public synchronized void requestOpen(WireEditor.OpenMode openMode) {
        Objects.requireNonNull(openMode, "openMode");
        if (closed || openHandshake.sent()) {
            return;
        }
        if (openMode == WireEditor.OpenMode.CREATE_EMPTY
                && sourceAvailability != WireEditor.SourceAvailability.NONE) {
            throw new EditorCommandException(
                    "CREATE_EMPTY is only valid after the server reports no source");
        }
        openHandshake.send(openMode, baseRevision, nextState -> state = nextState);
    }

    /** Explicitly creates an empty source after OPEN_EXISTING returned NONE. */
    public synchronized void createEmptyAfterMissingSource() {
        openHandshake.requireCreateAllowed(sourceAvailability);
        openHandshake.resetSendGuard();
        requestOpen(WireEditor.OpenMode.CREATE_EMPTY);
    }
    /** Starts a new transport session for a previously acknowledged draft locator. */
    public synchronized void requestResume(Sha256 expectedRoot, long expectedAckCursor) {
        Objects.requireNonNull(expectedRoot, "expectedRoot");
        if (expectedAckCursor < 0 || closed || state != TransportState.LOCAL
                || openHandshake.sent()) {
            throw new EditorCommandException("Editor resume is not available in the current state");
        }
        draftRootHash = expectedRoot;
        ackCursor = expectedAckCursor;
        serverSessionId = null;
        reconnect.prepareManualResume(true);
        state = TransportState.RESUMING;
        transportListener.resuming();
        try {
            reconnect.sendResume(context());
        } catch (RuntimeException failure) {
            fail(new GatewayError(
                    null,
                    failure.getMessage() == null ? "Editor resume failed" : failure.getMessage(),
                    Optional.of(EditorRequestKind.RESUME.opcode().code())
            ));
        }
    }

    public synchronized void acceptServerSession(EditorWireMessage.EditorSession session) {
        Objects.requireNonNull(session, "session");
        if (closed) {
            if (closeHandshake.pending() && openHandshake.matches(session)) {
                sendPendingClose(session.context());
            }
            return;
        }
        if (state == TransportState.RESUMING) {
            acceptResumedSession(session);
            return;
        }
        if (state != TransportState.OPENING || !openHandshake.matches(session)) {
            return;
        }
        WireIdentity.EditorContext context = session.context();
        if (!openHandshake.acceptExpectedSource(session, baseRevision, this::fail)) return;
        serverSessionId = context.sessionId();
        // The server allocates the draft during OPEN. The provisional client ID
        // must never be sent after this point.
        draftId = context.draftId();
        baseRevision = context.baseRevision();
        baseSourceHash = context.baseSourceHash();
        if (pendingCommands.isEmpty() && operations.isEmpty()) {
            draftRootHash = context.draftRootHash();
            ackCursor = context.ackCursor();
        }
        sourceAvailability = session.sourceAvailability();
        openHandshake.completeRequest(session.requestId());
        lastError = null;
        state = sourceAvailability == WireEditor.SourceAvailability.FULL_SOURCE
                ? TransportState.WAITING_SOURCE : TransportState.READY;
        if (state == TransportState.WAITING_SOURCE) {
            startSourceDownload(context);
        }
        openHandshake.finishSession();
        sessionListener.accept(session);
    }

    private void acceptResumedSession(EditorWireMessage.EditorSession session) {
        EditorReconnectCoordinator.Checkpoint checkpoint;
        try {
            Optional<EditorReconnectCoordinator.Checkpoint> accepted =
                    reconnect.accept(session, pendingCommands);
            if (accepted.isEmpty()) return;
            checkpoint = accepted.orElseThrow();
        } catch (EditorCommandException invalidCheckpoint) {
            fail(new GatewayError(
                    session.requestId(), invalidCheckpoint.getMessage(),
                    Optional.of(EditorRequestKind.RESUME.opcode().code())
            ));
            return;
        }
        WireIdentity.EditorContext next = checkpoint.context();
        serverSessionId = next.sessionId();
        baseRevision = next.baseRevision();
        baseSourceHash = next.baseSourceHash();
        draftRootHash = next.draftRootHash();
        ackCursor = next.ackCursor();
        pendingCommands.entrySet().removeIf(entry -> entry.getKey() <= ackCursor);
        sourceAvailability = session.sourceAvailability();
        lastError = null;
        state = checkpoint.hydrate() ? TransportState.WAITING_SOURCE : TransportState.READY;
        sessionListener.accept(session);
        if (checkpoint.hydrate()) {
            startSourceDownload(next);
            return;
        }
        transportListener.restored();
        operationTransport.dispatchReady(pendingCommands);
    }

    /** Handles all editor ACK variants; stale or cross-session ACKs are ignored. */
    public synchronized void acceptEditorAck(EditorWireMessage.EditorAck ack) {
        Objects.requireNonNull(ack, "ack");
        if (closed || !validContext(ack.context())) {
            return;
        }
        EditorRequestKind requestKind = requests.get(ack.requestId());
        if (ack.data() instanceof WireEditor.OperationAck) {
            if (requestKind != EditorRequestKind.OPERATION) {
                return;
            }
            Long sequence = operationRequests.remove(ack.requestId());
            if (sequence == null || !pendingCommands.containsKey(sequence)) {
                return;
            }
            acknowledge(ack.context(), sequence);
            requests.remove(ack.requestId());
            return;
        }
        if (ack.data() instanceof WireEditor.DraftSaved) {
            if (requestKind != EditorRequestKind.SAVE) {
                return;
            }
            updateAuthoritativeContext(ack.context());
            requests.remove(ack.requestId());
            snapshotListener.accept(snapshot());
            return;
        }
        if (ack.data() instanceof WireEditor.UploadAck uploadAck) {
            EditorPendingUpload pending = uploads.values().stream()
                    .filter(upload -> upload.ownsRequest(ack.requestId()))
                    .findFirst().orElse(null);
            if (pending == null) {
                return;
            }
            switch (pending.accept(ack.requestId(), requestKind, uploadAck)) {
                case IGNORED -> {
                    return;
                }
                case BEGIN_ACCEPTED -> {
                    requests.remove(ack.requestId());
                    operationTransport.dispatchUpload(pending);
                }
                case DATA_ACCEPTED -> requests.remove(ack.requestId());
                case HASH_MISMATCH -> {
                    operationTransport.clearUploadRequests(pending);
                    uploads.remove(pending.beginRequest());
                    fail(new GatewayError(
                            ack.requestId(), "Upload ACK hash does not match the requested object",
                            Optional.empty()
                    ));
                }
                case COMPLETED -> {
                    requests.remove(ack.requestId());
                    operationTransport.clearUploadRequests(pending);
                    operationTransport.dispatchReady(pendingCommands);
                }
            }
            return;
        }
        if (ack.data() instanceof WireEditor.Closed) {
            if (requestKind != EditorRequestKind.CLOSE) {
                return;
            }
            requests.remove(ack.requestId());
            state = TransportState.CLOSED;
        }
    }

    public synchronized void acceptSourceManifest(EditorWireMessage.SourceManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        if (!validSourceResponse(manifest.requestId(), manifest.context(), manifest.sourceHash())) return;
        try {
            sourceDownload.accept(manifest);
        } catch (RuntimeException failure) {
            failSource(manifest.requestId(), failure);
        }
    }

    public synchronized void acceptSourceFragment(EditorWireMessage.SourceFragment fragment) {
        Objects.requireNonNull(fragment, "fragment");
        if (!validSourceResponse(
                fragment.requestId(), fragment.context(), fragment.sourceHash()
        )) {
            return;
        }
        try {
            sourceDownload.accept(fragment);
        } catch (RuntimeException failure) {
            failSource(fragment.requestId(), failure);
        }
    }

    public synchronized void acceptRebaseResult(PublishWireMessage.EditorRebaseResult result) {
        Objects.requireNonNull(result, "result");
        if (closed || rebaseRequestId == null
                || !rebaseRequestId.equals(result.requestId())
                || requests.get(result.requestId()) != EditorRequestKind.REBASE
                || !lease.equals(result.lease())
                || !matchesBinding(result.binding())) {
            return;
        }
        WireIdentity.EditorContext next = result.newContext().orElse(null);
        if (result.status() == WireStatus.RebaseResultStatus.MERGED && (next == null
                || !lease.equals(next.lease()) || !matchesBinding(next.binding())
                || !Objects.equals(serverSessionId, next.sessionId()) || !draftId.equals(next.draftId()))) return;
        requests.remove(result.requestId());
        rebaseRequestId = null;
        if (result.status() == WireStatus.RebaseResultStatus.MERGED) {
            updateAuthoritativeContext(next);
            RebaseResult merged = new RebaseResult(
                    next.baseSourceHash(), List.of(), List.of(),
                    next.draftRootHash(), Math.max(1L, next.ackCursor() + 1L)
            );
            rebaseListener.accept(merged, result);
            return;
        }
        RebaseResult conflicts = new RebaseResult(
                baseSourceHash, List.of(), List.of(), draftRootHash,
                Math.max(1L, ackCursor + 1L),
                List.of(new RebaseResult.ConflictSlot(result.rebaseId()))
        );
        rebaseListener.accept(conflicts, result);
    }

    public synchronized void acceptError(PublishWireMessage.ErrorMessage error) {
        if (publishLifecycle.accept(Objects.requireNonNull(error, "error"), this::completePublish)) return;
        if (closed) {
            if (closeHandshake.pending() && openHandshake.matchesPendingError(error)) {
                openHandshake.cancelActive();
                requests.clear();
                closeHandshake.complete();
            }
            return;
        }
        if (state == TransportState.RESUMING) {
            if (!reconnect.matches(error)) {
                return;
            }
            reconnect.cancelHandshake();
            fail(new GatewayError(
                    error.requestId().orElse(null), error.error().detail(),
                    error.failedOpcode()
            ));
            return;
        }
        if (closed || error.requestId().isEmpty() || error.lease().isEmpty()
                || error.binding().isEmpty() || error.failedOpcode().isEmpty()) {
            return;
        }
        UUID requestId = error.requestId().orElseThrow();
        EditorRequestKind requestKind = requests.get(requestId);
        if (requestKind == null
                || !lease.equals(error.lease().orElseThrow())
                || !matchesBinding(error.binding().orElseThrow())
                || error.failedOpcode().orElseThrow() != requestKind.opcode().code()
                || (requestKind == EditorRequestKind.REBASE && !requestId.equals(rebaseRequestId))) {
            return;
        }
        if (requestKind == EditorRequestKind.OPEN
                && !openHandshake.matchesOpenError(error)) {
            return;
        }
        if (requestKind == EditorRequestKind.OPEN) {
            openHandshake.acceptError(requestId);
        } else {
            requests.remove(requestId);
        }
        operationRequests.remove(requestId);
        requests.remove(rebaseRequestId);
        rebaseRequestId = null;
        EditorPendingUpload failedUpload = uploads.values().stream()
                .filter(upload -> upload.ownsRequest(requestId)).findFirst().orElse(null);
        if (requestKind.opcode() == EditorRequestKind.UPLOAD_BEGIN.opcode() && failedUpload == null) return;
        if (failedUpload != null) {
            operationTransport.clearUploadRequests(failedUpload);
            uploads.remove(failedUpload.beginRequest());
        }
        if (requestKind == EditorRequestKind.SOURCE) {
            clearSourceRequests();
        }
        GatewayError failure = new GatewayError(
                requestId,
                error.error().detail(),
                error.failedOpcode()
        );
        fail(failure);
        if (error.error().retryDisposition() == WireStatus.RetryDisposition.REOPEN_SESSION) {
            if (requestKind == EditorRequestKind.OPEN) {
                requestOpen(WireEditor.OpenMode.OPEN_EXISTING);
            } else {
                detachNetworkState();
                state = TransportState.DETACHED;
                transportListener.detached();
                transportReady(reconnect.epoch(), !pendingCommands.isEmpty());
            }
        }
    }
    @Override
    public synchronized boolean isPublishInFlight() {
        return publishLifecycle.inFlight();
    }
    @Override
    public synchronized DraftSnapshot apply(
            UUID sessionId,
            UUID actorId,
            EditorCommand command,
            boolean authorized
    ) {
        requireAuthorized(authorized);
        requireOpen(sessionId, actorId);
        requireSourceReady();
        Objects.requireNonNull(command, "command");
        validateCommand(command);
        if (!isServerSessionReady()) {
            if (command.sequence() <= ackCursor) {
                return snapshot();
            }
            operations.addAll(command.edit().forward());
            draftRootHash = command.resultingRootHash();
            ackCursor = command.sequence();
            return snapshot();
        }
        EditorCommand previous = pendingCommands.putIfAbsent(command.sequence(), command);
        if (previous != null) {
            if (!previous.equals(command)) {
                throw new EditorCommandException("A different command is already pending at this sequence");
            }
            return snapshot();
        }
        operationTransport.dispatchReady(pendingCommands);
        return snapshot();
    }
    @Override
    public synchronized DraftSnapshot resend(
            UUID sessionId,
            UUID actorId,
            long sequence,
            boolean authorized
    ) {
        requireAuthorized(authorized);
        requireOpen(sessionId, actorId);
        if (sequence < 1) {
            throw new EditorCommandException("Editor sequence must be positive");
        }
        EditorCommand command = pendingCommands.get(sequence);
        if (command != null && isServerSessionReady()) {
            requests.keySet().removeIf(id -> Objects.equals(operationRequests.get(id), sequence));
            operationRequests.values().removeIf(value -> value == sequence);
            operationTransport.dispatch(command);
        }
        return snapshot();
    }
    @Override
    public synchronized RebaseResult rebase(
            UUID sessionId,
            UUID actorId,
            Sha256 expectedBaseHash,
            boolean authorized
    ) {
        requireAuthorized(authorized);
        requireOpen(sessionId, actorId);
        requireSourceReady();
        Objects.requireNonNull(expectedBaseHash, "expectedBaseHash");
        if (!isServerSessionReady()) {
            return new RebaseResult(
                    draftRootHash, List.of(), List.of(), draftRootHash,
                    Math.max(1L, ackCursor + 1L)
            );
        }
        if (rebaseRequestId != null) {
            throw new EditorCommandException("Editor rebase is already in progress");
        }
        rebaseRequestId = requestIds.get();
        requests.put(rebaseRequestId, EditorRequestKind.REBASE);
        sender.accept(new PublishWireMessage.EditorRebase(
                rebaseRequestId,
                context(),
                new WireEditor.RebaseStart(baseRevision, expectedBaseHash)
        ));
        return new RebaseResult(
                baseSourceHash, List.of(), List.of(), draftRootHash,
                Math.max(1L, ackCursor + 1L)
        );
    }

    @Override
    public synchronized void publish(
            UUID sessionId,
            UUID actorId,
            Sha256 requestedRootHash,
            boolean authorized
    ) {
        requireAuthorized(authorized);
        requireOpen(sessionId, actorId);
        requireSourceReady();
        Objects.requireNonNull(requestedRootHash, "requestedRootHash");
        if (!draftRootHash.equals(requestedRootHash)) {
            throw new EditorCommandException("Draft root hash mismatch on publish");
        }
        if (!isServerSessionReady()) {
            return;
        }
        if (publishLifecycle.queryRequired()) {
            publishLifecycle.retryStatusQuery().ifPresent(this::completePublish);
            return;
        }
        if (hasPendingNetworkWork() && (!publishLifecycle.inFlight()
                || !pendingCommands.isEmpty() || !uploads.isEmpty())) {
            throw new EditorCommandException("Publish requires all draft operations to be acknowledged");
        }
        if (publishLifecycle.inFlight()) {
            throw new EditorCommandException("Publish already in progress");
        }
        reconnect.publishSent();
        try {
            publishLifecycle.begin(context());
        } catch (RuntimeException failure) {
            reconnect.publishCompleted();
            throw failure;
        }
    }

    public synchronized void acceptPublishResult(PublishWireMessage.PublishResult result) {
        Objects.requireNonNull(result, "result");
        if (!closed) publishLifecycle.accept(result).ifPresent(this::completePublish);
    }

    public synchronized void acceptPublishStatus(PublishWireMessage.PublishStatus status) {
        Objects.requireNonNull(status, "status");
        if (!closed) publishLifecycle.accept(status).ifPresent(this::completePublish);
    }

    public synchronized void requestClose(WireEditor.CloseMode closeMode) {
        Objects.requireNonNull(closeMode, "closeMode");
        if (closed) {
            return;
        }
        closed = true;
        boolean awaitingOpen = serverSessionId == null && openHandshake.pending();
        UUID activeOpenRequest = openHandshake.activeRequestId().orElse(null);
        if (awaitingOpen) {
            closeHandshake.begin(closeMode);
        }
        if (serverSessionId != null) {
            try {
                UUID requestId = requestIds.get();
                requests.put(requestId, EditorRequestKind.CLOSE);
                sender.accept(new EditorWireMessage.EditorClose(
                        requestId, context(), closeMode
                ));
            } catch (RuntimeException ignored) {
                // Closing a screen must not strand the client UI on a transport error.
            }
        }
        clearLocalState(awaitingOpen ? activeOpenRequest : null);
    }

    private void sendPendingClose(WireIdentity.EditorContext sessionContext) {
        WireEditor.CloseMode closeMode = closeHandshake.mode();
        serverSessionId = sessionContext.sessionId();
        draftId = sessionContext.draftId();
        baseRevision = sessionContext.baseRevision();
        baseSourceHash = sessionContext.baseSourceHash();
        draftRootHash = sessionContext.draftRootHash();
        ackCursor = sessionContext.ackCursor();
        UUID closeRequestId = requestIds.get();
        requests.clear();
        requests.put(closeRequestId, EditorRequestKind.CLOSE);
        openHandshake.cancelActive();
        try {
            sender.accept(new EditorWireMessage.EditorClose(
                    closeRequestId, sessionContext, closeMode
            ));
        } catch (RuntimeException ignored) {
            // The session is already closed locally; a transport failure cannot reopen it.
        }
        requests.clear();
        closeHandshake.complete();
    }

    private void acknowledge(WireIdentity.EditorContext next, long sequence) {
        if (next.ackCursor() < ackCursor || next.ackCursor() < sequence
                || !pendingCommands.containsKey(sequence)) {
            fail(new GatewayError(
                    null, "Editor ACK cursor is not a forward acknowledgement", Optional.empty()
            ));
            return;
        }
        EditorCommand command = pendingCommands.get(sequence);
        if (!command.resultingRootHash().equals(next.draftRootHash())) {
            fail(new GatewayError(
                    null, "Editor ACK root does not match the submitted command", Optional.empty()
            ));
            return;
        }
        updateAuthoritativeContext(next);
        pendingCommands.entrySet().removeIf(entry -> entry.getKey() <= next.ackCursor());
        operationRequests.values().removeIf(value -> value <= next.ackCursor());
        uploads.entrySet().removeIf(entry -> entry.getValue().sequence() <= next.ackCursor());
        requests.entrySet().removeIf(entry -> entry.getValue() == EditorRequestKind.OPERATION);
        snapshotListener.accept(snapshot());
    }

    private void updateAuthoritativeContext(WireIdentity.EditorContext next) {
        serverSessionId = next.sessionId();
        baseRevision = next.baseRevision();
        baseSourceHash = next.baseSourceHash();
        draftRootHash = next.draftRootHash();
        ackCursor = next.ackCursor();
        state = TransportState.READY;
    }

    private void fail(GatewayError failure) {
        lastError = failure;
        state = TransportState.ERROR;
        errorListener.accept(failure);
    }

    private boolean validContext(WireIdentity.EditorContext context) {
        return lease.equals(context.lease())
                && draftId.equals(context.draftId())
                && (serverSessionId == null || serverSessionId.equals(context.sessionId()))
                && baseRevision == context.baseRevision()
                && baseSourceHash.equals(context.baseSourceHash())
                && matchesBinding(context.binding());
    }

    private boolean validSourceResponse(
            UUID requestId,
            WireIdentity.EditorContext context,
            Sha256 sourceHash
    ) {
        return !closed && state == TransportState.WAITING_SOURCE
                && requests.get(requestId) == EditorRequestKind.SOURCE
                && sourceDownload != null && sourceDownload.owns(requestId)
                && baseSourceHash.equals(sourceHash)
                && context.equals(context());
    }

    private void acceptHydratedSource(EditorSourceSnapshot snapshot) {
        if (closed || state != TransportState.WAITING_SOURCE || sourceDownload == null) return;
        clearSourceRequests();
        state = TransportState.READY;
        lastError = null;
        sourceReadyListener.accept(snapshot);
        transportListener.restored();
    }

    private void startSourceDownload(WireIdentity.EditorContext context) {
        sourceDownload = new EditorSourceDownload(
                context, baseSourceHash, sender, requestIds,
                requestId -> requests.put(requestId, EditorRequestKind.SOURCE),
                requests::remove, this::acceptHydratedSource
        );
        try {
            sourceDownload.start();
        } catch (RuntimeException failure) {
            failSource(null, failure);
        }
    }

    private void failSource(UUID requestId, RuntimeException failure) {
        clearSourceRequests();
        fail(new GatewayError(
                requestId,
                failure.getMessage() == null ? "Editor source transfer failed" : failure.getMessage(),
                Optional.of(com.ptcrys.fpsmatch.core.minimap.contract.MinimapOpcode
                        .C2S_EDITOR_REQUEST_SOURCE_ENTRIES.code())
        ));
    }

    private void clearSourceRequests() {
        EditorSourceDownload download = sourceDownload;
        sourceDownload = null;
        if (download != null) download.cancel();
        requests.entrySet().removeIf(entry -> entry.getValue() == EditorRequestKind.SOURCE);
    }

    private void detachNetworkState() {
        reconnect.retainPublishIntent(publishLifecycle.inFlight());
        publishLifecycle.clear();
        rebaseRequestId = null;
        operationRequests.clear();
        uploads.clear();
        clearSourceRequests();
        reconnect.cancelHandshake();
        requests.clear();
    }

    private void completePublish(EditorPublishLifecycle.Resolution resolution) {
        if (!resolution.terminal()) {
            publishCompletionListener.onPublishCompleted(
                    false, resolution.revision(), resolution.detail()
            );
            return;
        }
        reconnect.publishCompleted();
        if (resolution.committed()) {
            baseRevision = resolution.revision();
            resolution.hashes().ifPresent(hashes -> {
                baseSourceHash = hashes.sourceHash();
                draftRootHash = hashes.sourceHash();
            });
            closed = true;
            clearLocalState(null);
        }
        publishCompletionListener.onPublishCompleted(
                resolution.committed(), resolution.revision(), resolution.detail()
        );
    }

    private void clearLocalState(UUID retainedRequestId) {
        state = TransportState.CLOSED;
        publishLifecycle.clear();
        reconnect.publishCompleted();
        pendingCommands.clear();
        operationRequests.clear();
        uploads.clear();
        clearSourceRequests();
        if (retainedRequestId == null) requests.clear();
        else requests.entrySet().removeIf(entry -> !entry.getKey().equals(retainedRequestId));
    }

    private void requireSourceReady() {
        if (state != TransportState.LOCAL && state != TransportState.READY) {
            throw new EditorCommandException("Editor source is not ready");
        }
    }

    private boolean matchesBinding(WireIdentity.DocumentBinding binding) {
        return binding.documentId().equals(documentId)
                && binding.target().mapKey().equals(mapKey)
                && binding.target().dimension().equals(dimension);
    }

    private void validateCommand(EditorCommand command) {
        if (command.sequence() <= ackCursor) {
            if (command.sequence() == ackCursor
                    && command.resultingRootHash().equals(draftRootHash)) {
                return;
            }
            throw new EditorCommandException("Editor command sequence was already acknowledged");
        }
        if (command.sequence() != ackCursor + 1L) {
            throw new EditorCommandException(
                    "Editor command sequence must follow the ACK cursor: expected "
                            + (ackCursor + 1L) + " but was " + command.sequence()
            );
        }
        Sha256 expectedPrevious = pendingCommands.isEmpty()
                ? draftRootHash
                : pendingCommands.values().stream()
                .max(java.util.Comparator.comparingLong(EditorCommand::sequence))
                .orElseThrow().resultingRootHash();
        if (!command.previousRoot().equals(expectedPrevious)) {
            throw new EditorCommandException("Editor command previous root does not match the draft root");
        }
    }

    private DraftSnapshot snapshot() {
        return new DraftSnapshot(
                draftId, ackCursor, baseSourceHash, draftRootHash, List.copyOf(operations)
        );
    }

    private void requireAuthorized(boolean authorized) {
        if (!authorized) {
            throw new EditorCommandException("Editor action rejected: not server-authorized");
        }
    }

    private void requireOpen(UUID expectedSessionId, UUID expectedActorId) {
        if (closed) {
            throw new EditorCommandException("Editor session is closed");
        }
        if (!sessionId.equals(expectedSessionId) || !actorId.equals(expectedActorId)) {
            throw new EditorCommandException("Editor session actor mismatch");
        }
    }

    public static Sha256 emptyHash() {
        return EMPTY_HASH;
    }

    public static NamespacedId documentIdFor(MapKey mapKey) {
        return EditorDocumentIdentity.forMap(mapKey);
    }

    public enum TransportState {
        LOCAL, OPENING, WAITING_SOURCE, READY, DETACHED, RESUMING, ERROR, CLOSED
    }

    public record GatewayError(UUID requestId, String detail, Optional<Integer> failedOpcode) {
        public GatewayError {
            detail = detail == null || detail.isBlank() ? "Editor request failed" : detail;
            failedOpcode = Objects.requireNonNull(failedOpcode, "failedOpcode");
        }
    }

    @FunctionalInterface
    public interface PublishCompletionListener {
        void onPublishCompleted(boolean committed, long publishRevision, String detail);
    }
}
