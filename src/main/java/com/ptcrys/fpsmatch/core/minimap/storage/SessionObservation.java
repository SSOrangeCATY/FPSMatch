package com.ptcrys.fpsmatch.core.minimap.storage;

import java.util.Objects;

record SessionObservation(
        String domainKey,
        long epochId,
        String executionContextId,
        long waiterOrOperationId,
        EpochState epochState,
        HandleState handleState,
        int referenceCount,
        int admittedOperationCount,
        CompletionOutcome completionOutcome
) {
    enum EpochState {
        OPENING,
        ACTIVE,
        CLOSING,
        CLOSED,
        POISONED
    }

    enum HandleState {
        NONE,
        OPEN,
        CLOSING,
        CLOSED,
        POISONED
    }

    enum CompletionOutcome {
        NONE,
        ACQUIRED,
        CLOSED,
        REJECTED,
        POISONED
    }

    SessionObservation {
        domainKey = requireText(domainKey, "domainKey");
        executionContextId = requireText(executionContextId, "executionContextId");
        if (epochId <= 0 || waiterOrOperationId < 0
                || referenceCount < 0 || admittedOperationCount < 0) {
            throw new IllegalArgumentException("Session observation counters are invalid");
        }
        Objects.requireNonNull(epochState, "epochState");
        Objects.requireNonNull(handleState, "handleState");
        Objects.requireNonNull(completionOutcome, "completionOutcome");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
