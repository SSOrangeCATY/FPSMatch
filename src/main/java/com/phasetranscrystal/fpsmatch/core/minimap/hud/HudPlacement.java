package com.phasetranscrystal.fpsmatch.core.minimap.hud;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record HudPlacement(
        String id,
        boolean hidden,
        Optional<ScreenRect> rect,
        Optional<HudAnchor> anchor,
        int size,
        Set<String> conflictIds
) {
    public HudPlacement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(rect, "rect");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(conflictIds, "conflictIds");
        conflictIds = Set.copyOf(conflictIds);
        if (hidden) {
            if (rect.isPresent() || size != 0) {
                throw new IllegalArgumentException("hidden placement cannot have geometry");
            }
        } else if (rect.isEmpty() || anchor.isEmpty() || size <= 0) {
            throw new IllegalArgumentException("visible placement requires geometry");
        }
    }

    public static HudPlacement visible(String id, ScreenRect rect, HudAnchor anchor, int size) {
        return new HudPlacement(id, false, Optional.of(rect), Optional.of(anchor), size, Set.of());
    }

    public static HudPlacement hidden(String id, Set<String> conflictIds) {
        return new HudPlacement(id, true, Optional.empty(), Optional.empty(), 0, conflictIds);
    }
}