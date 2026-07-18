package com.phasetranscrystal.fpsmatch.core.minimap.editor.raster;

import java.util.Objects;
import java.util.Optional;

public final class Eyedropper {
    public Optional<Integer> sample(RasterSurface surface, int x, int y) {
        Objects.requireNonNull(surface, "surface");
        if (surface.isInherited(x, y)) {
            return Optional.empty();
        }
        return Optional.of(surface.getPixel(x, y));
    }
}
