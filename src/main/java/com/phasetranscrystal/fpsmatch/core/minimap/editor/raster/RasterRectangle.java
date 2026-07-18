package com.phasetranscrystal.fpsmatch.core.minimap.editor.raster;

import java.util.Objects;

public final class RasterRectangle {
    public void drawFilled(RasterSurface surface, int minX, int minY, int maxX, int maxY, int rgba) {
        Objects.requireNonNull(surface, "surface");
        for (int y = minY; y < maxY; y++) {
            for (int x = minX; x < maxX; x++) {
                if (x >= 0 && y >= 0 && x < surface.width() && y < surface.height()) {
                    surface.setPixel(x, y, rgba);
                }
            }
        }
    }

    public void drawHollow(RasterSurface surface, int minX, int minY, int maxX, int maxY, int rgba) {
        Objects.requireNonNull(surface, "surface");
        for (int x = minX; x < maxX; x++) {
            set(surface, x, minY, rgba);
            set(surface, x, maxY - 1, rgba);
        }
        for (int y = minY; y < maxY; y++) {
            set(surface, minX, y, rgba);
            set(surface, maxX - 1, y, rgba);
        }
    }

    private static void set(RasterSurface surface, int x, int y, int rgba) {
        if (x >= 0 && y >= 0 && x < surface.width() && y < surface.height()) {
            surface.setPixel(x, y, rgba);
        }
    }
}
