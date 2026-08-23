package com.ptcrys.fpsmatch.core.minimap.model;

public record WorldPoint2D(double x, double z) {
    public WorldPoint2D {
        requireFinite(x, "x");
        requireFinite(z, "z");
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("World point " + name + " must be finite");
        }
    }
}
