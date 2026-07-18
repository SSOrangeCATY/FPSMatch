package com.phasetranscrystal.fpsmatch.core.minimap.marker;

import java.util.List;

@FunctionalInterface
public interface MinimapVisibilityPolicy {
    List<MarkerSnapshot.Marker> filter(MinimapViewerContext context, List<MarkerCandidate> candidates);
}