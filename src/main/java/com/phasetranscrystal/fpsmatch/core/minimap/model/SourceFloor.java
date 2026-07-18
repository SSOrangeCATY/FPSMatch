package com.phasetranscrystal.fpsmatch.core.minimap.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record SourceFloor(
        MinimapFloor selection,
        DisplayLabel label,
        Optional<CanvasRect> contentBounds,
        FloorBackground background,
        FloorCalibration calibration,
        List<MinimapLayer> layers
) {
    public SourceFloor {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(label, "label");
        contentBounds = Objects.requireNonNull(contentBounds, "contentBounds");
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(calibration, "calibration");
        layers = List.copyOf(layers);
    }
}
