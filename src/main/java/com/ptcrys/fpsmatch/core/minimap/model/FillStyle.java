package com.ptcrys.fpsmatch.core.minimap.model;

import java.util.Objects;

public record FillStyle(RgbaColor color, double opacity) {
    public FillStyle {
        Objects.requireNonNull(color, "color");
        requireUnit(opacity, "Fill opacity");
    }

    static void requireUnit(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(name + " must be in [0, 1]");
        }
    }
}
