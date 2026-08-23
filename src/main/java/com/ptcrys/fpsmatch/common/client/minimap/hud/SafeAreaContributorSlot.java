package com.ptcrys.fpsmatch.common.client.minimap.hud;

import com.ptcrys.fpsmatch.core.minimap.hud.HudSafeAreaRegistry;

import java.util.Objects;
import java.util.function.BiConsumer;

public record SafeAreaContributorSlot(
        String id,
        int priority,
        BiConsumer<HudSafeAreaRegistry, HudRenderContext> contributor
) {
    public SafeAreaContributorSlot {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id cannot be blank");
        }
        Objects.requireNonNull(contributor, "contributor");
    }
}