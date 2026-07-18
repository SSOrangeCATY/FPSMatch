package com.phasetranscrystal.fpsmatch.core.minimap.hud;

import java.util.Objects;

public record HudFlexibleRequest(
        String id,
        int priority,
        int preferredSize,
        int minSize,
        int margin,
        HudAnchor preferredAnchor
) {
    public static final String MINIMAP_HUD_ID = "fpsmatch:minimap_hud";
    public static final int MINIMAP_DEFAULT_PRIORITY = 50;
    public static final int MINIMAP_PREFERRED_SIZE = 128;
    public static final int MINIMAP_MIN_SIZE = 96;
    public static final int MINIMAP_DEFAULT_MARGIN = 8;

    public HudFlexibleRequest {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id cannot be blank");
        }
        if (preferredSize < minSize) {
            throw new IllegalArgumentException("preferredSize must be >= minSize");
        }
        if (minSize <= 0 || preferredSize <= 0) {
            throw new IllegalArgumentException("sizes must be positive");
        }
        if (margin < 0) {
            throw new IllegalArgumentException("margin must be non-negative");
        }
        Objects.requireNonNull(preferredAnchor, "preferredAnchor");
    }

    public static HudFlexibleRequest minimapDefaults(HudAnchor preferredAnchor) {
        return new HudFlexibleRequest(
                MINIMAP_HUD_ID,
                MINIMAP_DEFAULT_PRIORITY,
                MINIMAP_PREFERRED_SIZE,
                MINIMAP_MIN_SIZE,
                MINIMAP_DEFAULT_MARGIN,
                preferredAnchor
        );
    }
}