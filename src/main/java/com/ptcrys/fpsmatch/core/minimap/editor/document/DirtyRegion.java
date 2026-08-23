package com.ptcrys.fpsmatch.core.minimap.editor.document;

public record DirtyRegion(int minX, int minY, int maxX, int maxY) {
    public DirtyRegion {
        if (minX > maxX || minY > maxY) {
            throw new IllegalArgumentException("Dirty region bounds are inverted");
        }
    }

    public boolean isEmpty() {
        return minX >= maxX || minY >= maxY;
    }

    public boolean intersectsOrTouches(DirtyRegion other) {
        return minX <= other.maxX
                && maxX >= other.minX
                && minY <= other.maxY
                && maxY >= other.minY;
    }

    public DirtyRegion union(DirtyRegion other) {
        return new DirtyRegion(
                Math.min(minX, other.minX),
                Math.min(minY, other.minY),
                Math.max(maxX, other.maxX),
                Math.max(maxY, other.maxY)
        );
    }
}
