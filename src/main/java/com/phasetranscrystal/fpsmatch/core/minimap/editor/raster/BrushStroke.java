package com.phasetranscrystal.fpsmatch.core.minimap.editor.raster;

import java.util.Objects;

public final class BrushStroke {
    private final BrushStamp stamp;
    private final int rgba;

    public BrushStroke(BrushStamp stamp, int rgba) {
        this.stamp = Objects.requireNonNull(stamp, "stamp");
        this.rgba = rgba;
    }

    public void apply(RasterSurface surface, int centerX, int centerY) {
        Objects.requireNonNull(surface, "surface");
        int radius = stamp.radius();
        int min = -radius;
        int max = min + stamp.size() - 1;
        for (int dy = min; dy <= max; dy++) {
            for (int dx = min; dx <= max; dx++) {
                float coverage = stamp.coverage(dx, dy);
                if (coverage <= 0.0f) {
                    continue;
                }
                int x = centerX + dx;
                int y = centerY + dy;
                try {
                    surface.paintCoverage(x, y, rgba, coverage);
                } catch (IllegalArgumentException ignored) {
                    // Outside canvas.
                }
            }
        }
    }
}
