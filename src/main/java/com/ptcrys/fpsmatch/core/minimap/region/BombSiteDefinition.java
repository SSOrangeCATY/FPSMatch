package com.ptcrys.fpsmatch.core.minimap.region;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapFormatContract;

import java.util.Objects;
import java.util.Optional;

/**
 * Stable bomb-site identity used by minimap region providers.
 * IDs are internal slugs such as site_1 / site_a; never display-name order.
 */
public record BombSiteDefinition(
        String id,
        Optional<String> displayName,
        WorldAxisAlignedBounds bounds
) {
    public BombSiteDefinition {
        if (!MinimapFormatContract.isInternalSlug(id)) {
            throw new IllegalArgumentException("Bomb site id must be a valid internal slug");
        }
        displayName = Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(bounds, "bounds");
    }

    public static BombSiteDefinition of(String id, WorldAxisAlignedBounds bounds) {
        return new BombSiteDefinition(id, Optional.empty(), bounds);
    }

    public static BombSiteDefinition of(String id, String displayName, WorldAxisAlignedBounds bounds) {
        return new BombSiteDefinition(id, Optional.ofNullable(displayName), bounds);
    }
}