package com.ptcrys.fpsmatch.common.client.minimap.generated;

import com.ptcrys.fpsmatch.core.minimap.model.AffineTransform2D;
import com.ptcrys.fpsmatch.core.minimap.model.CanvasBounds;
import com.ptcrys.fpsmatch.core.minimap.model.CanvasPoint;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeFloor;
import com.ptcrys.fpsmatch.core.minimap.model.WorldPoint2D;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Maps a generated world chunk footprint into the active document tile grid. */
public final class GeneratedMinimapTileProjection {
    private static final int CHUNK_EDGE = 16;

    private GeneratedMinimapTileProjection() {
    }

    public static List<ProjectedTile> project(
            GeneratedMinimapTile tile,
            RuntimeFloor floor,
            CanvasBounds canvas,
            int tileEdge,
            int zoom
    ) {
        Objects.requireNonNull(tile, "tile");
        Objects.requireNonNull(floor, "floor");
        Objects.requireNonNull(canvas, "canvas");
        if (tileEdge <= 0 || zoom < 0 || zoom >= floor.zoomLevels()) {
            throw new IllegalArgumentException("Projection tile identity is invalid");
        }
        double span = Math.scalb((double) tileEdge, zoom);
        if (!Double.isFinite(span) || span <= 0.0) {
            return List.of();
        }
        double minWorldX = (double) tile.key().chunkX() * CHUNK_EDGE;
        double minWorldZ = (double) tile.key().chunkZ() * CHUNK_EDGE;
        double maxWorldX = minWorldX + CHUNK_EDGE;
        double maxWorldZ = minWorldZ + CHUNK_EDGE;
        AffineTransform2D transform = floor.worldToCanvas();
        CanvasPoint[] corners = {
                transform.transform(new WorldPoint2D(minWorldX, minWorldZ)),
                transform.transform(new WorldPoint2D(maxWorldX, minWorldZ)),
                transform.transform(new WorldPoint2D(minWorldX, maxWorldZ)),
                transform.transform(new WorldPoint2D(maxWorldX, maxWorldZ))
        };
        double minU = Double.POSITIVE_INFINITY;
        double minV = Double.POSITIVE_INFINITY;
        double maxU = Double.NEGATIVE_INFINITY;
        double maxV = Double.NEGATIVE_INFINITY;
        for (CanvasPoint corner : corners) {
            minU = Math.min(minU, corner.u());
            minV = Math.min(minV, corner.v());
            maxU = Math.max(maxU, corner.u());
            maxV = Math.max(maxV, corner.v());
        }
        if (!Double.isFinite(minU) || !Double.isFinite(minV)
                || !Double.isFinite(maxU) || !Double.isFinite(maxV)
                || maxU <= 0.0 || maxV <= 0.0
                || minU >= canvas.width() || minV >= canvas.height()) {
            return List.of();
        }

        int minTileX = floorIndex(minU, span);
        int minTileY = floorIndex(minV, span);
        int maxTileX = floorIndex(Math.nextDown(maxU), span);
        int maxTileY = floorIndex(Math.nextDown(maxV), span);
        int canvasMaxTileX = Math.max(
                0, (int) Math.ceil(canvas.width() / span) - 1
        );
        int canvasMaxTileY = Math.max(
                0, (int) Math.ceil(canvas.height() / span) - 1
        );
        maxTileX = Math.min(maxTileX, canvasMaxTileX);
        maxTileY = Math.min(maxTileY, canvasMaxTileY);
        ArrayList<ProjectedTile> result = new ArrayList<>();
        for (int tileY = Math.max(0, minTileY); tileY <= maxTileY; tileY++) {
            for (int tileX = Math.max(0, minTileX); tileX <= maxTileX; tileX++) {
                double cellMinU = tileX * span;
                double cellMinV = tileY * span;
                double cellMaxU = Math.min(canvas.width(), cellMinU + span);
                double cellMaxV = Math.min(canvas.height(), cellMinV + span);
                if (cellMaxU <= 0.0 || cellMaxV <= 0.0
                        || cellMinU >= canvas.width() || cellMinV >= canvas.height()) {
                    continue;
                }
                ContainerPath path = ContainerPath.parse(
                        "floors/" + floor.selection().id() + "/tiles/"
                                + zoom + "/" + tileX + "_" + tileY + ".png"
                );
                result.add(new ProjectedTile(
                        path, tileX, tileY,
                        Math.max(0.0, minU), Math.max(0.0, minV),
                        Math.min(canvas.width(), maxU),
                        Math.min(canvas.height(), maxV)
                ));
            }
        }
        return List.copyOf(result);
    }

    public static List<ProjectedTile> project(
            GeneratedMinimapTile tile,
            GeneratedMinimapRuntimeBinding binding,
            CanvasBounds canvas
    ) {
        Objects.requireNonNull(binding, "binding");
        return project(tile, binding.floor(), canvas, binding.tileEdge(), binding.zoom());
    }

    private static int floorIndex(double value, double span) {
        double quotient = Math.floor(value / span);
        if (quotient <= Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        if (quotient >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) quotient;
    }

    public record ProjectedTile(
            ContainerPath path,
            int tileX,
            int tileY,
            double minU,
            double minV,
            double maxU,
            double maxV
    ) {
        public ProjectedTile {
            Objects.requireNonNull(path, "path");
            if (!Double.isFinite(minU) || !Double.isFinite(minV)
                    || !Double.isFinite(maxU) || !Double.isFinite(maxV)
                    || maxU <= minU || maxV <= minV) {
                throw new IllegalArgumentException("Projected tile bounds are invalid");
            }
        }
    }
}
