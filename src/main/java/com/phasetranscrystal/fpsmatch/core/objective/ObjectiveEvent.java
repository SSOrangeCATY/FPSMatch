package com.phasetranscrystal.fpsmatch.core.objective;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ObjectiveEvent(
        ObjectiveEventType type,
        String objectiveId,
        ObjectiveStatus status,
        Optional<ObjectiveActor> actor,
        Optional<UUID> teamId,
        int progressTicks,
        int completionTicks
) {
    public ObjectiveEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(objectiveId, "objectiveId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(teamId, "teamId");
        if (objectiveId.isBlank()) {
            throw new IllegalArgumentException("objectiveId must not be blank");
        }
        if (progressTicks < 0) {
            throw new IllegalArgumentException("progressTicks must be >= 0");
        }
        if (completionTicks < 0) {
            throw new IllegalArgumentException("completionTicks must be >= 0");
        }
    }
}
