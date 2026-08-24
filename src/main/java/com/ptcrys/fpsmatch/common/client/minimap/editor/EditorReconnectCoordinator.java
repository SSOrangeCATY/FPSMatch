package com.ptcrys.fpsmatch.common.client.minimap.editor;

import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorCommand;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorCommandException;
import com.ptcrys.fpsmatch.core.minimap.wire.EditorWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.PublishWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireEditor;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Owns transport epochs, resume correlation, and retained publish intent. */
final class EditorReconnectCoordinator {
    private final EditorResumeHandshake handshake;
    private long epoch;
    private boolean dirty;
    private boolean publishIntent;

    EditorReconnectCoordinator(EditorResumeHandshake handshake) {
        this.handshake = Objects.requireNonNull(handshake, "handshake");
    }

    long epoch() {
        return epoch;
    }

    boolean accepts(long candidate) {
        return candidate == epoch;
    }

    DownAction down(
            long candidate,
            LocalEditorSessionGateway.TransportState state,
            boolean closed
    ) {
        if (closed || candidate != epoch
                || state == LocalEditorSessionGateway.TransportState.LOCAL
                || state == LocalEditorSessionGateway.TransportState.CLOSED
                || state == LocalEditorSessionGateway.TransportState.DETACHED) {
            return DownAction.IGNORE;
        }
        return state == LocalEditorSessionGateway.TransportState.OPENING
                ? DownAction.FAIL_OPENING : DownAction.DETACH;
    }

    ReadyAction prepareReady(
            long candidate,
            LocalEditorSessionGateway.TransportState state,
            boolean closed,
            boolean hasServerSession,
            boolean localDirty,
            boolean hasPendingCommands
    ) {
        if (closed || candidate < epoch) return ReadyAction.IGNORE;
        epoch = candidate;
        if (state != LocalEditorSessionGateway.TransportState.DETACHED
                || !hasServerSession) return ReadyAction.EPOCH_ONLY;
        dirty = localDirty || hasPendingCommands;
        return ReadyAction.RESUME;
    }

    void sendResume(WireIdentity.EditorContext context) {
        handshake.send(epoch, context);
    }

    void prepareManualResume(boolean localModel) {
        dirty = localModel;
        publishIntent = false;
    }

    Optional<Checkpoint> accept(
            EditorWireMessage.EditorSession session,
            Map<Long, EditorCommand> pendingCommands
    ) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(pendingCommands, "pendingCommands");
        if (!handshake.matches(epoch, session)) return Optional.empty();
        WireIdentity.EditorContext next = session.context();
        if (next.ackCursor() > handshake.expectedAckCursor()) {
            EditorCommand confirmed = pendingCommands.get(next.ackCursor());
            if (confirmed == null
                    || !confirmed.resultingRootHash().equals(next.draftRootHash())) {
                throw new EditorCommandException(
                        "Editor resume returned an unknown ACK checkpoint"
                );
            }
        }
        handshake.complete();
        boolean hydrate = !dirty && pendingCommands.keySet().stream()
                .noneMatch(sequence -> sequence > next.ackCursor())
                && session.sourceAvailability() == WireEditor.SourceAvailability.FULL_SOURCE;
        return Optional.of(new Checkpoint(next, hydrate));
    }

    boolean matches(PublishWireMessage.ErrorMessage error) {
        return handshake.matches(epoch, error);
    }

    void retainPublishIntent(boolean pending) {
        publishIntent |= pending;
    }

    void publishSent() {
        publishIntent = true;
    }

    void publishCompleted() {
        publishIntent = false;
    }

    boolean hasPublishIntent() {
        return publishIntent;
    }

    void cancelHandshake() {
        handshake.cancel();
    }

    enum DownAction { IGNORE, FAIL_OPENING, DETACH }

    enum ReadyAction { IGNORE, EPOCH_ONLY, RESUME }

    record Checkpoint(WireIdentity.EditorContext context, boolean hydrate) {
        Checkpoint {
            Objects.requireNonNull(context, "context");
        }
    }
}
