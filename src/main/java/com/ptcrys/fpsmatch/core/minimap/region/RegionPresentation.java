package com.ptcrys.fpsmatch.core.minimap.region;

import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.TextAppearance;

import java.util.Objects;

/**
 * Gameplay-extension fallback for a region semantic that has no authored
 * visual profile in the published runtime map.
 */
public record RegionPresentation(
        NamespacedId semanticType,
        NamespacedId styleId,
        TextAppearance label,
        double minVisibleScale,
        double maxVisibleScale
) {
    public RegionPresentation {
        Objects.requireNonNull(semanticType, "semanticType");
        Objects.requireNonNull(styleId, "styleId");
        Objects.requireNonNull(label, "label");
        if (!Double.isFinite(minVisibleScale) || !Double.isFinite(maxVisibleScale)
                || minVisibleScale < 0 || minVisibleScale > maxVisibleScale) {
            throw new IllegalArgumentException("Region visibility scale range is invalid");
        }
    }
}
