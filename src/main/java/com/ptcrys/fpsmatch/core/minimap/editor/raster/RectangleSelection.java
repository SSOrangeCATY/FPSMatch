package com.ptcrys.fpsmatch.core.minimap.editor.raster;

public final class RectangleSelection implements SelectionMask {
    private final int minX;
    private final int minY;
    private final int maxX;
    private final int maxY;

    public RectangleSelection(int minX, int minY, int maxX, int maxY) {
        if (minX > maxX || minY > maxY) {
            throw new IllegalArgumentException("Rectangle selection bounds are inverted");
        }
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    @Override
    public boolean contains(int x, int y) {
        return x >= minX && x < maxX && y >= minY && y < maxY;
    }
}
