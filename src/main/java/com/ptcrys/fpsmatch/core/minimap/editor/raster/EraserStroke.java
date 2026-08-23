package com.ptcrys.fpsmatch.core.minimap.editor.raster;

import java.util.Objects;

public final class EraserStroke {
    private final BrushStamp stamp;

    public EraserStroke(BrushStamp stamp) {
        this.stamp = Objects.requireNonNull(stamp, "stamp");
    }

    public void apply(RasterSurface surface, int centerX, int centerY) {
        Objects.requireNonNull(surface, "surface");
        int radius = stamp.radius();
        int min = -radius;
        int max = min + stamp.size() - 1;
        for (int dy = min; dy <= max; dy++) {
            for (int dx = min; dx <= max; dx++) {
                if (stamp.coverage(dx, dy) <= 0.0f) {
                    continue;
                }
                int x = centerX + dx;
                int y = centerY + dy;
                try {
                    surface.erasePixel(x, y);
                } catch (IllegalArgumentException ignored) {
                    // Outside canvas.
                }
            }
        }
    }
}
