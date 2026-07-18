package com.phasetranscrystal.fpsmatch.common.client.minimap;

import com.phasetranscrystal.fpsmatch.common.client.minimap.sync.ClientMinimapS2CDispatcher;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class ClientMinimapSubscriptionCoordinator
        implements ClientMinimapS2CDispatcher.ScopeListener {
    private final SubscriptionRequester requester;
    private final Consumer<WireIdentity.Scope> unsubscriber;
    private WireIdentity.MapTarget matchTarget;
    private UUID matchRequestId;
    private WireIdentity.RuntimeIdentity matchRuntime;
    private Phase matchPhase = Phase.IDLE;
    private UUID tacticalRequestId;
    private Phase tacticalPhase = Phase.IDLE;
    private TacticalListener tacticalListener = TacticalListener.NONE;

    ClientMinimapSubscriptionCoordinator(
            SubscriptionRequester requester,
            Consumer<WireIdentity.Scope> unsubscriber
    ) {
        this.requester = Objects.requireNonNull(requester, "requester");
        this.unsubscriber = Objects.requireNonNull(unsubscriber, "unsubscriber");
    }

    public synchronized Optional<UUID> enterMatch(WireIdentity.MapTarget target) {
        Objects.requireNonNull(target, "target");
        if (target.equals(matchTarget) && matchPhase != Phase.IDLE) {
            return Optional.empty();
        }
        if (matchTarget != null && !target.equals(matchTarget)) {
            closeTactical();
        }
        Optional<UUID> requested = requester.subscribe(
                WireIdentity.Scope.MATCH_HUD,
                target,
                List.of(),
                Optional.empty()
        );
        if (requested.isEmpty()) {
            return Optional.empty();
        }
        matchTarget = target;
        matchRequestId = requested.orElseThrow();
        matchRuntime = null;
        matchPhase = Phase.PENDING;
        return requested;
    }

    public synchronized boolean matchHudAvailable() {
        return matchPhase == Phase.ACTIVE;
    }

    public synchronized Optional<UUID> requestTactical() {
        if (matchPhase != Phase.ACTIVE
                || matchRuntime == null
                || tacticalPhase != Phase.IDLE) {
            return Optional.empty();
        }
        Optional<UUID> requested = requester.subscribe(
                WireIdentity.Scope.TACTICAL_SCREEN,
                matchTarget,
                List.of(),
                Optional.of(new WireIdentity.RuntimeHint(
                        matchRuntime.binding().documentId(),
                        matchRuntime.revision(),
                        matchRuntime.runtimeHash()
                ))
        );
        if (requested.isEmpty()) {
            return Optional.empty();
        }
        tacticalRequestId = requested.orElseThrow();
        tacticalPhase = Phase.PENDING;
        return requested;
    }

    public synchronized void closeTactical() {
        if (tacticalPhase == Phase.IDLE) {
            return;
        }
        unsubscriber.accept(WireIdentity.Scope.TACTICAL_SCREEN);
        tacticalRequestId = null;
        tacticalPhase = Phase.IDLE;
        tacticalListener.closed();
    }

    public synchronized void setTacticalListener(TacticalListener tacticalListener) {
        this.tacticalListener = Objects.requireNonNull(
                tacticalListener, "tacticalListener"
        );
    }

    public synchronized void reset() {
        matchTarget = null;
        matchRequestId = null;
        matchRuntime = null;
        matchPhase = Phase.IDLE;
        tacticalRequestId = null;
        tacticalPhase = Phase.IDLE;
        tacticalListener.closed();
    }

    @Override
    public synchronized void acknowledged(
            UUID requestId,
            WireIdentity.ScopeLease lease,
            WireIdentity.RuntimeIdentity runtime
    ) {
        if (lease.scope() == WireIdentity.Scope.MATCH_HUD
                && matchPhase == Phase.PENDING
                && requestId.equals(matchRequestId)
                && runtime.binding().target().equals(matchTarget)) {
            matchRuntime = runtime;
            matchPhase = Phase.ACTIVE;
        } else if (lease.scope() == WireIdentity.Scope.TACTICAL_SCREEN
                && tacticalPhase == Phase.PENDING
                && requestId.equals(tacticalRequestId)
                && runtime.equals(matchRuntime)) {
            tacticalPhase = Phase.ACTIVE;
            tacticalListener.activated(lease, runtime);
        }
    }

    @Override
    public synchronized void rejected(
            UUID requestId,
            WireIdentity.ScopeLease lease,
            WireStatus.ErrorInfo error
    ) {
        if (lease.scope() == WireIdentity.Scope.MATCH_HUD
                && matchPhase == Phase.PENDING
                && requestId.equals(matchRequestId)) {
            matchRuntime = null;
            matchPhase = Phase.REJECTED;
        } else if (lease.scope() == WireIdentity.Scope.TACTICAL_SCREEN
                && tacticalPhase == Phase.PENDING
                && requestId.equals(tacticalRequestId)) {
            tacticalPhase = Phase.REJECTED;
            tacticalListener.rejected(error);
        }
    }

    @FunctionalInterface
    interface SubscriptionRequester {
        Optional<UUID> subscribe(
                WireIdentity.Scope scope,
                WireIdentity.MapTarget target,
                List<ContainerPath> requiredPaths,
                Optional<WireIdentity.RuntimeHint> runtimeHint
        );
    }

    public interface TacticalListener {
        TacticalListener NONE = new TacticalListener() {
        };

        default void activated(
                WireIdentity.ScopeLease lease,
                WireIdentity.RuntimeIdentity runtime
        ) {
        }

        default void rejected(WireStatus.ErrorInfo error) {
        }

        default void closed() {
        }
    }

    private enum Phase {
        IDLE,
        PENDING,
        ACTIVE,
        REJECTED
    }
}
