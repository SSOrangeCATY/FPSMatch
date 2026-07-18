package com.phasetranscrystal.fpsmatch.core.minimap.editor.raster;

import java.util.Objects;

public final class ColorReplace {
    private final ColorTolerance tolerance;

    public ColorReplace(ColorTolerance tolerance) {
        this.tolerance = Objects.requireNonNull(tolerance, "tolerance");
    }

    public int replace(RasterSurface surface, int fromRgba, int toRgba) {
        Objects.requireNonNull(surface, "surface");
        int changed = 0;
        for (int y = 0; y < surface.height(); y++) {
            for (int x = 0; x < surface.width(); x++) {
                if (!surface.isSelected(x, y) || surface.isInherited(x, y)) {
                    continue;
                }
                int current = surface.getPixel(x, y);
                if (tolerance.matches(fromRgba, current)) {
                    surface.setPixel(x, y, toRgba);
                    changed++;
                }
            }
        }
        return changed;
    }
}
