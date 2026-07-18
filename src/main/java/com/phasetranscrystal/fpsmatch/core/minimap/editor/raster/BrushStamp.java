package com.phasetranscrystal.fpsmatch.core.minimap.editor.raster;

public final class BrushStamp {
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 64;

    public enum Shape {
        SQUARE,
        ROUND
    }

    private final Shape shape;
    private final int size;
    private final boolean antialias;

    private BrushStamp(Shape shape, int size, boolean antialias) {
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new IllegalArgumentException("Brush size must be in [1, 64]");
        }
        this.shape = shape;
        this.size = size;
        this.antialias = antialias;
    }

    public static BrushStamp square(int size, boolean antialias) {
        return new BrushStamp(Shape.SQUARE, size, antialias);
    }

    public static BrushStamp round(int size, boolean antialias) {
        return new BrushStamp(Shape.ROUND, size, antialias);
    }

    public Shape shape() {
        return shape;
    }

    public int size() {
        return size;
    }

    public boolean antialias() {
        return antialias;
    }

    public int radius() {
        return size / 2;
    }

    public float coverage(int dx, int dy) {
        if (shape == Shape.SQUARE) {
            int extent = size;
            int min = -radius();
            int max = min + extent - 1;
            if (dx < min || dy < min || dx > max || dy > max) {
                return 0.0f;
            }
            if (!antialias) {
                return 1.0f;
            }
            // Soft square edges only at the outer 0.5px shell of the stamp.
            float edge = Math.min(Math.min(dx - min, max - dx), Math.min(dy - min, max - dy));
            if (edge >= 0.5f) {
                return 1.0f;
            }
            return Math.max(0.0f, Math.min(1.0f, edge + 0.5f));
        }

        int r = radius();
        float distance = (float) Math.hypot(dx, dy);
        if (!antialias) {
            // Hard disks exclude the outer half-pixel ring so size-5 edge samples are fully outside.
            return distance <= Math.max(0.0f, r - 0.5f) + 1.0e-4f ? 1.0f : 0.0f;
        }
        float outer = r + 0.5f;
        float inner = Math.max(0.0f, outer - 1.0f);
        if (distance <= inner) {
            return 1.0f;
        }
        if (distance >= outer) {
            return 0.0f;
        }
        return (outer - distance) / (outer - inner);
    }
}
