package com.phasetranscrystal.fpsmatch.core.settlement;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ObjectiveOutcome(
        String objectiveId,
        OutcomeType outcome,
        Optional<UUID> completedByTeam,
        Map<String, String> metadata
) {
    public ObjectiveOutcome(String objectiveId, OutcomeType outcome, Optional<UUID> completedByTeam) {
        this(objectiveId, outcome, completedByTeam, Map.of());
    }

    public ObjectiveOutcome {
        Objects.requireNonNull(objectiveId, "objectiveId");
        Objects.requireNonNull(outcome, "outcome");
        completedByTeam = Objects.requireNonNull(completedByTeam, "completedByTeam");
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }
}
