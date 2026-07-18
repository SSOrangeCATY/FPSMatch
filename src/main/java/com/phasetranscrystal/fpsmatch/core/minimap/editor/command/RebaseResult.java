package com.phasetranscrystal.fpsmatch.core.minimap.editor.command;

import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;

import java.util.List;
import java.util.Objects;

public record RebaseResult(
        Sha256 baseRootHash,
        List<EditorOperation> mergedOperations,
        List<MergeConflict> conflicts,
        Sha256 mergedRootHash
) {
    public RebaseResult {
        Objects.requireNonNull(baseRootHash, "baseRootHash");
        mergedOperations = List.copyOf(mergedOperations);
        conflicts = List.copyOf(conflicts);
        Objects.requireNonNull(mergedRootHash, "mergedRootHash");
    }
}
