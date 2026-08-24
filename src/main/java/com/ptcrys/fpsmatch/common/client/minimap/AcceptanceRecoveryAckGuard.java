package com.ptcrys.fpsmatch.common.client.minimap;

import com.ptcrys.fpsmatch.core.minimap.wire.RuntimeWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.util.Objects;

final class AcceptanceRecoveryAckGuard {
    private final RuntimeWireMessage.Subscribe subscribe;
    private final WireIdentity.RuntimeIdentity identity;
    private final RuntimeGeneration generation;
    private final Runnable rejected;

    AcceptanceRecoveryAckGuard(
            RuntimeWireMessage.Subscribe subscribe,
            WireIdentity.RuntimeIdentity identity,
            RuntimeGeneration generation,
            Runnable rejected
    ) {
        this.subscribe = Objects.requireNonNull(subscribe, "subscribe");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.generation = Objects.requireNonNull(generation, "generation");
        this.rejected = Objects.requireNonNull(rejected, "rejected");
    }

    Decision classify(RuntimeWireMessage.ScopeAck ack) {
        Objects.requireNonNull(ack, "ack");
        if (!subscribe.requestId().equals(ack.requestId())
                || !subscribe.lease().equals(ack.lease())) {
            return Decision.UNRELATED;
        }
        return identity.equals(ack.runtime()) ? Decision.ACCEPT : Decision.REJECT;
    }

    RuntimeWireMessage.Subscribe subscribe() {
        return subscribe;
    }

    RuntimeGeneration generation() {
        return generation;
    }

    void reject() {
        rejected.run();
    }

    enum Decision {
        UNRELATED,
        ACCEPT,
        REJECT
    }
}
