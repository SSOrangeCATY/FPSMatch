package com.ptcrys.fpsmatch.core.minimap.editor.command;

import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record DraftSnapshot(
        UUID draftId,
        long ackCursor,
        Sha256 baseSourceHash,
        Sha256 draftRootHash,
        List<EditorOperation> operations
) {
    public DraftSnapshot {
        Objects.requireNonNull(draftId, "draftId");
        Objects.requireNonNull(baseSourceHash, "baseSourceHash");
        Objects.requireNonNull(draftRootHash, "draftRootHash");
        operations = List.copyOf(operations);
        if (ackCursor < 0) {
            throw new IllegalArgumentException("ACK cursor must be non-negative");
        }
    }

    public static DraftSnapshot fromLog(UUID draftId, long ackCursor, Sha256 baseSourceHash, EditorCommandLog log) {
        Objects.requireNonNull(log, "log");
        return new DraftSnapshot(
                draftId,
                ackCursor,
                baseSourceHash,
                log.rootHash(),
                log.currentState().operations()
        );
    }
}
