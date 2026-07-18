package com.phasetranscrystal.fpsmatch.core.minimap.editor.raster;

import java.util.List;
import java.util.Objects;

public final class PolygonSelection implements SelectionMask {
    private final List<IntPoint> vertices;

    public PolygonSelection(List<IntPoint> vertices) {
        Objects.requireNonNull(vertices, "vertices");
        if (vertices.size() < 3) {
            throw new IllegalArgumentException("Polygon selection requires at least 3 vertices");
        }
        this.vertices = List.copyOf(vertices);
    }

    @Override
    public boolean contains(int x, int y) {
        // Inclusive boundary + even-odd fill.
        boolean inside = false;
        int count = vertices.size();
        for (int index = 0, previous = count - 1; index < count; previous = index++) {
            IntPoint a = vertices.get(previous);
            IntPoint b = vertices.get(index);
            if (onSegment(a, b, x, y)) {
                return true;
            }
            boolean intersect = ((a.y() > y) != (b.y() > y))
                    && (x < (long) (b.x() - a.x()) * (y - a.y()) / (double) (b.y() - a.y()) + a.x());
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static boolean onSegment(IntPoint a, IntPoint b, int x, int y) {
        long cross = (long) (b.x() - a.x()) * (y - a.y()) - (long) (b.y() - a.y()) * (x - a.x());
        if (cross != 0) {
            return false;
        }
        return x >= Math.min(a.x(), b.x())
                && x <= Math.max(a.x(), b.x())
                && y >= Math.min(a.y(), b.y())
                && y <= Math.max(a.y(), b.y());
    }
}
