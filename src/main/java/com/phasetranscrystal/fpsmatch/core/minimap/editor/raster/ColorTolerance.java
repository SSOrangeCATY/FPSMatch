package com.phasetranscrystal.fpsmatch.core.minimap.editor.raster;

public final class ColorTolerance {
    private final int tolerance;

    public ColorTolerance(int tolerance) {
        if (tolerance < 0 || tolerance > 255) {
            throw new IllegalArgumentException("Color tolerance must be in [0, 255]");
        }
        this.tolerance = tolerance;
    }

    public int tolerance() {
        return tolerance;
    }

    public boolean matches(int left, int right) {
        int dr = Math.abs(Rgba8.red(left) - Rgba8.red(right));
        int dg = Math.abs(Rgba8.green(left) - Rgba8.green(right));
        int db = Math.abs(Rgba8.blue(left) - Rgba8.blue(right));
        int da = Math.abs(Rgba8.alpha(left) - Rgba8.alpha(right));
        return Math.max(Math.max(dr, dg), Math.max(db, da)) <= tolerance;
    }
}
