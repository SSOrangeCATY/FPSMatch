package com.ptcrys.fpsmatch.core.minimap.storage;

public record PublishOutcome(
        PublishState state,
        Status status,
        long revision,
        String message
) {
    public enum Status {
        PREPARED,
        COMMITTED,
        ABORTED,
        UNAVAILABLE,
        COMMIT_STATUS_UNKNOWN
    }

    public PublishOutcome {
        if (state == null || status == null || revision < 0) {
            throw new IllegalArgumentException("Publish outcome fields are invalid");
        }
        message = message == null ? "" : message;
    }

    public boolean committed() {
        return status == Status.COMMITTED && state == PublishState.COMMITTED;
    }

    public boolean unknown() {
        return status == Status.COMMIT_STATUS_UNKNOWN;
    }
}
