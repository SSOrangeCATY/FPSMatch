package com.ptcrys.fpsmatch.common.minimap.server;

import java.util.Objects;
import java.util.UUID;

public record UploadOwnerScope(
        UUID actorId,
        UUID sessionId,
        UUID draftId,
        long baseRevision
) {
    public UploadOwnerScope {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(draftId, "draftId");
        if (baseRevision < 0) {
            throw new IllegalArgumentException("Upload base revision must be non-negative");
        }
    }
}
