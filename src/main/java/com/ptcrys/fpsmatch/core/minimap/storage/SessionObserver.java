package com.ptcrys.fpsmatch.core.minimap.storage;

import java.util.Objects;

@FunctionalInterface
interface SessionObserver {
    enum Event {
        AFTER_SESSION_WAITER_ENQUEUED,
        AFTER_SESSION_ACQUIRED,
        AFTER_SESSION_REENTERED,
        AFTER_OPERATION_WAITER_ENQUEUED,
        AFTER_OPERATION_ADMITTED,
        AFTER_HANDLE_CLOSING,
        AFTER_HANDLE_CLOSED,
        AFTER_DOMAIN_CLOSED,
        AFTER_DOMAIN_POISONED,
        AFTER_SESSION_WAITER_COMPLETED,
        AFTER_OPERATION_WAITER_COMPLETED,
        AFTER_LOCK_OPEN_BEFORE_LOCK,
        AFTER_LOCK_BEFORE_VALIDATE,
        BEFORE_UNLOCK,
        AFTER_UNLOCK,
        BEFORE_LOCK_HANDLE_CLOSE,
        AFTER_LOCK_HANDLE_CLOSE,
        BEFORE_PARENT_HANDLE_CLOSE,
        AFTER_PARENT_HANDLE_CLOSE
    }

    void observe(Event event, SessionObservation observation);

    static SessionObserver none() {
        return (event, observation) -> {
            Objects.requireNonNull(event, "event");
            Objects.requireNonNull(observation, "observation");
        };
    }
}
