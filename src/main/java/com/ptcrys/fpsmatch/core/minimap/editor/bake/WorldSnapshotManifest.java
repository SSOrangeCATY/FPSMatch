package com.ptcrys.fpsmatch.core.minimap.editor.bake;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record WorldSnapshotManifest(
        UUID snapshotId,
        List<WorldSnapshotSectionStatus> sections,
        int skippedUnloaded,
        long declaredBytes
) {
    public WorldSnapshotManifest {
        Objects.requireNonNull(snapshotId, "snapshotId");
        sections = List.copyOf(sections);
        if (skippedUnloaded < 0 || declaredBytes < 0) {
            throw new IllegalArgumentException("Snapshot manifest counters must be non-negative");
        }
    }
}
