package com.phasetranscrystal.fpsmatch.core.phase;

import java.util.Objects;

public record MatchPhaseDefinition(String phaseId, int durationTicks) {
    public static final int OPEN_ENDED = -1;

    public MatchPhaseDefinition {
        Objects.requireNonNull(phaseId, "phaseId");
        if (phaseId.isBlank()) {
            throw new IllegalArgumentException("phaseId must not be blank");
        }
        if (durationTicks < OPEN_ENDED) {
            throw new IllegalArgumentException("durationTicks must be -1 or >= 0");
        }
    }

    public static MatchPhaseDefinition timed(String phaseId, int durationTicks) {
        return new MatchPhaseDefinition(phaseId, durationTicks);
    }

    public static MatchPhaseDefinition openEnded(String phaseId) {
        return new MatchPhaseDefinition(phaseId, OPEN_ENDED);
    }

    public boolean openEnded() {
        return durationTicks == OPEN_ENDED;
    }
}
