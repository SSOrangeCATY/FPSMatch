package com.phasetranscrystal.fpsmatch.core.minimap.hud;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class HudSafeAreaResolution {
    private final List<String> placementOrder;
    private final Map<String, HudPlacement> placements;

    public HudSafeAreaResolution(List<String> placementOrder, Map<String, HudPlacement> placements) {
        this.placementOrder = List.copyOf(Objects.requireNonNull(placementOrder, "placementOrder"));
        this.placements = Map.copyOf(Objects.requireNonNull(placements, "placements"));
    }

    public List<String> placementOrder() {
        return placementOrder;
    }

    public Optional<HudPlacement> placement(String id) {
        return Optional.ofNullable(placements.get(id));
    }

    public Map<String, HudPlacement> placements() {
        return placements;
    }

    public List<ScreenRect> occupiedRects() {
        return placements.values().stream()
                .filter(p -> !p.hidden())
                .map(p -> p.rect().orElseThrow())
                .toList();
    }
}