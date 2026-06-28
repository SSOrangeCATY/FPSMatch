package com.phasetranscrystal.fpsmatch.core.settlement;

import java.util.Objects;
import java.util.UUID;

public record TeamOutcome(UUID teamId, OutcomeType outcome) {
    public TeamOutcome {
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(outcome, "outcome");
    }
}
