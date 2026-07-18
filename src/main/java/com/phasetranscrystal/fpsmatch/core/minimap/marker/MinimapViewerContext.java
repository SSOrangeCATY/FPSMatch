package com.phasetranscrystal.fpsmatch.core.minimap.marker;

import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;

import java.util.Objects;
import java.util.Optional;

public record MinimapViewerContext(
        ViewerRole role,
        String teamId,
        Optional<NamespacedId> selfMarkerId,
        boolean teamSharedIntelEnabled,
        boolean observerOmniscient
) {
    public MinimapViewerContext {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(selfMarkerId, "selfMarkerId");
    }
}