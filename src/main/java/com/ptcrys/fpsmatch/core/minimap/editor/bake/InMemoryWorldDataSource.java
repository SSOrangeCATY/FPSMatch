package com.ptcrys.fpsmatch.core.minimap.editor.bake;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class InMemoryWorldDataSource implements WorldDataSource {
    private final Set<SectionCoord> loaded;
    private final long baseRevision;

    public InMemoryWorldDataSource(Set<SectionCoord> loaded, long baseRevision) {
        this.loaded = Set.copyOf(Objects.requireNonNull(loaded, "loaded"));
        this.baseRevision = baseRevision;
    }

    @Override
    public boolean isSectionLoaded(SectionCoord coord) {
        return loaded.contains(coord);
    }

    @Override
    public long sectionRevision(SectionCoord coord) {
        return baseRevision + (isSectionLoaded(coord) ? 1L : 0L);
    }

    @Override
    public Optional<WorldSectionSnapshot> copySection(SectionCoord coord, List<SnapshotChannelId> channels) {
        if (!isSectionLoaded(coord)) {
            return Optional.empty();
        }
        return Optional.of(new WorldSectionSnapshot(
                coord,
                sectionRevision(coord),
                true,
                new SnapshotPalette(List.of("minecraft:air", "minecraft:stone")),
                new byte[] {0, 1, 1, 0},
                new short[] {64, 64, 64, 64},
                new byte[] {15, 15, 15, 15},
                new int[] {1, 1, 1, 1}
        ));
    }
}
