package com.phasetranscrystal.fpsmatch.core.objective;

import java.util.Optional;
import java.util.UUID;

public record ObjectiveView(
        String id,
        String displayName,
        ObjectiveStatus status,
        int progressTicks,
        int completionTicks,
        Optional<UUID> controllingTeam,
        Optional<UUID> completedByTeam,
        Optional<CarriableState> carriableState,
        Optional<ObjectiveActor> holder
) {
    public static ObjectiveView from(ObjectiveInstance objective) {
        Optional<CarriableObjectiveState> carriable = objective.carriable();
        return new ObjectiveView(
                objective.id(),
                objective.definition().displayName(),
                objective.status(),
                objective.progressTicks(),
                objective.definition().completionTicks(),
                objective.controllingTeam(),
                objective.completedByTeam(),
                carriable.map(CarriableObjectiveState::state),
                carriable.flatMap(CarriableObjectiveState::holder)
        );
    }
}
