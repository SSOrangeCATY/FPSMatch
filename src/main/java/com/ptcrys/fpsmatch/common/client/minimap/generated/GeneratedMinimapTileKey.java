package com.ptcrys.fpsmatch.common.client.minimap.generated;

import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;

import java.util.Objects;

/** Cache identity for one generated chunk tile. */
public record GeneratedMinimapTileKey(
        NamespacedId dimension,
        long worldEpoch,
        int chunkX,
        int chunkZ,
        long chunkRevision
) {
    public GeneratedMinimapTileKey {
        Objects.requireNonNull(dimension, "dimension");
        if (worldEpoch < 0 || chunkRevision < 0) {
            throw new IllegalArgumentException("Generated tile counters must be non-negative");
        }
    }

    public GeneratedWorldIdentity world() {
        return new GeneratedWorldIdentity(dimension, worldEpoch);
    }

    public boolean sameChunk(GeneratedMinimapTileKey other) {
        return other != null
                && dimension.equals(other.dimension)
                && worldEpoch == other.worldEpoch
                && chunkX == other.chunkX
                && chunkZ == other.chunkZ;
    }
}
