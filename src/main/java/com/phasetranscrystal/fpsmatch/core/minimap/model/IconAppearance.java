package com.phasetranscrystal.fpsmatch.core.minimap.model;

import java.util.Objects;

public record IconAppearance(NamespacedId texture, double scale) {
    public IconAppearance {
        Objects.requireNonNull(texture, "texture");
        if (!Double.isFinite(scale) || scale <= 0 || scale > 64) {
            throw new IllegalArgumentException("Icon scale must be in (0, 64]");
        }
    }
}
