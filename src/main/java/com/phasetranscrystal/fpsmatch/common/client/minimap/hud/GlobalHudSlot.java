package com.phasetranscrystal.fpsmatch.common.client.minimap.hud;

import com.phasetranscrystal.fpsmatch.core.minimap.hud.HudSafeAreaResolution;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public record GlobalHudSlot(
        String id,
        Predicate<HudRenderContext> predicate,
        BiConsumer<HudRenderContext, HudSafeAreaResolution> renderer
) {
    public GlobalHudSlot {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id cannot be blank");
        }
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(renderer, "renderer");
    }
}
