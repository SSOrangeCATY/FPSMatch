package com.phasetranscrystal.fpsmatch.core.minimap.editor.bake;

import java.util.Arrays;
import java.util.Objects;

public final class WorldSectionSnapshot {
    private final SectionCoord coord;
    private final long sectionRevision;
    private final boolean loaded;
    private final boolean stale;
    private final SnapshotPalette palette;
    private final byte[] blockIndices;
    private final short[] heights;
    private final byte[] light;
    private final int[] biomes;

    public WorldSectionSnapshot(
            SectionCoord coord,
            long sectionRevision,
            boolean loaded,
            SnapshotPalette palette,
            byte[] blockIndices,
            short[] heights,
            byte[] light,
            int[] biomes
    ) {
        this(coord, sectionRevision, loaded, false, palette, blockIndices, heights, light, biomes);
    }

    public WorldSectionSnapshot(
            SectionCoord coord,
            long sectionRevision,
            boolean loaded,
            boolean stale,
            SnapshotPalette palette,
            byte[] blockIndices,
            short[] heights,
            byte[] light,
            int[] biomes
    ) {
        this.coord = Objects.requireNonNull(coord, "coord");
        if (sectionRevision < 0) {
            throw new IllegalArgumentException("Section revision must be non-negative");
        }
        this.sectionRevision = sectionRevision;
        this.loaded = loaded;
        this.stale = stale;
        this.palette = Objects.requireNonNull(palette, "palette");
        this.blockIndices = Arrays.copyOf(Objects.requireNonNull(blockIndices, "blockIndices"), blockIndices.length);
        this.heights = Arrays.copyOf(Objects.requireNonNull(heights, "heights"), heights.length);
        this.light = Arrays.copyOf(Objects.requireNonNull(light, "light"), light.length);
        this.biomes = Arrays.copyOf(Objects.requireNonNull(biomes, "biomes"), biomes.length);
    }

    public SectionCoord coord() {
        return coord;
    }

    public long sectionRevision() {
        return sectionRevision;
    }

    public boolean loaded() {
        return loaded;
    }

    public boolean stale() {
        return stale;
    }

    public SnapshotPalette palette() {
        return palette;
    }

    public byte[] blockIndices() {
        return Arrays.copyOf(blockIndices, blockIndices.length);
    }

    public short[] heights() {
        return Arrays.copyOf(heights, heights.length);
    }

    public byte[] light() {
        return Arrays.copyOf(light, light.length);
    }

    public int[] biomes() {
        return Arrays.copyOf(biomes, biomes.length);
    }
}