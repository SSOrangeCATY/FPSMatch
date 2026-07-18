package com.phasetranscrystal.fpsmatch.core.minimap.editor.bake;

public record WorldSnapshotSectionStatus(
        SectionCoord coord,
        long sectionRevision,
        boolean loaded
) {
}
