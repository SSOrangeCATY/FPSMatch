package com.phasetranscrystal.fpsmatch.core.area;

import java.util.Objects;
import java.util.UUID;

public record AreaActor(UUID playerId, UUID teamId) {
    public AreaActor {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(teamId, "teamId");
    }

    public static AreaActor player(UUID playerId, UUID teamId) {
        return new AreaActor(playerId, teamId);
    }
}
