package com.phasetranscrystal.fpsmatch.core.minimap.editor.raster;

import java.util.Objects;

public final class CutoutStroke {
    private final BrushStamp stamp;
    private final int alpha;

    public CutoutStroke(BrushStamp stamp, int alpha) {
        this.stamp = Objects.requireNonNull(stamp, "stamp");
        if (alpha < 0 || alpha > 255) {
            throw new IllegalArgumentException("Cutout alpha must be in [0, 255]");
        }
        this.alpha = alpha;
    }

    public void apply(RasterSurface surface, int centerX, int centerY) {
        Objects.requireNonNull(surface, "surface");
        int rgba = Rgba8.of(255, 255, 255, alpha);
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
