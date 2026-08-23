package com.ptcrys.fpsmatch.common.client.minimap.editor;

import com.ptcrys.fpsmatch.core.minimap.editor.command.DraftSnapshot;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorCommand;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorCommandException;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorOperation;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorSessionGateway;
import com.ptcrys.fpsmatch.core.minimap.editor.command.RebaseResult;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Local-first editor gateway used by the OP minimap editor UI.
 * Draft/publish remain usable offline; open/close are forwarded to the server when a transport is available.
 */
public final class LocalEditorSessionGateway implements EditorSessionGateway {
    private static final Sha256 EMPTY_HASH = Sha256Digest.of(new byte[0]);

    private final UUID actorId;
    private final MapKey mapKey;
    private final NamespacedId dimension;
    private final NamespacedId documentId;
    private final UUID draftId;
    private final WireIdentity.ScopeLease lease;
    private final Consumer<MinimapWireMessage> sender;
    private final Supplier<UUID> requestIds;

    private final UUID sessionId;
    private UUID serverSessionId;
    private long baseRevision;
    private Sha256 baseSourceHash = EMPTY_HASH;
    private Sha256 draftRootHash = EMPTY_HASH;
    private long ackCursor;
    private final List<EditorOperation> operations = new ArrayList<>();
    private boolean serverSessionReady;
    private boolean openSent;
    private boolean closed;
    private UUID pendingPublishRequestId;
    private PublishCompletionListener publishCompletionListener = (committed, revision, detail) -> {
    };

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
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.mapKey = Objects.requireNonNull(mapKey, "mapKey");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.documentId = Objects.requireNonNull(documentId, "documentId");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.draftId = Objects.requireNonNull(draftId, "draftId");
        this.lease = Objects.requireNonNull(lease, "lease");
        this.sender = Objects.requireNonNull(sender, "sender");
        this.requestIds = Objects.requireNonNull(requestIds, "requestIds");
    }

    public UUID sessionId() {
        return sessionId;
    }

    public UUID draftId() {
        return draftId;
    }

    public boolean isServerSessionReady() {
        return serverSessionReady;
    }

    @Override
    public synchronized boolean isPublishInFlight() {
        return pendingPublishRequestId != null;
    }

    public synchronized void setPublishCompletionListener(PublishCompletionListener listener) {
        this.publishCompletionListener = Objects.requireNonNull(listener, "listener");
    }

    public synchronized void clearPublishCompletionListener() {
        this.publishCompletionListener = (committed, revision, detail) -> {
        };
    }

    public WireIdentity.EditorContext context() {
        UUID wireSessionId = serverSessionId != null ? serverSessionId : sessionId;
        return new WireIdentity.EditorContext(
                lease,
                new WireIdentity.DocumentBinding(
                        new WireIdentity.MapTarget(mapKey, dimension),
                        documentId
                ),
                wireSessionId,
                draftId,
                baseRevision,
                baseSourceHash,
                draftRootHash,
                ackCursor
        );
    }

    public synchronized void requestOpen(WireEditor.OpenMode openMode) {
        Objects.requireNonNull(openMode, "openMode");
        if (closed || openSent) {
            return;
        }
        openSent = true;
        try {
            sender.accept(new EditorWireMessage.EditorOpen(
                    requestIds.get(),
                    lease,
                    new WireIdentity.MapTarget(mapKey, dimension),
                    documentId,
                    openMode,
                    baseRevision,
                    Optional.empty()
            ));
        } catch (RuntimeException ignored) {
            openSent = false;
        }
    }

    public synchronized void acceptServerSession(EditorWireMessage.EditorSession session) {
        Objects.requireNonNull(session, "session");
        if (closed) {
            return;
        }
        WireIdentity.EditorContext context = session.context();
        if (!context.binding().target().mapKey().equals(mapKey)
                || !context.binding().target().dimension().equals(dimension)
                || !context.binding().documentId().equals(documentId)) {
            return;
        }
        // Keep provisional controller sessionId stable; only server wire context is updated.
        this.serverSessionId = context.sessionId();
        this.baseRevision = context.baseRevision();
        this.baseSourceHash = context.baseSourceHash();
        if (operations.isEmpty()) {
            this.draftRootHash = context.draftRootHash();
            this.ackCursor = context.ackCursor();
        }
        this.serverSessionReady = true;
    }

    public synchronized void requestClose(WireEditor.CloseMode closeMode) {
        Objects.requireNonNull(closeMode, "closeMode");
        if (closed) {
            return;
        }
        closed = true;
        pendingPublishRequestId = null;
        if (!serverSessionReady) {
            return;
        }
        try {
            sender.accept(new EditorWireMessage.EditorClose(
                    requestIds.get(),
                    context(),
                    closeMode
            ));
        } catch (RuntimeException ignored) {
            // Best-effort close notification.
        }
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
        Objects.requireNonNull(command, "command");
        operations.add(command.operation());
        draftRootHash = command.resultingRootHash();
        ackCursor += 1L;
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
        if (sequence < 0) {
            throw new EditorCommandException("Editor sequence must be non-negative");
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
        Objects.requireNonNull(expectedBaseHash, "expectedBaseHash");
        return new RebaseResult(
                baseSourceHash,
                List.copyOf(operations),
                List.of(),
                draftRootHash
        );
    }

    @Override
    public synchronized void publish(
            UUID sessionId,
            UUID actorId,
            Sha256 draftRootHash,
            boolean authorized
    ) {
        requireAuthorized(authorized);
        requireOpen(sessionId, actorId);
        Objects.requireNonNull(draftRootHash, "draftRootHash");
        if (!this.draftRootHash.equals(draftRootHash)) {
            throw new EditorCommandException("Draft root hash mismatch on publish");
        }
        this.draftRootHash = draftRootHash;
        if (!serverSessionReady) {
            // Offline / local-only editor: accept publish without server commit.
            return;
        }
        if (pendingPublishRequestId != null) {
            throw new EditorCommandException("Publish already in progress");
        }
        UUID requestId = requestIds.get();
        pendingPublishRequestId = requestId;
        try {
            sender.accept(new PublishWireMessage.ReservePublish(requestId, context()));
        } catch (RuntimeException failure) {
            pendingPublishRequestId = null;
            throw new EditorCommandException(
                    failure.getMessage() == null ? "Failed to send publish request" : failure.getMessage()
            );
        }
    }

    public synchronized void acceptPublishResult(PublishWireMessage.PublishResult result) {
        Objects.requireNonNull(result, "result");
        if (closed || pendingPublishRequestId == null) {
            return;
        }
        if (!pendingPublishRequestId.equals(result.requestId())) {
            return;
        }
        pendingPublishRequestId = null;
        boolean committed = result.outcome() == WireStatus.PublishOutcome.COMMITTED;
        String detail;
        if (committed) {
            this.baseRevision = result.publishRevision();
            result.hashes().ifPresent(hashes -> {
                this.baseSourceHash = hashes.sourceHash();
                this.draftRootHash = hashes.sourceHash();
            });
            detail = "committed";
        } else {
            detail = result.error()
                    .map(WireStatus.ErrorInfo::detail)
                    .filter(value -> value != null && !value.isBlank())
                    .orElse("publish aborted");
        }
        publishCompletionListener.onPublishCompleted(committed, result.publishRevision(), detail);
    }

    private DraftSnapshot snapshot() {
        return new DraftSnapshot(
                draftId,
                ackCursor,
                baseSourceHash,
                draftRootHash,
                List.copyOf(operations)
        );
    }

    private void requireAuthorized(boolean authorized) {
        if (!authorized) {
            throw new EditorCommandException("Editor action rejected: not server-authorized");
        }
    }

    private void requireOpen(UUID sessionId, UUID actorId) {
        if (closed) {
            throw new EditorCommandException("Editor session is closed");
        }
        if (!this.sessionId.equals(sessionId) || !this.actorId.equals(actorId)) {
            throw new EditorCommandException("Editor session actor mismatch");
        }
    }

    public static Sha256 emptyHash() {
        return EMPTY_HASH;
    }

    @FunctionalInterface
    public interface PublishCompletionListener {
        void onPublishCompleted(boolean committed, long publishRevision, String detail);
    }

    public static NamespacedId documentIdFor(MapKey mapKey) {
        Objects.requireNonNull(mapKey, "mapKey");
        String gameType = mapKey.gameType().toLowerCase().replace(':', '/');
        String mapName = slug(mapKey.mapName());
        return new NamespacedId("fpsmatch", "minimap/" + gameType + "/" + mapName);
    }

    private static String slug(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = Character.toLowerCase(value.charAt(index));
            if ((current >= 'a' && current <= 'z')
                    || (current >= '0' && current <= '9')
                    || current == '_'
                    || current == '-'
                    || current == '.'
                    || current == '/') {
                builder.append(current);
            } else {
                builder.append('_');
            }
        }
        String slug = builder.toString();
        return slug.isEmpty() ? "map" : slug;
    }
}
