package com.phasetranscrystal.fpsmatch.common.minimap.server;

import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.MinimapRepository;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.PublishOutcome;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.PublishTransaction;

import java.util.Objects;

public final class PublishCoordinator {
    private final MinimapRepository repository;
    private final PublishDelivery delivery;

    public PublishCoordinator(
            MinimapRepository repository,
            PublishDelivery delivery
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.delivery = Objects.requireNonNull(delivery, "delivery");
    }

    public PublishOutcome commit(PublishTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        PublishOutcome outcome = repository.commit(transaction);
        RuntimeException deliveryFailure = attemptResponse(outcome);
        if (outcome.committed()) {
            deliveryFailure = combine(
                    deliveryFailure,
                    attemptBroadcast(transaction.mapKey(), outcome.revision())
            );
        }
        if (deliveryFailure != null) {
            throw new PublishDeliveryException(outcome, deliveryFailure);
        }
        return outcome;
    }

    public PublishOutcome recoverAndReplay(MapKey mapKey) {
        Objects.requireNonNull(mapKey, "mapKey");
        PublishOutcome outcome = repository.recover(mapKey);
        if (!outcome.committed()) {
            return outcome;
        }
        RuntimeException failure = attemptBroadcast(mapKey, outcome.revision());
        if (failure != null) {
            throw new PublishDeliveryException(outcome, failure);
        }
        return outcome;
    }

    private RuntimeException attemptResponse(PublishOutcome outcome) {
        try {
            delivery.respond(outcome);
            return null;
        } catch (RuntimeException failure) {
            return failure;
        }
    }

    private RuntimeException attemptBroadcast(MapKey mapKey, long revision) {
        try {
            delivery.broadcastRevision(mapKey, revision);
            return null;
        } catch (RuntimeException failure) {
            return failure;
        }
    }

    private static RuntimeException combine(
            RuntimeException first,
            RuntimeException second
    ) {
        if (first == null) {
            return second;
        }
        if (second != null) {
            first.addSuppressed(second);
        }
        return first;
    }
}
