package com.ptcrys.fpsmatch.core.minimap.hud;

import com.ptcrys.fpsmatch.core.minimap.model.MapKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class HudSafeAreaResolver {
    private static final int OCCUPY_INFLATE = 4;
    private static final int SHRINK_STEP = 8;
    private static final HudAnchor[] DEFAULT_ANCHOR_ORDER = {
            HudAnchor.TOP_LEFT,
            HudAnchor.TOP_RIGHT,
            HudAnchor.BOTTOM_LEFT,
            HudAnchor.BOTTOM_RIGHT
    };

    public HudSafeAreaResolution resolve(
            MapKey mapKey,
            int screenWidth,
            int screenHeight,
            List<HudSafeAreaEntry> entries
    ) {
        Objects.requireNonNull(mapKey, "mapKey");
        Objects.requireNonNull(entries, "entries");
        if (screenWidth <= 0 || screenHeight <= 0) {
            throw new IllegalArgumentException("screen size must be positive");
        }

        List<HudSafeAreaEntry> ordered = new ArrayList<>(entries);
        ordered.sort(Comparator
                .comparingInt(HudSafeAreaEntry::priority).reversed()
                .thenComparing(HudSafeAreaEntry::id));

        List<String> placementOrder = new ArrayList<>();
        Map<String, HudPlacement> placements = new LinkedHashMap<>();
        List<Occupied> occupied = new ArrayList<>();

        for (HudSafeAreaEntry entry : ordered) {
            placementOrder.add(entry.id());
            if (entry.isFixed()) {
                ScreenRect rect = entry.fixedRect().orElseThrow();
                placements.put(entry.id(), HudPlacement.visible(entry.id(), rect, HudAnchor.TOP_LEFT, Math.min(rect.width(), rect.height())));
                occupied.add(new Occupied(entry.id(), rect));
                continue;
            }
            HudFlexibleRequest request = entry.flexible().orElseThrow();
            PlacementAttempt attempt = placeFlexible(request, screenWidth, screenHeight, occupied);
            if (attempt.placement().hidden()) {
                placements.put(entry.id(), attempt.placement());
            } else {
                placements.put(entry.id(), attempt.placement());
                occupied.add(new Occupied(entry.id(), attempt.placement().rect().orElseThrow()));
            }
        }
        return new HudSafeAreaResolution(placementOrder, placements);
    }

    private PlacementAttempt placeFlexible(
            HudFlexibleRequest request,
            int screenWidth,
            int screenHeight,
            List<Occupied> occupied
    ) {
        List<HudAnchor> anchors = anchorOrder(request.preferredAnchor());
        Set<String> conflicts = new TreeSet<>();
        for (HudAnchor anchor : anchors) {
            for (int size = request.preferredSize(); size >= request.minSize(); size -= SHRINK_STEP) {
                ScreenRect candidate = rectFor(anchor, size, request.margin(), screenWidth, screenHeight);
                if (candidate == null) {
                    continue;
                }
                boolean collides = false;
                for (Occupied item : occupied) {
                    if (candidate.intersects(item.rect().inflated(OCCUPY_INFLATE))) {
                        collides = true;
                        conflicts.add(item.id());
                    }
                }
                if (!collides) {
                    return new PlacementAttempt(HudPlacement.visible(request.id(), candidate, anchor, size));
                }
            }
        }
        return new PlacementAttempt(HudPlacement.hidden(request.id(), conflicts));
    }

    private static List<HudAnchor> anchorOrder(HudAnchor preferred) {
        List<HudAnchor> order = new ArrayList<>();
        order.add(preferred);
        for (HudAnchor anchor : DEFAULT_ANCHOR_ORDER) {
            if (anchor != preferred) {
                order.add(anchor);
            }
        }
        return order;
    }

    private static ScreenRect rectFor(HudAnchor anchor, int size, int margin, int screenWidth, int screenHeight) {
        if (size + margin * 2 > screenWidth || size + margin * 2 > screenHeight) {
            return null;
        }
        return switch (anchor) {
            case TOP_LEFT -> new ScreenRect(margin, margin, size, size);
            case TOP_RIGHT -> new ScreenRect(screenWidth - margin - size, margin, size, size);
            case BOTTOM_LEFT -> new ScreenRect(margin, screenHeight - margin - size, size, size);
            case BOTTOM_RIGHT -> new ScreenRect(screenWidth - margin - size, screenHeight - margin - size, size, size);
        };
    }

    private record Occupied(String id, ScreenRect rect) {
    }

    private record PlacementAttempt(HudPlacement placement) {
    }
}