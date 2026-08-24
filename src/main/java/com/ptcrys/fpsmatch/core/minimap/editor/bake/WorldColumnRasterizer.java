package com.ptcrys.fpsmatch.core.minimap.editor.bake;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Deterministic top-down rasterizer for an immutable loaded column snapshot. */
public final class WorldColumnRasterizer {
    private final SamplerProfile profile;
    private final SampleDecision unknownBlockFallback;

    public WorldColumnRasterizer(SamplerProfile profile) {
        this(profile, SampleDecision.ignore());
    }

    /**
     * Creates a rasterizer with an optional opaque fallback for block IDs that
     * are not present in the sampler profile.  Authoring callers retain the
     * historical transparent default; the client runtime supplies a neutral
     * color so newly loaded modded blocks do not erase the minimap.
     */
    public WorldColumnRasterizer(SamplerProfile profile, int unknownBlockArgb) {
        this(profile, SampleDecision.color(unknownBlockArgb));
    }

    private WorldColumnRasterizer(
            SamplerProfile profile,
            SampleDecision unknownBlockFallback
    ) {
        this.profile = Objects.requireNonNull(profile, "profile");
        this.unknownBlockFallback = Objects.requireNonNull(
                unknownBlockFallback, "unknownBlockFallback"
        );
    }

    public RasterizedTile rasterize(WorldColumnSnapshot snapshot) {
        return rasterize(snapshot, snapshot.minY(), snapshot.maxYExclusive());
    }

    /** Rasterizes only the selected floor's integer Y interval. */
    public RasterizedTile rasterize(
            WorldColumnSnapshot snapshot,
            int floorMinY,
            int floorMaxYExclusive
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        int minY = Math.max(snapshot.minY(), floorMinY);
        int maxY = Math.min(snapshot.maxYExclusive(), floorMaxYExclusive);
        byte[] rgba = new byte[WorldColumnSnapshot.CHUNK_EDGE
                * WorldColumnSnapshot.CHUNK_EDGE * 4];
        if (!snapshot.loaded() || maxY <= minY) {
            return new RasterizedTile(
                    snapshot.chunkX(), snapshot.chunkZ(),
                    WorldColumnSnapshot.CHUNK_EDGE,
                    WorldColumnSnapshot.CHUNK_EDGE,
                    rgba
            );
        }
        for (int localZ = 0; localZ < WorldColumnSnapshot.CHUNK_EDGE; localZ++) {
            for (int localX = 0; localX < WorldColumnSnapshot.CHUNK_EDGE; localX++) {
                int argb = sampleTop(snapshot, localX, localZ, minY, maxY);
                int offset = (localZ * WorldColumnSnapshot.CHUNK_EDGE + localX) * 4;
                rgba[offset] = (byte) ((argb >>> 16) & 0xFF);
                rgba[offset + 1] = (byte) ((argb >>> 8) & 0xFF);
                rgba[offset + 2] = (byte) (argb & 0xFF);
                rgba[offset + 3] = (byte) ((argb >>> 24) & 0xFF);
            }
        }
        return new RasterizedTile(
                snapshot.chunkX(), snapshot.chunkZ(),
                WorldColumnSnapshot.CHUNK_EDGE,
                WorldColumnSnapshot.CHUNK_EDGE,
                rgba
        );
    }

    public byte[] rasterizeRgba(WorldColumnSnapshot snapshot) {
        return rasterize(snapshot).rgba();
    }

    private int sampleTop(
            WorldColumnSnapshot snapshot,
            int localX,
            int localZ,
            int minY,
            int maxYExclusive
    ) {
        for (int y = maxYExclusive - 1; y >= minY; y--) {
            String blockId = snapshot.palette().blockId(
                    snapshot.blockIndex(localX, y, localZ) & 0xFF
            );
            if (isAir(blockId)) {
                continue;
            }
            SampleDecision decision = profile.sampleOrDefault(
                    blockId, List.of(), null, unknownBlockFallback
            );
            if (decision.kind() == SampleDecision.Kind.COLOR) {
                return decision.argb();
            }
            if (decision.kind() == SampleDecision.Kind.TRANSPARENT) {
                // Transparent surfaces (for example water or glass) do not
                // occlude the terrain below them in a top-down minimap.
                continue;
            }
            // An ignored block is intentionally transparent.  We have found
            // the highest non-air block and must not reveal lower terrain.
            return 0;
        }
        return 0;
    }

    private static boolean isAir(String blockId) {
        return "minecraft:air".equals(blockId)
                || "minecraft:cave_air".equals(blockId)
                || "minecraft:void_air".equals(blockId);
    }

    public record RasterizedTile(
            int chunkX,
            int chunkZ,
            int width,
            int height,
            byte[] rgba
    ) {
        public RasterizedTile {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Raster dimensions must be positive");
            }
            Objects.requireNonNull(rgba, "rgba");
            if (rgba.length != width * height * 4) {
                throw new IllegalArgumentException("RGBA length does not match raster dimensions");
            }
            rgba = Arrays.copyOf(rgba, rgba.length);
        }

        @Override
        public byte[] rgba() {
            return Arrays.copyOf(rgba, rgba.length);
        }
    }
}
