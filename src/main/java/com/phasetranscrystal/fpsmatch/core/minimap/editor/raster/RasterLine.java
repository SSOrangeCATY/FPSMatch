package com.phasetranscrystal.fpsmatch.core.minimap.editor.raster;

import java.util.Objects;

public final class RasterLine {
    public void draw(RasterSurface surface, int x0, int y0, int x1, int y1, int rgba) {
        Objects.requireNonNull(surface, "surface");
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            trySet(surface, x, y, rgba);
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = err * 2;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }

    private static void trySet(RasterSurface surface, int x, int y, int rgba) {
        if (x < 0 || y < 0 || x >= surface.width() || y >= surface.height()) {
            return;
        }
        surface.setPixel(x, y, rgba);
    }
}
