package com.phasetranscrystal.fpsmatch.core.minimap.view;

import com.phasetranscrystal.fpsmatch.core.minimap.marker.MarkerSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MarkerFloorFilter {
    private MarkerFloorFilter() {
    }

    public static List<MarkerRenderIntent> filter(
            String activeFloorId,
            List<MarkerSnapshot.Marker> markers,
            boolean allowAdjacentStyle
    ) {
        Objects.requireNonNull(activeFloorId, "activeFloorId");
        Objects.requireNonNull(markers, "markers");
        List<MarkerRenderIntent> intents = new ArrayList<>();
        for (MarkerSnapshot.Marker marker : markers) {
            String floor = marker.floorSlug().orElse(activeFloorId);
            if (floor.equals(activeFloorId)) {
                intents.add(new MarkerRenderIntent(marker, AdjacentFloorStyle.NONE));
                continue;
            }
            if (!allowAdjacentStyle) {
                continue;
            }
            AdjacentFloorStyle style = styleFor(activeFloorId, floor);
            if (style != AdjacentFloorStyle.NONE) {
                intents.add(new MarkerRenderIntent(marker, style));
            }
        }
        return List.copyOf(intents);
    }

    private static AdjacentFloorStyle styleFor(String active, String other) {
        // Stable generic adjacency heuristic by lexicographic neighbor names when unknown topology.
        int cmp = other.compareTo(active);
        if (Math.abs(cmp) == 0) {
            return AdjacentFloorStyle.NONE;
        }
        // Treat "upper" as above ground and "roof" as non-adjacent far floor for fixture.
        if ("ground".equals(active) && "upper".equals(other)) {
            return AdjacentFloorStyle.ABOVE;
        }
        if ("upper".equals(active) && "ground".equals(other)) {
            return AdjacentFloorStyle.BELOW;
        }
        return AdjacentFloorStyle.NONE;
    }
}