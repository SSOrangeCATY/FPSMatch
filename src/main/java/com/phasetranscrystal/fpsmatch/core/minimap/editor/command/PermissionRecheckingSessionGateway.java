package com.phasetranscrystal.fpsmatch.core.minimap.editor.command;

import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;

public final class PermissionRecheckingSessionGateway implements EditorSessionGateway {
    private final EditorSessionGateway delegate;
    private final BiPredicate<UUID, UUID> permissionCheck;

    public PermissionRecheckingSessionGateway(
            EditorSessionGateway delegate,
            BiPredicate<UUID, UUID> permissionCheck
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.permissionCheck = Objects.requireNonNull(permissionCheck, "permissionCheck");
    }

    @Override
    public DraftSnapshot apply(UUID sessionId, UUID actorId, EditorCommand command, boolean authorized) {
        requireAuthorized(sessionId, actorId, authorized);
        return delegate.apply(sessionId, actorId, command, true);
    }

    @Override
    public DraftSnapshot resend(UUID sessionId, UUID actorId, long sequence, boolean authorized) {
        requireAuthorized(sessionId, actorId, authorized);
        return delegate.resend(sessionId, actorId, sequence, true);
    }

    @Override
    public RebaseResult rebase(UUID sessionId, UUID actorId, Sha256 expectedBaseHash, boolean authorized) {
        requireAuthorized(sessionId, actorId, authorized);
        return delegate.rebase(sessionId, actorId, expectedBaseHash, true);
    }

    @Override
    public void publish(UUID sessionId, UUID actorId, Sha256 draftRootHash, boolean authorized) {
        requireAuthorized(sessionId, actorId, authorized);
        delegate.publish(sessionId, actorId, draftRootHash, true);
    }

    private void requireAuthorized(UUID sessionId, UUID actorId, boolean authorized) {
        if (!authorized || !permissionCheck.test(sessionId, actorId)) {
            throw new EditorCommandException("Editor action rejected: permission recheck failed");
        }
    }
}
