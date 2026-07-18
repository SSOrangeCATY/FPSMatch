package com.phasetranscrystal.fpsmatch.core.minimap.model;

import java.util.Objects;

public record FloorBackground(RgbaColor color) {
    public FloorBackground {
        Objects.requireNonNull(color, "color");
    }
}
