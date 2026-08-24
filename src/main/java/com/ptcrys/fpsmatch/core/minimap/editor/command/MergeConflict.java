package com.ptcrys.fpsmatch.core.minimap.editor.command;

import java.util.Objects;
import java.util.List;
import java.util.UUID;

public record MergeConflict(
        UUID id,
        Kind kind,
        String path,
        List<EditorOperation> oursOperations,
        List<EditorOperation> theirsOperations
) {
    public MergeConflict(
            UUID id,
            Kind kind,
            String path,
            EditorOperation ours,
            EditorOperation theirs
    ) {
        this(id, kind, path, List.of(ours), List.of(theirs));
    }

    public MergeConflict {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(path, "path");
        oursOperations = List.copyOf(oursOperations);
        theirsOperations = List.copyOf(theirsOperations);
        if (oursOperations.isEmpty() || theirsOperations.isEmpty()) {
            throw new IllegalArgumentException("Merge conflicts require both operation sequences");
        }
    }

    public EditorOperation ours() {
        return oursOperations.get(oursOperations.size() - 1);
    }

    public EditorOperation theirs() {
        return theirsOperations.get(theirsOperations.size() - 1);
    }

    public enum Kind {
        SAME_OBJECT,
        SAME_TILE
    }
}
