package com.ptcrys.fpsmatch.core.minimap.editor.bake;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public record DirtyBakeJob(SectionCoord section, long sectionRevision) {
    public DirtyBakeJob {
        Objects.requireNonNull(section, "section");
        if (sectionRevision < 0) {
            throw new IllegalArgumentException("Section revision must be non-negative");
        }
    }

    public Set<TileCoord> intersectingTiles(int sectionBlockEdge, int tileEdge, int originTileX, int originTileY) {
        if (sectionBlockEdge <= 0 || tileEdge <= 0) {
            throw new IllegalArgumentException("Edges must be positive");
        }
        int blocksX = section.sectionX() * sectionBlockEdge;
        int blocksZ = section.sectionZ() * sectionBlockEdge;
        int minTileX = Math.floorDiv(blocksX, tileEdge) + originTileX;
        int minTileY = Math.floorDiv(blocksZ, tileEdge) + originTileY;
        int maxTileX = Math.floorDiv(blocksX + sectionBlockEdge - 1, tileEdge) + originTileX;
        int maxTileY = Math.floorDiv(blocksZ + sectionBlockEdge - 1, tileEdge) + originTileY;
        Set<TileCoord> tiles = new HashSet<>();
        for (int y = minTileY; y <= maxTileY; y++) {
            for (int x = minTileX; x <= maxTileX; x++) {
                tiles.add(new TileCoord(x, y));
            }
        }
        return Set.copyOf(tiles);
    }
}
