package com.phasetranscrystal.fpsmatch.common.minimap.server;

import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EditorSession(
        UUID sessionId,
        UUID actorId,
        MapKey mapKey,
        NamespacedId dimension,
        NamespacedId documentId,
        UUID draftId,
        long baseRevision,
        Instant expiresAt
) {
    public EditorSession {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(mapKey, "mapKey");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(draftId, "draftId");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (baseRevision < 0) {
            throw new IllegalArgumentException("Session base revision must be non-negative");
        }
    }

    EditorSession renew(Instant nextExpiry) {
        return new EditorSession(
                sessionId,
                actorId,
                mapKey,
                dimension,
                documentId,
                draftId,
                baseRevision,
                nextExpiry
        );
    }
}
