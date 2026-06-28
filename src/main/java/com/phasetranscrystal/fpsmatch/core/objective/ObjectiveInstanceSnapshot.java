package com.phasetranscrystal.fpsmatch.core.objective;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record ObjectiveInstanceSnapshot(
        String objectiveId,
        ObjectiveStatus status,
        int progressTicks,
        Optional<ObjectiveActor> activeInteraction,
        Optional<UUID> controllingTeam,
        Optional<UUID> completedByTeam,
        Set<ObjectiveActor> presentActors,
        Optional<CarriableObjectiveSnapshot> carriable
) {
    public ObjectiveInstanceSnapshot {
        Objects.requireNonNull(objectiveId, "objectiveId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(activeInteraction, "activeInteraction");
        Objects.requireNonNull(controllingTeam, "controllingTeam");
        Objects.requireNonNull(completedByTeam, "completedByTeam");
        Objects.requireNonNull(presentActors, "presentActors");
        Objects.requireNonNull(carriable, "carriable");
        if (objectiveId.isBlank()) {
            throw new IllegalArgumentException("objectiveId must not be blank");
        }
        if (progressTicks < 0) {
            throw new IllegalArgumentException("progressTicks must be >= 0");
        }
        presentActors = Set.copyOf(presentActors);
    }

    static ObjectiveInstanceSnapshot from(ObjectiveInstance objective) {
        Objects.requireNonNull(objective, "objective");
        return new ObjectiveInstanceSnapshot(
                objective.id(),
                objective.status(),
                objective.progressTicks(),
                objective.activeInteraction(),
                objective.controllingTeam(),
                objective.completedByTeam(),
                objective.presentActors(),
                objective.carriable().map(CarriableObjectiveSnapshot::from)
        );
    }
}
