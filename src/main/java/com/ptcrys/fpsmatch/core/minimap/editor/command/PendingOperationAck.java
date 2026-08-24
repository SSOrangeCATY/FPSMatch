package com.ptcrys.fpsmatch.core.minimap.editor.command;

import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.Objects;

/** The only state returned after a local pending-command acknowledgement. */
public record PendingOperationAck(long ackCursor, Sha256 draftRootHash) {
    public PendingOperationAck {
        if (ackCursor < 0) {
            throw new IllegalArgumentException("ACK cursor must be non-negative");
        }
        Objects.requireNonNull(draftRootHash, "draftRootHash");
    }
}
