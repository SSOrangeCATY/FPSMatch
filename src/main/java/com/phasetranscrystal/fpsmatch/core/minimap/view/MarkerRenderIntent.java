package com.phasetranscrystal.fpsmatch.core.minimap.view;

import com.phasetranscrystal.fpsmatch.core.minimap.marker.MarkerSnapshot;

import java.util.Objects;

public record MarkerRenderIntent(MarkerSnapshot.Marker marker, AdjacentFloorStyle adjacentStyle) {
    public MarkerRenderIntent {
        Objects.requireNonNull(marker, "marker");
        Objects.requireNonNull(adjacentStyle, "adjacentStyle");
    }
}