package com.ptcrys.fpsmatch.core.minimap.marker;

import java.util.List;

@FunctionalInterface
public interface MinimapMarkerProvider {
    List<MarkerCandidate> collect(MinimapViewerContext context);
}