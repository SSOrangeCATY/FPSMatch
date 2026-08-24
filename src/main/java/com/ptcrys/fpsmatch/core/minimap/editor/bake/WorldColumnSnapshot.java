package com.ptcrys.fpsmatch.core.minimap.editor.bake;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable client-side copy of one loaded 16x16 chunk column.
 *
 * <p>The backing array is laid out as {@code y, z, x}.  Minecraft objects are
 * copied into this value on the client thread and this class is the only value
 * that is allowed to cross into a background rasterization task.</p>
 */
public final class WorldColumnSnapshot {
    public static final int CHUNK_EDGE = 16;

    private final int chunkX;
    private final int chunkZ;
    private final long chunkRevision;
    private final boolean loaded;
    private final int minY;
    private final int maxYExclusive;
    private final SnapshotPalette palette;
    private final byte[] blockIndices;

    public WorldColumnSnapshot(
            int chunkX,
            int chunkZ,
            long chunkRevision,
            boolean loaded,
            int minY,
            int maxYExclusive,
            SnapshotPalette palette,
            byte[] blockIndices
    ) {
        if (chunkRevision < 0) {
            throw new IllegalArgumentException("Chunk revision must be non-negative");
        }
        if (maxYExclusive <= minY) {
            throw new IllegalArgumentException("Column height must be positive");
        }
        Objects.requireNonNull(palette, "palette");
        Objects.requireNonNull(blockIndices, "blockIndices");
        long expected = (long) (maxYExclusive - minY)
                * CHUNK_EDGE * CHUNK_EDGE;
        if (expected > Integer.MAX_VALUE || blockIndices.length != expected) {
            throw new IllegalArgumentException(
                    "Column block array must contain " + expected + " entries"
            );
        }
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.chunkRevision = chunkRevision;
        this.loaded = loaded;
        this.minY = minY;
        this.maxYExclusive = maxYExclusive;
        this.palette = palette;
        this.blockIndices = Arrays.copyOf(blockIndices, blockIndices.length);
    }

    /** Convenience constructor for a single top-down plane. */
    public WorldColumnSnapshot(
            int chunkX,
            int chunkZ,
            long chunkRevision,
            boolean loaded,
            SnapshotPalette palette,
            byte[] topDownBlockIndices
    ) {
        this(
                chunkX,
                chunkZ,
                chunkRevision,
                loaded,
                0,
                1,
                palette,
                topDownBlockIndices
        );
    }

    public static WorldColumnSnapshot unloaded(
            int chunkX,
            int chunkZ,
            long chunkRevision,
            SnapshotPalette palette,
            int minY,
            int maxYExclusive
    ) {
        return new WorldColumnSnapshot(
                chunkX,
                chunkZ,
                chunkRevision,
                false,
                minY,
                maxYExclusive,
                palette,
                new byte[(maxYExclusive - minY) * CHUNK_EDGE * CHUNK_EDGE]
        );
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public long chunkRevision() {
        return chunkRevision;
    }

    /** Alias used by callers that refer to revisions as simply {@code revision}. */
    public long revision() {
        return chunkRevision;
    }

    public boolean loaded() {
        return loaded;
    }

    public int minY() {
        return minY;
    }

    public int maxYExclusive() {
        return maxYExclusive;
    }

    public int height() {
        return maxYExclusive - minY;
    }

    public SnapshotPalette palette() {
        return palette;
    }

    public byte blockIndex(int localX, int y, int localZ) {
        if (localX < 0 || localX >= CHUNK_EDGE
                || localZ < 0 || localZ >= CHUNK_EDGE
                || y < minY || y >= maxYExclusive) {
            throw new IndexOutOfBoundsException(
                    "Column sample outside chunk bounds: "
                            + localX + "," + y + "," + localZ
            );
        }
        int vertical = y - minY;
        return blockIndices[(vertical * CHUNK_EDGE + localZ) * CHUNK_EDGE + localX];
    }

    public byte[] blockIndices() {
        return Arrays.copyOf(blockIndices, blockIndices.length);
    }
}
