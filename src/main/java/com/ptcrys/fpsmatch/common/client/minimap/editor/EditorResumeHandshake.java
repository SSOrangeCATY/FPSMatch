package com.ptcrys.fpsmatch.common.client.minimap.editor;

import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.wire.EditorWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.PublishWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Correlates one editor resume request to one client transport epoch. */
final class EditorResumeHandshake {
    private final WireIdentity.ScopeLease lease;
    private final WireIdentity.DocumentBinding binding;
    private final Consumer<MinimapWireMessage> sender;
    private final Supplier<UUID> requestIds;
    private final Map<UUID, EditorRequestKind> requests;
    private UUID requestId;
    private long transportEpoch = Long.MIN_VALUE;
    private UUID draftId;
    private long baseRevision;
    private Sha256 baseSourceHash;
    private Sha256 draftRootHash;
    private long ackCursor;

    EditorResumeHandshake(
            WireIdentity.ScopeLease lease,
            WireIdentity.DocumentBinding binding,
            Consumer<MinimapWireMessage> sender,
            Supplier<UUID> requestIds,
            Map<UUID, EditorRequestKind> requests
    ) {
        this.lease = Objects.requireNonNull(lease, "lease");
        this.binding = Objects.requireNonNull(binding, "binding");
        this.sender = Objects.requireNonNull(sender, "sender");
        this.requestIds = Objects.requireNonNull(requestIds, "requestIds");
        this.requests = Objects.requireNonNull(requests, "requests");
    }

    void send(long epoch, WireIdentity.EditorContext context) {
        if (pending()) {
            throw new IllegalStateException("Editor resume is already pending");
        }
        transportEpoch = epoch;
        draftId = context.draftId();
        baseRevision = context.baseRevision();
        baseSourceHash = context.baseSourceHash();
        draftRootHash = context.draftRootHash();
        ackCursor = context.ackCursor();
        requestId = Objects.requireNonNull(requestIds.get(), "requestIds returned null");
        requests.put(requestId, EditorRequestKind.RESUME);
        try {
            sender.accept(new EditorWireMessage.EditorResume(
                    requestId, lease, binding, draftId, draftRootHash, ackCursor
            ));
        } catch (RuntimeException failure) {
            cancel();
            throw failure;
        }
    }

    boolean matches(long epoch, EditorWireMessage.EditorSession session) {
        if (!pending() || epoch != transportEpoch
                || !requestId.equals(session.requestId())) {
            return false;
        }
        WireIdentity.EditorContext context = session.context();
        if (!lease.equals(context.lease()) || !binding.equals(context.binding())
                || !draftId.equals(context.draftId())
                || baseRevision != context.baseRevision()
                || !baseSourceHash.equals(context.baseSourceHash())
                || context.ackCursor() < ackCursor) {
            return false;
        }
        return context.ackCursor() != ackCursor
                || draftRootHash.equals(context.draftRootHash());
    }

    boolean matches(long epoch, PublishWireMessage.ErrorMessage error) {
        return pending() && epoch == transportEpoch
                && error.requestId().filter(requestId::equals).isPresent()
                && error.lease().filter(lease::equals).isPresent()
                && error.binding().filter(binding::equals).isPresent()
                && error.failedOpcode().filter(opcode ->
                        opcode == EditorRequestKind.RESUME.opcode().code()).isPresent();
    }

    long expectedAckCursor() {
        return ackCursor;
    }

    Sha256 expectedDraftRootHash() {
        return draftRootHash;
    }

    boolean pending() {
        return requestId != null && requests.get(requestId) == EditorRequestKind.RESUME;
    }

    void complete() {
        cancel();
    }

    void cancel() {
        if (requestId != null) {
            requests.remove(requestId);
        }
        requestId = null;
        transportEpoch = Long.MIN_VALUE;
        draftId = null;
        baseSourceHash = null;
        draftRootHash = null;
    }
}
