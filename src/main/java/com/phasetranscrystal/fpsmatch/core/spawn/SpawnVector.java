package com.phasetranscrystal.fpsmatch.core.spawn;

public record SpawnVector(double x, double y, double z) {
    public double distanceTo(SpawnVector other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
