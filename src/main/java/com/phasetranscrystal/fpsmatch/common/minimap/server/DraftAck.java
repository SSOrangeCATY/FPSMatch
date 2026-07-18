package com.phasetranscrystal.fpsmatch.common.minimap.server;

import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;

import java.util.Objects;
import java.util.UUID;

public record DraftAck(UUID draftId, long ackCursor, Sha256 draftRootHash) {
    public DraftAck {
        Objects.requireNonNull(draftId, "draftId");
        Objects.requireNonNull(draftRootHash, "draftRootHash");
        if (ackCursor < 0) {
            throw new IllegalArgumentException("Draft ACK cursor must be non-negative");
        }
    }
}
