package com.phasetranscrystal.fpsmatch.core.minimap.editor.command;

import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;

import java.util.Objects;

public record EditorCommand(
        long sequence,
        Sha256 baseRootHash,
        Sha256 resultingRootHash,
        EditorOperation operation
) {
    public EditorCommand {
        if (sequence <= 0) {
            throw new IllegalArgumentException("Command sequence must be positive");
        }
        Objects.requireNonNull(baseRootHash, "baseRootHash");
        Objects.requireNonNull(resultingRootHash, "resultingRootHash");
        Objects.requireNonNull(operation, "operation");
    }
}
