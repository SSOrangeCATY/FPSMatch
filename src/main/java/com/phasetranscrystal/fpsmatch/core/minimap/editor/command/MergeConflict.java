package com.phasetranscrystal.fpsmatch.core.minimap.editor.command;

import java.util.Objects;
import java.util.UUID;

public record MergeConflict(
        UUID id,
        Kind kind,
        String path,
        EditorOperation ours,
        EditorOperation theirs
) {
    public MergeConflict {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(ours, "ours");
        Objects.requireNonNull(theirs, "theirs");
    }

    public enum Kind {
        SAME_OBJECT,
        SAME_TILE
    }
}
