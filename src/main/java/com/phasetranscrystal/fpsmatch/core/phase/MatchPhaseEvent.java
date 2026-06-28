package com.phasetranscrystal.fpsmatch.core.phase;

import java.util.Objects;

public record MatchPhaseEvent(String previousPhaseId, String currentPhaseId, int elapsedTicks) {
    public MatchPhaseEvent {
        Objects.requireNonNull(previousPhaseId, "previousPhaseId");
        Objects.requireNonNull(currentPhaseId, "currentPhaseId");
        if (previousPhaseId.isBlank()) {
            throw new IllegalArgumentException("previousPhaseId must not be blank");
        }
        if (currentPhaseId.isBlank()) {
            throw new IllegalArgumentException("currentPhaseId must not be blank");
        }
        if (elapsedTicks < 0) {
            throw new IllegalArgumentException("elapsedTicks must be >= 0");
        }
    }
}
