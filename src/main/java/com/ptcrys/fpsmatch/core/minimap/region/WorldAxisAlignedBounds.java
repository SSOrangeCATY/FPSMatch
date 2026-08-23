package com.ptcrys.fpsmatch.core.minimap.region;

import java.util.Collection;
import java.util.Objects;

/**
 * Pure world-space AABB on XZ (Y optional for floor hints). No Minecraft types.
 */
public record WorldAxisAlignedBounds(
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ
) {
    public WorldAxisAlignedBounds {
        if (!(minX <= maxX) || !(minY <= maxY) || !(minZ <= maxZ)
                || !Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)) {
            throw new IllegalArgumentException("World bounds must be finite and ordered");
        }
    }

    public double centerX() {
        return (minX + maxX) * 0.5;
    }

    public double centerZ() {
        return (minZ + maxZ) * 0.5;
    }

    public WorldAxisAlignedBounds expand(double horizontal, double vertical) {
        if (!Double.isFinite(horizontal) || !Double.isFinite(vertical) || horizontal < 0 || vertical < 0) {
            throw new IllegalArgumentException("expand amounts must be finite and non-negative");
        }
        return new WorldAxisAlignedBounds(
                minX - horizontal,
                minY - vertical,
                minZ - horizontal,
                maxX + horizontal,
                maxY + vertical,
                maxZ + horizontal
        );
    }

    /**
     * Axis-aligned envelope around discrete points (spawn points). Empty input is rejected.
     */
    public static WorldAxisAlignedBounds envelopeOfPoints(Collection<Point3> points) {
        Objects.requireNonNull(points, "points");
        if (points.isEmpty()) {
            throw new IllegalArgumentException("points cannot be empty");
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Point3 point : points) {
            Objects.requireNonNull(point, "point");
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
            maxZ = Math.max(maxZ, point.z());
        }
        return new WorldAxisAlignedBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public record Point3(double x, double y, double z) {
        public Point3 {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Point must be finite");
            }
        }
    }
}