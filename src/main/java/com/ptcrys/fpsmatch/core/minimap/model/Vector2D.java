package com.ptcrys.fpsmatch.core.minimap.model;

public record Vector2D(double x, double y) {
    public Vector2D {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Vector components must be finite");
        }
    }

    public double length() {
        return Math.hypot(x, y);
    }

    public Vector2D normalized() {
        double length = length();
        if (length == 0.0) {
            throw new IllegalStateException("Cannot normalize a zero-length vector");
        }
        return new Vector2D(x / length, y / length);
    }
}
