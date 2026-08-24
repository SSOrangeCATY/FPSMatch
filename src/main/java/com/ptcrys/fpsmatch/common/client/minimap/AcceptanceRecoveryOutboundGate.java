package com.ptcrys.fpsmatch.common.client.minimap;

import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.RuntimeWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.util.Objects;
import java.util.function.Consumer;

final class AcceptanceRecoveryOutboundGate implements Consumer<MinimapWireMessage> {
    private final Consumer<MinimapWireMessage> delegate;
    private WireIdentity.MapTarget armedTarget;
    private RuntimeWireMessage.Subscribe held;

    AcceptanceRecoveryOutboundGate(Consumer<MinimapWireMessage> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    synchronized void arm(WireIdentity.MapTarget target) {
        Objects.requireNonNull(target, "target");
        if (armedTarget != null || held != null) {
            throw new IllegalStateException("Acceptance recovery gate is already armed");
        }
        armedTarget = target;
    }

    @Override
    public void accept(MinimapWireMessage message) {
        Objects.requireNonNull(message, "message");
        synchronized (this) {
            if (armedTarget != null
                    && message instanceof RuntimeWireMessage.Subscribe subscribe
                    && subscribe.lease().scope() == WireIdentity.Scope.MATCH_HUD
                    && armedTarget.equals(subscribe.target())) {
                held = subscribe;
                armedTarget = null;
                return;
            }
        }
        delegate.accept(message);
    }

    synchronized RuntimeWireMessage.Subscribe takeExact(
            java.util.UUID requestId,
            WireIdentity.ScopeLease lease
    ) {
        if (held == null
                || !held.requestId().equals(requestId)
                || !held.lease().equals(lease)) {
            throw new IllegalStateException(
                    "Acceptance recovery gate did not hold the correlated Subscribe"
            );
        }
        RuntimeWireMessage.Subscribe subscribe = held;
        held = null;
        return subscribe;
    }

    void forwardConsumed(RuntimeWireMessage.Subscribe subscribe) {
        delegate.accept(Objects.requireNonNull(subscribe, "subscribe"));
    }

    synchronized void clear() {
        armedTarget = null;
        held = null;
    }
}
