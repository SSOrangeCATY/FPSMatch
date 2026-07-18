package com.phasetranscrystal.fpsmatch.core.minimap.model;

public record WorldBounds(double minX, double minZ, double maxX, double maxZ) {
    public WorldBounds {
        if (!Double.isFinite(minX) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxZ)
                || minX >= maxX || minZ >= maxZ) {
            throw new IllegalArgumentException("World bounds must be finite and non-empty");
        }
    }

    public boolean contains(WorldPoint2D point) {
        return point.x() >= minX && point.x() <= maxX
                && point.z() >= minZ && point.z() <= maxZ;
    }
}
