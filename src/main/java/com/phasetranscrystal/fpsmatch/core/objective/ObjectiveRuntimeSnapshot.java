package com.phasetranscrystal.fpsmatch.core.objective;

import java.util.List;
import java.util.Objects;

public record ObjectiveRuntimeSnapshot(List<ObjectiveInstanceSnapshot> objectives) {
    public ObjectiveRuntimeSnapshot {
        Objects.requireNonNull(objectives, "objectives");
        objectives = List.copyOf(objectives);
    }
}
