package com.ptcrys.fpsmatch.common.client.minimap.editor;

import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorCommandException;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.wire.EditorWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.PublishWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireEditor;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns the request identity and correlation lifecycle for editor OPEN. */
final class EditorOpenHandshake {
    private final WireIdentity.ScopeLease lease;
    private final WireIdentity.DocumentBinding binding;
    private final Optional<Sha256> expectedSourceHash;
    private final Optional<Sha256> expectedRuntimeHash;
    private final Consumer<MinimapWireMessage> sender;
    private final Supplier<UUID> requestIds;
    private final Map<UUID, EditorRequestKind> requests;
    private UUID activeRequestId;
    private boolean sent;

    EditorOpenHandshake(
            WireIdentity.ScopeLease lease,
            WireIdentity.DocumentBinding binding,
            Optional<Sha256> expectedSourceHash,
            Optional<Sha256> expectedRuntimeHash,
            Consumer<MinimapWireMessage> sender,
            Supplier<UUID> requestIds,
            Map<UUID, EditorRequestKind> requests
    ) {
        this.lease = Objects.requireNonNull(lease, "lease");
        this.binding = Objects.requireNonNull(binding, "binding");
        this.expectedSourceHash = Objects.requireNonNull(
                expectedSourceHash, "expectedSourceHash"
        );
        this.expectedRuntimeHash = Objects.requireNonNull(
                expectedRuntimeHash, "expectedRuntimeHash"
        );
        this.sender = Objects.requireNonNull(sender, "sender");
        this.requestIds = Objects.requireNonNull(requestIds, "requestIds");
        this.requests = Objects.requireNonNull(requests, "requests");
    }

    boolean send(
            WireEditor.OpenMode openMode,
            long baseRevision,
            Consumer<LocalEditorSessionGateway.TransportState> stateSink
    ) {
        if (sent) return false;
        UUID requestId = requestIds.get();
        activeRequestId = requestId;
        requests.put(requestId, EditorRequestKind.OPEN);
        sent = true;
        stateSink.accept(LocalEditorSessionGateway.TransportState.OPENING);
        try {
            // The sender may synchronously reenter the gateway, so correlation is registered first.
            sender.accept(new EditorWireMessage.EditorOpen(
                    requestId,
                    lease,
                    binding.target(),
                    binding.documentId(),
                    openMode,
                    baseRevision,
                    expectedRuntimeHash
            ));
        } catch (RuntimeException failure) {
            if (cancel(requestId)) {
                stateSink.accept(LocalEditorSessionGateway.TransportState.ERROR);
            }
            throw failure;
        }
        return true;
    }

    boolean sent() {
        return sent;
    }

    boolean pending() {
        return activeRequestId != null
                && requests.get(activeRequestId) == EditorRequestKind.OPEN;
    }

    Optional<UUID> activeRequestId() {
        return Optional.ofNullable(activeRequestId);
    }

    boolean matches(EditorWireMessage.EditorSession session) {
        return pending()
                && activeRequestId.equals(session.requestId())
                && lease.equals(session.context().lease())
                && matchesBinding(session.context().binding());
    }

    boolean matchesOpenError(PublishWireMessage.ErrorMessage error) {
        if (error.requestId().isEmpty() || error.lease().isEmpty()
                || error.binding().isEmpty() || error.failedOpcode().isEmpty()) {
            return false;
        }
        UUID requestId = error.requestId().orElseThrow();
        return pending()
                && activeRequestId.equals(requestId)
                && requests.get(requestId) == EditorRequestKind.OPEN
                && lease.equals(error.lease().orElseThrow())
                && matchesBinding(error.binding().orElseThrow())
                && error.failedOpcode().orElseThrow()
                == EditorRequestKind.OPEN.opcode().code();
    }

    boolean matchesPendingError(PublishWireMessage.ErrorMessage error) {
        return pending()
                && error.requestId().filter(activeRequestId::equals).isPresent()
                && error.lease().filter(lease::equals).isPresent()
                && error.binding().filter(this::matchesBinding).isPresent()
                && error.failedOpcode().filter(opcode ->
                        opcode == EditorRequestKind.OPEN.opcode().code()).isPresent();
    }

    boolean matchesExpectedSource(WireIdentity.EditorContext context, long baseRevision) {
        return expectedSourceHash.isEmpty()
                || context.baseRevision() == baseRevision
                && expectedSourceHash.orElseThrow().equals(context.baseSourceHash());
    }

    boolean acceptExpectedSource(
            EditorWireMessage.EditorSession session,
            long baseRevision,
            Consumer<LocalEditorSessionGateway.GatewayError> rejection
    ) {
        if (matchesExpectedSource(session.context(), baseRevision)) return true;
        rejection.accept(new LocalEditorSessionGateway.GatewayError(
                session.requestId(),
                "Editor source identity changed before open",
                Optional.of(session.opcode().code())
        ));
        return false;
    }

    void requireCreateAllowed(WireEditor.SourceAvailability sourceAvailability) {
        if (sourceAvailability != WireEditor.SourceAvailability.NONE) {
            throw new EditorCommandException("The server already has an editor source");
        }
    }

    void completeRequest(UUID requestId) {
        requests.remove(requestId);
        if (requestId.equals(activeRequestId)) activeRequestId = null;
    }

    void finishSession() {
        sent = false;
    }

    void acceptError(UUID requestId) {
        requests.remove(requestId);
        if (requestId.equals(activeRequestId)) {
            activeRequestId = null;
            sent = false;
        }
    }

    void resetSendGuard() {
        sent = false;
    }

    void cancelActive() {
        UUID requestId = activeRequestId;
        if (requestId != null) requests.remove(requestId);
        activeRequestId = null;
        sent = false;
    }

    private boolean cancel(UUID requestId) {
        requests.remove(requestId);
        if (!requestId.equals(activeRequestId)) return false;
        activeRequestId = null;
        sent = false;
        return true;
    }

    private boolean matchesBinding(WireIdentity.DocumentBinding actual) {
        return binding.documentId().equals(actual.documentId())
                && binding.target().mapKey().equals(actual.target().mapKey())
                && binding.target().dimension().equals(actual.target().dimension());
    }
}
