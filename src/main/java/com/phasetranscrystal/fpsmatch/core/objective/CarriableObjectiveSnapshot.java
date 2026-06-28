package com.phasetranscrystal.fpsmatch.core.objective;

import java.util.Objects;
import java.util.Optional;

public record CarriableObjectiveSnapshot(
        CarriableState state,
        Optional<ObjectiveActor> holder
) {
    public CarriableObjectiveSnapshot {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(holder, "holder");
    }

    static CarriableObjectiveSnapshot from(CarriableObjectiveState state) {
        Objects.requireNonNull(state, "state");
        return new CarriableObjectiveSnapshot(state.state(), state.holder());
    }
}
