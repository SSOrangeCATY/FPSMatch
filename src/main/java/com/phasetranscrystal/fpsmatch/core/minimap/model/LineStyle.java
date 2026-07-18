package com.phasetranscrystal.fpsmatch.core.minimap.model;

import java.util.Objects;

public record LineStyle(NamespacedId id, StrokeStyle stroke) implements MinimapStyle {
    public LineStyle {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(stroke, "stroke");
    }

    @Override
    public StyleType type() {
        return StyleType.LINE;
    }
}
