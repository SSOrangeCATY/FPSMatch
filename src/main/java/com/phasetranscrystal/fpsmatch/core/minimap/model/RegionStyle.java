package com.phasetranscrystal.fpsmatch.core.minimap.model;

import java.util.Objects;

public record RegionStyle(
        NamespacedId id,
        FillStyle fill,
        StrokeStyle stroke,
        TextAppearance label
) implements MinimapStyle {
    public RegionStyle {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fill, "fill");
        Objects.requireNonNull(stroke, "stroke");
        Objects.requireNonNull(label, "label");
    }

    @Override
    public StyleType type() {
        return StyleType.REGION;
    }
}
