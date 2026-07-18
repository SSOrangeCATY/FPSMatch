package com.phasetranscrystal.fpsmatch.common.minimap.server;

import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;

import java.util.Objects;

public record WorldSectionKey(
        NamespacedId dimension,
        int sectionX,
        int sectionY,
        int sectionZ
) {
    public WorldSectionKey {
        Objects.requireNonNull(dimension, "dimension");
    }
}
