package com.ptcrys.fpsmatch.core.minimap.extension;

import com.ptcrys.fpsmatch.core.minimap.marker.MinimapMarkerProvider;
import com.ptcrys.fpsmatch.core.minimap.marker.MinimapViewerContext;
import com.ptcrys.fpsmatch.core.minimap.marker.MinimapVisibilityPolicy;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Gameplay-side extension surface. Implementations live outside FPSMatch core packages.
 */
public interface MinimapGameplayExtension {
    String id();

    boolean supports(MapKey mapKey);

    default Optional<MinimapViewerContext> viewerContext(
            MapKey mapKey,
            UUID actorId
    ) {
        return Optional.empty();
    }

    default List<MinimapMarkerProvider> markerProviders(MapKey mapKey) {
        return List.of();
    }

    default List<MarkerPresentation> markerPresentations(MapKey mapKey) {
        return List.of();
    }

    default Optional<MinimapVisibilityPolicy> visibilityPolicy(MapKey mapKey) {
        return Optional.empty();
    }
}
