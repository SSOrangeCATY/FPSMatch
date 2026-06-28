package com.phasetranscrystal.fpsmatch.core.phase;

import java.util.Objects;

public record MatchPhaseSnapshot(
        String currentPhaseId,
        int currentPhaseIndex,
        int elapsedTicks,
        int phaseElapsedTicks
) {
    public MatchPhaseSnapshot {
        Objects.requireNonNull(currentPhaseId, "currentPhaseId");
        if (currentPhaseId.isBlank()) {
            throw new IllegalArgumentException("currentPhaseId must not be blank");
        }
        if (currentPhaseIndex < 0) {
            throw new IllegalArgumentException("currentPhaseIndex must be >= 0");
        }
        if (elapsedTicks < 0) {
            throw new IllegalArgumentException("elapsedTicks must be >= 0");
        }
        if (phaseElapsedTicks < 0) {
            throw new IllegalArgumentException("phaseElapsedTicks must be >= 0");
        }
    }
}
