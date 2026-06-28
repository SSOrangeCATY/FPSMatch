package com.phasetranscrystal.fpsmatch.core.spawn;

import java.util.Objects;
import java.util.UUID;

public record SpawnAssignment(UUID teamId, SpawnCandidate candidate) {
    public SpawnAssignment {
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(candidate, "candidate");
    }
}
