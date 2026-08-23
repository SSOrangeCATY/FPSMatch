package com.ptcrys.fpsmatch.core.minimap.model;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapFormatContract;

import java.util.Objects;
import java.util.Optional;

public record LayerCommon(
        String id,
        DisplayLabel label,
        boolean visible,
        boolean locked,
        double opacity,
        BlendMode blendMode,
        Optional<CanvasRect> clip,
        boolean maskEnabled
) {
    public LayerCommon {
        if (!MinimapFormatContract.isInternalSlug(id)) {
            throw new IllegalArgumentException("Layer ID must be a valid internal slug");
        }
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(blendMode, "blendMode");
        clip = Objects.requireNonNull(clip, "clip");
        if (!Double.isFinite(opacity) || opacity < 0 || opacity > 1) {
            throw new IllegalArgumentException("Layer opacity must be in [0, 1]");
        }
    }
}
