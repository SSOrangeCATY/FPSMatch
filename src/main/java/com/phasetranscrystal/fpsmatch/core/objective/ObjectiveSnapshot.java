package com.phasetranscrystal.fpsmatch.core.objective;

import java.util.List;

public record ObjectiveSnapshot(List<String> objectiveIds, List<ObjectiveView> objectives) {
    public ObjectiveSnapshot {
        objectiveIds = List.copyOf(objectiveIds);
        objectives = List.copyOf(objectives);
    }
}
