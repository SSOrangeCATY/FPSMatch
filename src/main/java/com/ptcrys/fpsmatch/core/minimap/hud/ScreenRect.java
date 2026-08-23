package com.ptcrys.fpsmatch.core.minimap.hud;

public record ScreenRect(int x, int y, int width, int height) {
    public ScreenRect {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("ScreenRect size must be positive");
        }
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public ScreenRect inflated(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("inflate amount must be non-negative");
        }
        if (amount == 0) {
            return this;
        }
        return new ScreenRect(x - amount, y - amount, width + amount * 2, height + amount * 2);
    }

    public boolean intersects(ScreenRect other) {
        return x < other.right()
                && right() > other.x
                && y < other.bottom()
                && bottom() > other.y;
    }
}