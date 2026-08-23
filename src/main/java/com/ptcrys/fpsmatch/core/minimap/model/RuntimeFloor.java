package com.ptcrys.fpsmatch.core.minimap.model;

import java.util.Objects;
import java.util.Optional;

public record RuntimeFloor(
        MinimapFloor selection,
        DisplayLabel label,
        Optional<CanvasRect> contentBounds,
        AffineTransform2D worldToCanvas,
        int zoomLevels
) {
    public RuntimeFloor {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(label, "label");
        contentBounds = Objects.requireNonNull(contentBounds, "contentBounds");
        Objects.requireNonNull(worldToCanvas, "worldToCanvas");
        if (zoomLevels <= 0 || zoomLevels > 32) {
            throw new IllegalArgumentException("Runtime zoom level count must be in [1, 32]");
        }
    }

    public Vector2D northVector() {
        return worldToCanvas.northVector();
    }
}
