package com.phasetranscrystal.fpsmatch.common.minimap.server.sync;

import com.phasetranscrystal.fpsmatch.core.minimap.marker.MarkerSnapshot;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.MinimapViewerContext;

import java.util.List;
import java.util.Objects;

public record RuntimeMarkerSnapshot(
        MinimapViewerContext viewer,
        MarkerSnapshot markerSnapshot
) {
    public RuntimeMarkerSnapshot(
            MinimapViewerContext viewer,
            List<MarkerSnapshot.Marker> markers
    ) {
        this(viewer, MarkerSnapshot.of(Objects.requireNonNull(markers, "markers")));
    }

    public RuntimeMarkerSnapshot {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(markerSnapshot, "markerSnapshot");
    }

    public List<MarkerSnapshot.Marker> markers() {
        return markerSnapshot.markers();
    }
}
