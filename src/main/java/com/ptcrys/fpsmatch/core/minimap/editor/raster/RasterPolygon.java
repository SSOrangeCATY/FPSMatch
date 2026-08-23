package com.ptcrys.fpsmatch.core.minimap.editor.raster;

import java.util.List;
import java.util.Objects;

public final class RasterPolygon {
    public void drawFilled(RasterSurface surface, List<IntPoint> vertices, int rgba) {
        Objects.requireNonNull(surface, "surface");
        SelectionMask mask = new PolygonSelection(vertices);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (IntPoint point : vertices) {
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
        }
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (mask.contains(x, y)
                        && x >= 0 && y >= 0
                        && x < surface.width() && y < surface.height()) {
                    surface.setPixel(x, y, rgba);
                }
            }
        }
    }
}
