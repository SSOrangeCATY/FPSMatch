package com.ptcrys.fpsmatch.common.client.minimap;

import com.ptcrys.fpsmatch.common.packet.minimap.MinimapS2CDispatcher;
import com.ptcrys.fpsmatch.core.minimap.wire.RuntimeWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Acceptance-only recovery boundary. The package-private surface keeps lease and
 * transport authority inside FPSMatch while the behavior is driven test-first.
 */
final class ClientMinimapAcceptanceRecovery {
    private ClientMinimapAcceptanceRecovery() {
    }

    static Optional<ReplayAttempt> begin(
            ClientMinimapServices services,
            RuntimeGeneration baseline,
            WireIdentity.RuntimeIdentity identity
    ) {
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(identity, "identity");
        AtomicReference<Status> state = new AtomicReference<>(Status.PENDING);
        return services.beginAcceptanceReplay(
                baseline, identity,
                () -> state.compareAndSet(Status.PENDING, Status.FAILED)
        ).map(start -> new Attempt(services, start, state));
    }

    static RuntimeException rollback(
            RuntimeException primary,
            Runnable... cleanup
    ) {
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(cleanup, "cleanup");
        for (Runnable action : cleanup) {
            try {
                Objects.requireNonNull(action, "cleanup action").run();
            } catch (RuntimeException failure) {
                if (failure != primary) {
                    primary.addSuppressed(failure);
                }
            }
        }
        throw primary;
    }

    enum Status {
        PENDING,
        RECOVERED,
        FAILED
    }

    interface ReplayAttempt {
        RuntimeWireMessage.Subscribe freshSubscribe();

        RuntimeGeneration freshGeneration();

        MinimapS2CDispatcher currentTransport();

        Status status();
    }

    private static final class Attempt implements ReplayAttempt {
        private final ClientMinimapServices services;
        private final ClientMinimapServices.AcceptanceReplayStart start;
        private final AtomicReference<Status> state;

        private Attempt(
                ClientMinimapServices services,
                ClientMinimapServices.AcceptanceReplayStart start,
                AtomicReference<Status> state
        ) {
            this.services = services;
            this.start = start;
            this.state = state;
        }

        @Override
        public RuntimeWireMessage.Subscribe freshSubscribe() {
            return start.subscribe();
        }

        @Override
        public RuntimeGeneration freshGeneration() {
            return start.generation();
        }

        @Override
        public MinimapS2CDispatcher currentTransport() {
            return start.transport();
        }

        @Override
        public Status status() {
            if (state.get() == Status.PENDING
                    && services.runtime().isCurrent(start.generation())
                    && services.hasActiveScope(WireIdentity.Scope.MATCH_HUD)
                    && services.subscriptions().matchHudAvailable()) {
                if (state.compareAndSet(Status.PENDING, Status.RECOVERED)) {
                    services.completeAcceptanceRecovery(start.guard());
                }
            }
            return state.get();
        }
    }
}
