package com.ptcrys.fpsmatch.core.minimap.model;

public record CanvasPoint(double u, double v) {
    public CanvasPoint {
        requireFinite(u, "u");
        requireFinite(v, "v");
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Canvas point " + name + " must be finite");
        }
    }
}
