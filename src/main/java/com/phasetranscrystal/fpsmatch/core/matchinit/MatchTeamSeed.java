package com.phasetranscrystal.fpsmatch.core.matchinit;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MatchTeamSeed(UUID teamId, String name, int limit, List<UUID> players) {
    public MatchTeamSeed {
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(name, "name");
        players = List.copyOf(Objects.requireNonNull(players, "players"));
    }
}
