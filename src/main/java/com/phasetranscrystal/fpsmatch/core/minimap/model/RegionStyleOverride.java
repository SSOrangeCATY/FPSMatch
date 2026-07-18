package com.phasetranscrystal.fpsmatch.core.minimap.model;

import java.util.Objects;
import java.util.Optional;

public record RegionStyleOverride(
        Optional<FillStyle> fill,
        Optional<StrokeStyle> stroke,
        Optional<TextAppearance> label
) {
    public RegionStyleOverride {
        fill = Objects.requireNonNull(fill, "fill");
        stroke = Objects.requireNonNull(stroke, "stroke");
        label = Objects.requireNonNull(label, "label");
    }

    public static RegionStyleOverride empty() {
        return new RegionStyleOverride(Optional.empty(), Optional.empty(), Optional.empty());
    }
}
