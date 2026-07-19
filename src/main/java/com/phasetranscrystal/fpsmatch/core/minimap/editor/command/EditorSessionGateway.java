package com.phasetranscrystal.fpsmatch.core.minimap.editor.command;

import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;

import java.util.UUID;

/**
 * Platform-neutral boundary used by editor clients to talk to foundation session services.
 * Every implementing server adapter must re-check administrator permission on apply/resend/rebase/publish.
 */
public interface EditorSessionGateway {
    DraftSnapshot apply(UUID sessionId, UUID actorId, EditorCommand command, boolean authorized);

    DraftSnapshot resend(UUID sessionId, UUID actorId, long sequence, boolean authorized);

    RebaseResult rebase(UUID sessionId, UUID actorId, Sha256 expectedBaseHash, boolean authorized);

    void publish(UUID sessionId, UUID actorId, Sha256 draftRootHash, boolean authorized);

    /**
     * True when a server-side publish was dispatched and the client is waiting for PublishResult.
     * Sync/local gateways return false.
     */
    default boolean isPublishInFlight() {
        return false;
    }
}
