package com.phasetranscrystal.fpsmatch.core.spawn;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record SpawnPlan(boolean success, boolean usedFallback, List<SpawnAssignment> assignments) {
    public SpawnPlan {
        assignments = List.copyOf(Objects.requireNonNull(assignments, "assignments"));
    }

    public static SpawnPlan failed(boolean usedFallback) {
        return new SpawnPlan(false, usedFallback, List.of());
    }

    public Optional<SpawnAssignment> assignmentFor(UUID teamId) {
        Objects.requireNonNull(teamId, "teamId");
        return assignments.stream()
                .filter(assignment -> assignment.teamId().equals(teamId))
                .findFirst();
    }
}
