package com.phasetranscrystal.fpsmatch.core.objective;

import java.util.Objects;
import java.util.Optional;

public final class CarriableObjectiveState {
    private CarriableState state = CarriableState.AVAILABLE;
    private ObjectiveActor holder;

    public CarriableState state() {
        return state;
    }

    public Optional<ObjectiveActor> holder() {
        return Optional.ofNullable(holder);
    }

    void pickUp(ObjectiveActor actor) {
        this.holder = actor;
        this.state = CarriableState.CARRIED;
    }

    void drop() {
        this.holder = null;
        this.state = CarriableState.DROPPED;
    }

    void resolve() {
        this.state = CarriableState.RESOLVED;
    }

    void restore(CarriableState state, ObjectiveActor holder) {
        Objects.requireNonNull(state, "state");
        if (state == CarriableState.CARRIED && holder == null) {
            throw new IllegalArgumentException("carried objective must have a holder");
        }
        if ((state == CarriableState.AVAILABLE || state == CarriableState.DROPPED) && holder != null) {
            throw new IllegalArgumentException(state + " objective must not have a holder");
        }
        this.state = state;
        this.holder = holder;
    }
}
