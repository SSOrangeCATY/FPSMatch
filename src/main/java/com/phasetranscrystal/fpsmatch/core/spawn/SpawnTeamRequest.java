package com.phasetranscrystal.fpsmatch.core.spawn;

import java.util.Objects;
import java.util.UUID;

public record SpawnTeamRequest(UUID teamId, int size) {
    public SpawnTeamRequest {
        Objects.requireNonNull(teamId, "teamId");
    }
}
