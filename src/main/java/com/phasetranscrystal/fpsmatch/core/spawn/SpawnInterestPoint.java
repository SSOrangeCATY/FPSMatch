package com.phasetranscrystal.fpsmatch.core.spawn;

import java.util.Objects;

public record SpawnInterestPoint(String id, SpawnVector position, double minDistance) {
    public SpawnInterestPoint {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(position, "position");
    }

    public boolean excludes(SpawnVector candidatePosition) {
        return position.distanceTo(candidatePosition) < minDistance;
    }
}
