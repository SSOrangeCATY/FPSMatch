package com.phasetranscrystal.fpsmatch.core.minimap.model;

public sealed interface RegionGeometry permits RectangleGeometry, PolygonGeometry {
    GeometryType type();

    boolean contains(CanvasPoint point);
}
