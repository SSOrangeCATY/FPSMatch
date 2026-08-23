package com.ptcrys.fpsmatch.core.minimap.model;

import java.util.Objects;

public record StrokeStyle(RgbaColor color, double width, double opacity) {
    public StrokeStyle {
        Objects.requireNonNull(color, "color");
        if (!Double.isFinite(width) || width < 0 || width > 1_024) {
            throw new IllegalArgumentException("Stroke width must be in [0, 1024]");
        }
        FillStyle.requireUnit(opacity, "Stroke opacity");
    }
}
