package com.ptcrys.fpsmatch.core.minimap.region;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapFormatContract;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Pure runtime region view for providers. Independent of AreaData / GuiGraphics / DFU codecs.
 */
public record RuntimeRegionDescriptor(
        String id,
        String floorId,
        String label,
        String semanticType,
        List<String> tags,
        Optional<String> gameplayReference,
        WorldAxisAlignedBounds worldBounds,
        int priority
) {
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public RuntimeRegionDescriptor {
        if (!MinimapFormatContract.isInternalSlug(id) || !MinimapFormatContract.isInternalSlug(floorId)) {
            throw new IllegalArgumentException("Region/floor ids must be internal slugs");
        }
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(semanticType, "semanticType");
        if (!RESOURCE_ID.matcher(semanticType).matches()) {
            throw new IllegalArgumentException("semanticType must be a namespaced id string");
        }
        tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
        for (String tag : tags) {
            if (!RESOURCE_ID.matcher(tag).matches()) {
                throw new IllegalArgumentException("tag must be a namespaced id string: " + tag);
            }
        }
        gameplayReference = Objects.requireNonNull(gameplayReference, "gameplayReference");
        gameplayReference.ifPresent(ref -> {
            if (!RESOURCE_ID.matcher(ref).matches()) {
                throw new IllegalArgumentException("gameplayReference must be a namespaced id string");
            }
        });
        Objects.requireNonNull(worldBounds, "worldBounds");
    }
}