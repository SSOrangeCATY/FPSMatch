package com.phasetranscrystal.fpsmatch.core.minimap.model;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RuntimeRegion(
        String id,
        String floorId,
        DisplayLabel label,
        RegionGeometry geometry,
        NamespacedId semanticType,
        List<NamespacedId> tags,
        Optional<NamespacedId> gameplayReference,
        NamespacedId styleId,
        CanvasPoint labelAnchor,
        int priority,
        double minVisibleScale,
        double maxVisibleScale
) {
    public RuntimeRegion {
        if (!MinimapFormatContract.isInternalSlug(id)
                || !MinimapFormatContract.isInternalSlug(floorId)) {
            throw new IllegalArgumentException("Region and floor IDs must be valid internal slugs");
        }
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(semanticType, "semanticType");
        tags = List.copyOf(tags);
        if (new HashSet<>(tags).size() != tags.size()) {
            throw new IllegalArgumentException("Region tags must be unique");
        }
        gameplayReference = Objects.requireNonNull(gameplayReference, "gameplayReference");
        Objects.requireNonNull(styleId, "styleId");
        Objects.requireNonNull(labelAnchor, "labelAnchor");
        if (!Double.isFinite(minVisibleScale) || !Double.isFinite(maxVisibleScale)
                || minVisibleScale < 0 || minVisibleScale > maxVisibleScale) {
            throw new IllegalArgumentException("Region visibility scale range is invalid");
        }
    }
}
