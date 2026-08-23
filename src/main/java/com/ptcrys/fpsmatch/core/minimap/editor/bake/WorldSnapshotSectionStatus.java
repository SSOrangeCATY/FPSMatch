package com.ptcrys.fpsmatch.core.minimap.editor.bake;

public record WorldSnapshotSectionStatus(
        SectionCoord coord,
        long sectionRevision,
        boolean loaded
) {
}
