package com.ptcrys.fpsmatch.common.client.minimap.generated;

import com.ptcrys.fpsmatch.core.minimap.model.CanvasBounds;
import com.ptcrys.fpsmatch.core.minimap.model.CanvasPoint;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeFloor;
import com.ptcrys.fpsmatch.core.minimap.model.WorldPoint2D;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Composes loaded chunk rasters into the document's tile grid without overwriting peers. */
public final class GeneratedMinimapTileComposer {
    private static final int CHUNK_EDGE = 16;

    private GeneratedMinimapTileComposer() {
    }

    public static List<ComposedTile> compose(
            List<GeneratedMinimapTile> sourceTiles,
            RuntimeFloor floor,
            CanvasBounds canvas,
            int tileEdge,
            int zoom,
            Set<String> staticTextureKeys
    ) {
        Objects.requireNonNull(sourceTiles, "sourceTiles");
        Objects.requireNonNull(floor, "floor");
        Objects.requireNonNull(canvas, "canvas");
        Objects.requireNonNull(staticTextureKeys, "staticTextureKeys");
        if (tileEdge <= 0 || zoom < 0 || zoom >= floor.zoomLevels()) {
            throw new IllegalArgumentException("Generated composition identity is invalid");
        }
        double scale = Math.scalb(1.0, zoom);
        double span = tileEdge * scale;
        int scaledCanvasWidth = scaledCanvasDimension(canvas.width(), zoom);
        int scaledCanvasHeight = scaledCanvasDimension(canvas.height(), zoom);
        Map<String, MutableTile> composed = new LinkedHashMap<>();
        for (GeneratedMinimapTile source : sourceTiles) {
            Objects.requireNonNull(source, "source tile");
            for (GeneratedMinimapTileProjection.ProjectedTile projected
                    : GeneratedMinimapTileProjection.project(
                            source, floor, canvas, tileEdge, zoom)) {
                String textureKey = projected.path().value();
                if (staticTextureKeys.contains(textureKey)) {
                    continue;
                }
                MutableTile target = composed.computeIfAbsent(
                        textureKey,
                        ignored -> new MutableTile(
                                textureKey,
                                tileWidth(scaledCanvasWidth, tileEdge, projected.tileX()),
                                tileHeight(scaledCanvasHeight, tileEdge, projected.tileY())
                        )
                );
                target.addSource(source);
                copyVisiblePixels(
                        target,
                        source,
                        projected,
                        floor,
                        span,
                        scale
                );
            }
        }
        return composed.values().stream()
                .map(MutableTile::freeze)
                .toList();
    }

    private static void copyVisiblePixels(
            MutableTile target,
            GeneratedMinimapTile source,
            GeneratedMinimapTileProjection.ProjectedTile projected,
            RuntimeFloor floor,
            double span,
            double scale
    ) {
        double chunkMinX = (double) source.key().chunkX() * CHUNK_EDGE;
        double chunkMinZ = (double) source.key().chunkZ() * CHUNK_EDGE;
        double chunkMaxX = chunkMinX + CHUNK_EDGE;
        double chunkMaxZ = chunkMinZ + CHUNK_EDGE;
        byte[] pixels = source.rgba();
        for (int pixelY = 0; pixelY < target.height; pixelY++) {
            double cellMinV = projected.tileY() * span + pixelY * scale;
            double cellMaxV = cellMinV + scale;
            double sampleMinV = Math.max(cellMinV, projected.minV());
            double sampleMaxV = Math.min(cellMaxV, projected.maxV());
            if (sampleMaxV <= sampleMinV) {
                continue;
            }
            double canvasV = sampleMinV + (sampleMaxV - sampleMinV) * 0.5;
            for (int pixelX = 0; pixelX < target.width; pixelX++) {
                double cellMinU = projected.tileX() * span + pixelX * scale;
                double cellMaxU = cellMinU + scale;
                double sampleMinU = Math.max(cellMinU, projected.minU());
                double sampleMaxU = Math.min(cellMaxU, projected.maxU());
                if (sampleMaxU <= sampleMinU) {
                    continue;
                }
                double canvasU = sampleMinU + (sampleMaxU - sampleMinU) * 0.5;
                WorldPoint2D world = floor.worldToCanvas().inverseTransform(
                        new CanvasPoint(canvasU, canvasV)
                );
                if (world.x() < chunkMinX || world.x() >= chunkMaxX
                        || world.z() < chunkMinZ || world.z() >= chunkMaxZ) {
                    continue;
                }
                int sourceX = clamp(
                        (int) Math.floor(world.x() - chunkMinX), source.width()
                );
                int sourceZ = clamp(
                        (int) Math.floor(world.z() - chunkMinZ), source.height()
                );
                int sourceOffset = (sourceZ * source.width() + sourceX) * 4;
                int alpha = pixels[sourceOffset + 3] & 0xFF;
                if (alpha == 0) {
                    continue;
                }
                int destinationOffset = (pixelY * target.width + pixelX) * 4;
                if ((target.rgba[destinationOffset + 3] & 0xFF) == 0) {
                    System.arraycopy(pixels, sourceOffset, target.rgba, destinationOffset, 4);
                }
            }
        }
    }

    private static int scaledCanvasDimension(int canvasEdge, int zoom) {
        long scale = 1L << zoom;
        return (int) ((canvasEdge + scale - 1L) / scale);
    }

    private static int tileWidth(int scaledCanvasWidth, int tileEdge, int tileX) {
        return tileDimension(scaledCanvasWidth, tileEdge, tileX);
    }

    private static int tileHeight(int scaledCanvasHeight, int tileEdge, int tileY) {
        return tileDimension(scaledCanvasHeight, tileEdge, tileY);
    }

    private static int tileDimension(int scaledCanvasEdge, int tileEdge, int tileCoordinate) {
        long remaining = (long) scaledCanvasEdge - (long) tileCoordinate * tileEdge;
        if (remaining <= 0L) {
            throw new IllegalArgumentException("Projected tile is outside the scaled canvas");
        }
        return (int) Math.min(tileEdge, remaining);
    }

    private static int clamp(int value, int size) {
        return Math.max(0, Math.min(size - 1, value));
    }

    public record ComposedTile(
            String textureKey,
            int width,
            int height,
            byte[] rgba,
            long signature
    ) {
        public ComposedTile {
            Objects.requireNonNull(textureKey, "textureKey");
            Objects.requireNonNull(rgba, "rgba");
            if (textureKey.isBlank() || width <= 0 || height <= 0
                    || rgba.length != width * height * 4) {
                throw new IllegalArgumentException("Composed tile dimensions are invalid");
            }
            rgba = Arrays.copyOf(rgba, rgba.length);
        }

        @Override
        public byte[] rgba() {
            return Arrays.copyOf(rgba, rgba.length);
        }
    }

    private static final class MutableTile {
        private final String textureKey;
        private final int width;
        private final int height;
        private final byte[] rgba;
        private final Map<String, Long> sources = new LinkedHashMap<>();

        private MutableTile(String textureKey, int width, int height) {
            this.textureKey = textureKey;
            this.width = width;
            this.height = height;
            this.rgba = new byte[width * height * 4];
        }

        private void addSource(GeneratedMinimapTile source) {
            sources.put(source.key().toString(),
                    (long) Arrays.hashCode(source.rgba()));
        }

        private ComposedTile freeze() {
            long signature = 0xcbf29ce484222325L;
            for (Map.Entry<String, Long> source : sources.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                signature ^= source.getKey().hashCode();
                signature *= 0x100000001b3L;
                signature ^= source.getValue();
                signature *= 0x100000001b3L;
            }
            return new ComposedTile(
                    textureKey, width, height, rgba, signature
            );
        }
    }
}
