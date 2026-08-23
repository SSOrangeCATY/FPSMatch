package com.ptcrys.fpsmatch.core.minimap.editor.bake;

import java.util.Optional;

/**
 * Platform-neutral world read surface. 1.20.1 adapters must copy section data on the server thread
 * before handing immutable snapshots to worker rasterization.
 */
public interface WorldDataSource {
    boolean isSectionLoaded(SectionCoord coord);

    long sectionRevision(SectionCoord coord);

    Optional<WorldSectionSnapshot> copySection(SectionCoord coord, java.util.List<SnapshotChannelId> channels);
}
