package com.phasetranscrystal.fpsmatch.common.minimap.server;

import com.phasetranscrystal.fpsmatch.core.minimap.storage.PublishOutcome;

import java.util.Objects;

public final class PublishDeliveryException extends RuntimeException {
    private final PublishOutcome outcome;

    public PublishDeliveryException(PublishOutcome outcome, Throwable cause) {
        super("Minimap publish delivery failed after storage outcome "
                + Objects.requireNonNull(outcome, "outcome").status(), cause);
        this.outcome = outcome;
    }

    public PublishOutcome outcome() {
        return outcome;
    }
}
