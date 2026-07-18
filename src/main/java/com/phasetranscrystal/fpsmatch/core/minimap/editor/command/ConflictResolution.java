package com.phasetranscrystal.fpsmatch.core.minimap.editor.command;

import java.util.Objects;
import java.util.UUID;

public record ConflictResolution(UUID conflictId, Choice choice) {
    public ConflictResolution {
        Objects.requireNonNull(conflictId, "conflictId");
        Objects.requireNonNull(choice, "choice");
    }

    public static ConflictResolution keepOurs(UUID conflictId) {
        return new ConflictResolution(conflictId, Choice.KEEP_OURS);
    }

    public static ConflictResolution keepTheirs(UUID conflictId) {
        return new ConflictResolution(conflictId, Choice.KEEP_THEIRS);
    }

    public enum Choice {
        KEEP_OURS,
        KEEP_THEIRS
    }
}
