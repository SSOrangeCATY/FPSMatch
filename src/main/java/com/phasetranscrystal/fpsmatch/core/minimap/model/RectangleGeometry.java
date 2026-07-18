package com.phasetranscrystal.fpsmatch.core.minimap.model;

import java.util.Objects;

public record RectangleGeometry(CanvasRect bounds) implements RegionGeometry {
    public RectangleGeometry {
        Objects.requireNonNull(bounds, "bounds");
    }

    @Override
    public GeometryType type() {
        return GeometryType.RECTANGLE;
    }

    @Override
    public boolean contains(CanvasPoint point) {
        return point.u() >= bounds.minU() && point.u() <= bounds.maxU()
                && point.v() >= bounds.minV() && point.v() <= bounds.maxV();
    }
}
