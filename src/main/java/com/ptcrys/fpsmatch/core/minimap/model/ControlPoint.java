package com.ptcrys.fpsmatch.core.minimap.model;

import java.util.Objects;

public record ControlPoint(WorldPoint2D world, CanvasPoint canvas) {
    public ControlPoint {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(canvas, "canvas");
    }
}
