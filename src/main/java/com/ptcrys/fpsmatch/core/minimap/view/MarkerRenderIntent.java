package com.ptcrys.fpsmatch.core.minimap.view;

import com.ptcrys.fpsmatch.core.minimap.marker.MarkerSnapshot;

import java.util.Objects;

public record MarkerRenderIntent(MarkerSnapshot.Marker marker, AdjacentFloorStyle adjacentStyle) {
    public MarkerRenderIntent {
        Objects.requireNonNull(marker, "marker");
        Objects.requireNonNull(adjacentStyle, "adjacentStyle");
    }
}