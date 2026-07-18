package com.phasetranscrystal.fpsmatch.common.minimap.server;

import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DraftState(
        UUID draftId,
        MapKey mapKey,
        NamespacedId dimension,
        NamespacedId documentId,
        long baseRevision,
        Sha256 baseSourceHash,
        Sha256 draftRootHash,
        long ackCursor,
        Instant expiresAt
) {
    public DraftState {
        Objects.requireNonNull(draftId, "draftId");
        Objects.requireNonNull(mapKey, "mapKey");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(baseSourceHash, "baseSourceHash");
        Objects.requireNonNull(draftRootHash, "draftRootHash");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (baseRevision < 0 || ackCursor < 0) {
            throw new IllegalArgumentException("Draft revisions must be non-negative");
        }
    }
}
