package com.phasetranscrystal.fpsmatch.core.minimap.hud;

import java.util.Objects;
import java.util.Optional;

public final class HudSafeAreaEntry {
    private final String id;
    private final int priority;
    private final Optional<ScreenRect> fixedRect;
    private final Optional<HudFlexibleRequest> flexible;

    private HudSafeAreaEntry(
            String id,
            int priority,
            Optional<ScreenRect> fixedRect,
            Optional<HudFlexibleRequest> flexible
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.priority = priority;
        this.fixedRect = Objects.requireNonNull(fixedRect, "fixedRect");
        this.flexible = Objects.requireNonNull(flexible, "flexible");
        if (fixedRect.isEmpty() == flexible.isEmpty()) {
            throw new IllegalArgumentException("entry must be exactly fixed or flexible");
        }
    }

    public static HudSafeAreaEntry fixed(String id, int priority, ScreenRect rect) {
        return new HudSafeAreaEntry(id, priority, Optional.of(rect), Optional.empty());
    }

    public static HudSafeAreaEntry flexible(HudFlexibleRequest request) {
        Objects.requireNonNull(request, "request");
        return new HudSafeAreaEntry(request.id(), request.priority(), Optional.empty(), Optional.of(request));
    }

    public String id() {
        return id;
    }

    public int priority() {
        return priority;
    }

    public Optional<ScreenRect> fixedRect() {
        return fixedRect;
    }

    public Optional<HudFlexibleRequest> flexible() {
        return flexible;
    }

    public boolean isFixed() {
        return fixedRect.isPresent();
    }
}