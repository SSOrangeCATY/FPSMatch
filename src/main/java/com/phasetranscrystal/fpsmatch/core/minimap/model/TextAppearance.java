package com.phasetranscrystal.fpsmatch.core.minimap.model;

import java.util.Objects;

public record TextAppearance(RgbaColor color, double scale) {
    public TextAppearance {
        Objects.requireNonNull(color, "color");
        if (!Double.isFinite(scale) || scale <= 0 || scale > 64) {
            throw new IllegalArgumentException("Text scale must be in (0, 64]");
        }
    }
}
