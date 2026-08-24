package com.ptcrys.fpsmatch.common.minimap.server;

import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

enum DraftLifecycle {
    CREATING,
    ACTIVE,
    DELETING
}

final class MutableDraft {
    final UUID draftId;
    final MapKey mapKey;
    final NamespacedId dimension;
    final NamespacedId documentId;
    final long baseRevision;
    final Sha256 baseSourceHash;
    final Sha256 initialRootHash;
    Sha256 draftRootHash;
    long ackCursor;
    Instant expiresAt;
    DraftLifecycle lifecycle;
    final Map<Long, DraftStore.Operation> operations;
    long contentBytes;

    MutableDraft(
            UUID draftId,
            MapKey mapKey,
            NamespacedId dimension,
            NamespacedId documentId,
            long baseRevision,
            Sha256 baseSourceHash,
            Sha256 initialRootHash,
            Sha256 draftRootHash,
            long ackCursor,
            Instant expiresAt,
            DraftLifecycle lifecycle,
            Map<Long, DraftStore.Operation> operations,
            long contentBytes
    ) {
        if (baseRevision < 0 || ackCursor < 0 || contentBytes < 0) {
            throw new IllegalArgumentException("Draft counters must be non-negative");
        }
        this.draftId = Objects.requireNonNull(draftId, "draftId");
        this.mapKey = Objects.requireNonNull(mapKey, "mapKey");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.documentId = Objects.requireNonNull(documentId, "documentId");
        this.baseRevision = baseRevision;
        this.baseSourceHash = Objects.requireNonNull(baseSourceHash, "baseSourceHash");
        this.initialRootHash = Objects.requireNonNull(
                initialRootHash, "initialRootHash"
        );
        this.draftRootHash = Objects.requireNonNull(draftRootHash, "draftRootHash");
        this.ackCursor = ackCursor;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.contentBytes = contentBytes;
    }

    DraftState snapshot() {
        return new DraftState(
                draftId, mapKey, dimension, documentId,
                baseRevision, baseSourceHash,
                draftRootHash, ackCursor, expiresAt
        );
    }

    DraftAck ack() {
        return new DraftAck(draftId, ackCursor, draftRootHash);
    }
}
