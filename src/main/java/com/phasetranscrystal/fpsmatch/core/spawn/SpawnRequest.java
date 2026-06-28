package com.phasetranscrystal.fpsmatch.core.spawn;

import java.util.List;
import java.util.Objects;

public record SpawnRequest(List<SpawnTeamRequest> teams, List<SpawnCandidate> candidates, SpawnConstraints constraints) {
    public SpawnRequest {
        teams = List.copyOf(Objects.requireNonNull(teams, "teams"));
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        Objects.requireNonNull(constraints, "constraints");
    }
}
