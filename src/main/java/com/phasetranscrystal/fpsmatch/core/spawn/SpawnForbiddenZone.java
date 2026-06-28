package com.phasetranscrystal.fpsmatch.core.spawn;

import java.util.Objects;

public record SpawnForbiddenZone(SpawnVector center, double radius) {
    public SpawnForbiddenZone {
        Objects.requireNonNull(center, "center");
    }

    public boolean contains(SpawnVector position) {
        return center.distanceTo(position) <= radius;
    }
}
