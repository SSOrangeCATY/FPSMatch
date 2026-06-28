package com.phasetranscrystal.fpsmatch.core.objective;

import java.util.Objects;
import java.util.UUID;

public record ObjectiveActor(UUID playerId, UUID teamId) {
    public ObjectiveActor {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(teamId, "teamId");
    }

    public static ObjectiveActor player(UUID playerId, UUID teamId) {
        return new ObjectiveActor(playerId, teamId);
    }
}
