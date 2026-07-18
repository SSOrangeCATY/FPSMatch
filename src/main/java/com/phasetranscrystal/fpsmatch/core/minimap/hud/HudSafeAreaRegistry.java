package com.phasetranscrystal.fpsmatch.core.minimap.hud;

import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Per-frame contributor registry. beginFrame clears contributions; resolve places once.
 */
public final class HudSafeAreaRegistry {
    private final HudSafeAreaResolver resolver = new HudSafeAreaResolver();
    private final List<HudSafeAreaEntry> frameEntries = new ArrayList<>();
    private final Set<HudSafeAreaDiagnostic> diagnostics = new LinkedHashSet<>();
    private MapKey mapKey;
    private int screenWidth;
    private int screenHeight;
    private boolean frameOpen;

    public void beginFrame(MapKey mapKey, int screenWidth, int screenHeight) {
        this.mapKey = Objects.requireNonNull(mapKey, "mapKey");
        if (screenWidth <= 0 || screenHeight <= 0) {
            throw new IllegalArgumentException("screen size must be positive");
        }
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.frameEntries.clear();
        this.frameOpen = true;
    }

    public void contributeFixed(String id, int priority, ScreenRect rect) {
        requireOpenFrame();
        frameEntries.add(HudSafeAreaEntry.fixed(id, priority, rect));
    }

    public void contributeFlexible(HudFlexibleRequest request) {
        requireOpenFrame();
        frameEntries.add(HudSafeAreaEntry.flexible(request));
    }

    public HudSafeAreaResolution resolve() {
        requireOpenFrame();
        frameOpen = false;
        HudSafeAreaResolution resolution = resolver.resolve(mapKey, screenWidth, screenHeight, List.copyOf(frameEntries));
        for (HudPlacement placement : resolution.placements().values()) {
            if (placement.hidden()) {
                diagnostics.add(new HudSafeAreaDiagnostic(mapKey, placement.id(), placement.conflictIds()));
            }
        }
        return resolution;
    }

    public Set<HudSafeAreaDiagnostic> diagnostics() {
        return Set.copyOf(diagnostics);
    }

    public void clearDiagnostics() {
        diagnostics.clear();
    }

    private void requireOpenFrame() {
        if (!frameOpen) {
            throw new IllegalStateException("beginFrame required before contribute/resolve");
        }
    }
}