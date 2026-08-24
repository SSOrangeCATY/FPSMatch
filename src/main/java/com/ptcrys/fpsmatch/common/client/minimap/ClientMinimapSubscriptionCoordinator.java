package com.ptcrys.fpsmatch.common.client.minimap;

import com.ptcrys.fpsmatch.common.client.minimap.sync.ClientMinimapS2CDispatcher;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;
import com.ptcrys.fpsmatch.core.minimap.wire.WireStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ClientMinimapSubscriptionCoordinator
        implements ClientMinimapS2CDispatcher.ScopeListener {
    private final SubscriptionRequester requester;
    private final Consumer<WireIdentity.Scope> unsubscriber;
    private final Predicate<WireIdentity.Scope> scopeTracker;
    private WireIdentity.MapTarget matchTarget;
    private UUID matchRequestId;
    private WireIdentity.ScopeLease matchLease;
    private WireIdentity.RuntimeIdentity matchRuntime;
    private Phase matchPhase = Phase.IDLE;
    private boolean matchRecoveryAttempted;
    private boolean matchWasReady;
    private HudState matchHudState = HudState.HIDDEN;
    private UUID tacticalRequestId;
    private WireIdentity.ScopeLease tacticalLease;
    private Phase tacticalPhase = Phase.IDLE;
    private boolean deferTacticalFailureCleanup;
    private TacticalListener tacticalListener = TacticalListener.NONE;

    ClientMinimapSubscriptionCoordinator(
            SubscriptionRequester requester,
            Consumer<WireIdentity.Scope> unsubscriber
    ) {
        this(requester, unsubscriber, ignored -> false);
    }

    ClientMinimapSubscriptionCoordinator(
            SubscriptionRequester requester,
            Consumer<WireIdentity.Scope> unsubscriber,
            Predicate<WireIdentity.Scope> scopeTracker
    ) {
        this.requester = Objects.requireNonNull(requester, "requester");
        this.unsubscriber = Objects.requireNonNull(unsubscriber, "unsubscriber");
        this.scopeTracker = Objects.requireNonNull(scopeTracker, "scopeTracker");
    }

    public synchronized Optional<UUID> enterMatch(WireIdentity.MapTarget target) {
        Objects.requireNonNull(target, "target");
        if (target.equals(matchTarget) && holdsLease(matchPhase)) {
            return Optional.empty();
        }
        boolean targetChanged = matchTarget != null && !target.equals(matchTarget);
        if (targetChanged) {
            matchTarget = target;
            matchRequestId = null;
            matchLease = null;
            matchRuntime = null;
            matchPhase = Phase.IDLE;
            matchRecoveryAttempted = false;
            matchWasReady = false;
            matchHudState = HudState.HIDDEN;
            closeTactical();
        } else if (matchTarget == null) {
            matchTarget = target;
        }
        MatchSnapshot beforeRequest = snapshotMatch();
        Optional<UUID> requested;
        try {
            requested = requester.subscribe(
                    WireIdentity.Scope.MATCH_HUD, target, List.of(), Optional.empty()
            );
        } catch (RuntimeException failure) {
            restoreMatch(beforeRequest);
            throw failure;
        }
        if (requested.isEmpty()) {
            restoreMatch(beforeRequest);
            return Optional.empty();
        }
        matchRequestId = requested.orElseThrow();
        matchLease = null;
        matchRuntime = null;
        matchPhase = Phase.PENDING;
        matchRecoveryAttempted = false;
        matchHudState = matchWasReady ? HudState.LOADING : HudState.HIDDEN;
        return requested;
    }

    public synchronized boolean matchHudAvailable() {
        return matchPhase == Phase.ACTIVE;
    }

    public synchronized HudProjection matchHudProjection() {
        Optional<WireIdentity.MapTarget> projectedTarget =
                matchHudState == HudState.HIDDEN
                        ? Optional.empty()
                        : Optional.ofNullable(matchTarget);
        return new HudProjection(matchHudState, projectedTarget);
    }

    public synchronized Optional<UUID> requestTactical() {
        return requestTactical(false);
    }

    /**
     * Starts a tactical request, optionally leaving its pending lease for the
     * caller to cancel after it has published a structured failure.
     */
    public synchronized Optional<UUID> requestTactical(
            boolean deferFailureCleanup
    ) {
        if (matchPhase != Phase.ACTIVE
                || matchRuntime == null
                || holdsLease(tacticalPhase)) {
            return Optional.empty();
        }
        tacticalRequestId = null;
        tacticalLease = null;
        tacticalPhase = Phase.PENDING;
        deferTacticalFailureCleanup = deferFailureCleanup;
        Optional<UUID> requested;
        try {
            requested = requester.subscribe(
                    WireIdentity.Scope.TACTICAL_SCREEN,
                    matchTarget,
                    List.of(),
                    Optional.of(new WireIdentity.RuntimeHint(
                            matchRuntime.binding().documentId(),
                            matchRuntime.revision(),
                            matchRuntime.runtimeHash()
                    ))
            );
        } catch (RuntimeException failure) {
            if (!deferFailureCleanup) {
                clearTacticalRequest();
            }
            throw failure;
        } finally {
            deferTacticalFailureCleanup = false;
        }
        if (requested.isEmpty()) {
            clearTacticalRequest();
            return Optional.empty();
        }
        tacticalRequestId = requested.orElseThrow();
        return requested;
    }

    synchronized boolean deferTacticalFailureCleanup() {
        return deferTacticalFailureCleanup;
    }

    public synchronized void closeTactical() {
        if (tacticalPhase == Phase.IDLE) {
            return;
        }
        boolean wasInvalidated = tacticalPhase == Phase.INVALIDATED;
        boolean hadTacticalLease = holdsLease(tacticalPhase);
        RuntimeException failure = null;
        if (hadTacticalLease) {
            try {
                unsubscriber.accept(WireIdentity.Scope.TACTICAL_SCREEN);
            } catch (RuntimeException exception) {
                failure = exception;
            }
        }
        tacticalRequestId = null;
        tacticalLease = null;
        tacticalPhase = Phase.IDLE;
        if (hadTacticalLease || wasInvalidated) {
            try {
                tacticalListener.closed();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else if (failure != exception) {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void clearTacticalRequest() {
        tacticalRequestId = null;
        tacticalLease = null;
        tacticalPhase = Phase.IDLE;
    }

    /** Leaves the complete match minimap lifecycle and permits same-target re-entry. */
    public synchronized void exitMatch() {
        RuntimeException failure = null;
        try {
            closeTactical();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        boolean releaseHud = ownsMatchLease(matchPhase);
        if (!releaseHud) {
            try {
                releaseHud = scopeTracker.test(WireIdentity.Scope.MATCH_HUD);
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else if (failure != exception) {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (releaseHud) {
            try {
                unsubscriber.accept(WireIdentity.Scope.MATCH_HUD);
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else if (failure != exception) {
                    failure.addSuppressed(exception);
                }
            }
        }
        matchTarget = null;
        matchRequestId = null;
        matchLease = null;
        matchRuntime = null;
        matchPhase = Phase.IDLE;
        matchRecoveryAttempted = false;
        matchWasReady = false;
        matchHudState = HudState.HIDDEN;
        if (failure != null) {
            throw failure;
        }
    }

    public synchronized void setTacticalListener(TacticalListener tacticalListener) {
        this.tacticalListener = Objects.requireNonNull(
                tacticalListener, "tacticalListener"
        );
    }

    public synchronized void reset() {
        boolean hadTacticalLease = holdsLease(tacticalPhase);
        matchTarget = null;
        matchRequestId = null;
        matchLease = null;
        matchRuntime = null;
        matchPhase = Phase.IDLE;
        matchRecoveryAttempted = false;
        matchWasReady = false;
        matchHudState = HudState.HIDDEN;
        tacticalRequestId = null;
        tacticalLease = null;
        tacticalPhase = Phase.IDLE;
        if (hadTacticalLease) {
            tacticalListener.closed();
        }
    }

    public synchronized void beginResourceReload() {
        boolean hadTacticalLease = holdsLease(tacticalPhase);
        matchRequestId = null;
        matchLease = null;
        matchRuntime = null;
        matchPhase = Phase.IDLE;
        matchRecoveryAttempted = false;
        matchHudState = matchWasReady && matchTarget != null
                ? HudState.LOADING
                : HudState.HIDDEN;
        tacticalRequestId = null;
        tacticalLease = null;
        tacticalPhase = Phase.IDLE;
        if (hadTacticalLease) {
            tacticalListener.closed();
        }
    }

    synchronized Optional<WireIdentity.MapTarget> retireForAcceptanceReplay() {
        if (matchTarget == null
                || (matchPhase != Phase.ACTIVE
                && matchPhase != Phase.ACKNOWLEDGED)) {
            return Optional.empty();
        }
        WireIdentity.MapTarget target = matchTarget;
        matchRequestId = null;
        matchLease = null;
        matchRuntime = null;
        matchPhase = Phase.IDLE;
        matchRecoveryAttempted = false;
        matchHudState = matchWasReady ? HudState.LOADING : HudState.HIDDEN;
        tacticalRequestId = null;
        tacticalLease = null;
        tacticalPhase = Phase.IDLE;
        return Optional.of(target);
    }

    synchronized boolean retireAcceptanceAttempt(UUID requestId) {
        Objects.requireNonNull(requestId, "requestId");
        if (!requestId.equals(matchRequestId) || !awaitingMatchAck(matchPhase)) {
            return false;
        }
        matchRequestId = null;
        matchLease = null;
        matchRuntime = null;
        matchPhase = Phase.IDLE;
        matchRecoveryAttempted = false;
        matchHudState = matchWasReady ? HudState.LOADING : HudState.HIDDEN;
        return true;
    }

    @Override
    public synchronized void acknowledged(
            UUID requestId,
            WireIdentity.ScopeLease lease,
            WireIdentity.RuntimeIdentity runtime
    ) {
        if (lease.scope() == WireIdentity.Scope.MATCH_HUD
                && awaitingMatchAck(matchPhase)
                && requestId.equals(matchRequestId)
                && runtime.binding().target().equals(matchTarget)) {
            matchLease = lease;
            matchRuntime = runtime;
            matchPhase = Phase.ACKNOWLEDGED;
            matchHudState = matchWasReady ? HudState.LOADING : HudState.HIDDEN;
        } else if (lease.scope() == WireIdentity.Scope.TACTICAL_SCREEN
                && tacticalPhase == Phase.PENDING
                && requestId.equals(tacticalRequestId)
                && runtime.equals(matchRuntime)) {
            tacticalLease = lease;
            tacticalPhase = Phase.ACKNOWLEDGED;
        }
    }

    @Override
    public synchronized void activated(
            UUID requestId,
            WireIdentity.ScopeLease lease,
            WireIdentity.RuntimeIdentity runtime
    ) {
        if (lease.scope() == WireIdentity.Scope.MATCH_HUD
                && matchPhase == Phase.ACKNOWLEDGED
                && requestId.equals(matchRequestId)
                && lease.equals(matchLease)
                && runtime.equals(matchRuntime)) {
            matchPhase = Phase.ACTIVE;
            matchRecoveryAttempted = false;
            matchWasReady = true;
            matchHudState = HudState.READY;
        } else if (lease.scope() == WireIdentity.Scope.TACTICAL_SCREEN
                && tacticalPhase == Phase.ACKNOWLEDGED
                && requestId.equals(tacticalRequestId)
                && lease.equals(tacticalLease)
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
                && awaitingMatchAck(matchPhase)
                && requestId.equals(matchRequestId)) {
            matchLease = null;
            matchRuntime = null;
            matchPhase = Phase.REJECTED;
            matchHudState = matchWasReady ? HudState.ERROR : HudState.HIDDEN;
        } else if (lease.scope() == WireIdentity.Scope.TACTICAL_SCREEN
                && tacticalPhase == Phase.PENDING
                && requestId.equals(tacticalRequestId)) {
            tacticalLease = null;
            tacticalPhase = Phase.REJECTED;
            tacticalListener.rejected(error);
        }
    }

    @Override
    public synchronized void invalidated(
            WireIdentity.ScopeLease lease,
            WireIdentity.RuntimeIdentity runtime,
            WireStatus.ErrorInfo error
    ) {
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(error, "error");
        WireStatus.RetryDisposition disposition = error.retryDisposition();
        boolean resync = disposition == WireStatus.RetryDisposition.RESYNC_SCOPE;
        boolean retry = resync || disposition == WireStatus.RetryDisposition.RETRY_NEW_REQUEST;
        boolean currentMatch = lease.scope() == WireIdentity.Scope.MATCH_HUD
                && (matchPhase == Phase.ACKNOWLEDGED || matchPhase == Phase.ACTIVE)
                && lease.equals(matchLease)
                && runtime.equals(matchRuntime);
        boolean currentTactical = lease.scope() == WireIdentity.Scope.TACTICAL_SCREEN
                && (tacticalPhase == Phase.ACKNOWLEDGED || tacticalPhase == Phase.ACTIVE)
                && lease.equals(tacticalLease)
                && runtime.equals(matchRuntime);
        if (resync) {
            if (!currentMatch && !currentTactical) {
                return;
            }
            boolean hadTacticalSession = tacticalPhase != Phase.IDLE;
            matchRequestId = null;
            matchLease = null;
            matchRuntime = null;
            matchPhase = Phase.INVALIDATED;
            matchHudState = matchWasReady ? HudState.ERROR : HudState.HIDDEN;
            tacticalRequestId = null;
            tacticalLease = null;
            tacticalPhase = Phase.IDLE;
            if (hadTacticalSession) {
                tacticalListener.closed();
            }
            if (retry) {
                requestAutomaticMatch();
            }
            return;
        }
        if (currentMatch) {
            matchRequestId = null;
            matchLease = null;
            matchRuntime = null;
            matchPhase = Phase.INVALIDATED;
            matchHudState = matchWasReady ? HudState.ERROR : HudState.HIDDEN;
            if (retry) {
                requestAutomaticMatch();
            }
        } else if (currentTactical) {
            tacticalRequestId = null;
            tacticalLease = null;
            tacticalPhase = Phase.IDLE;
            tacticalListener.closed();
        }
    }

    private void requestAutomaticMatch() {
        if (matchTarget == null || matchRecoveryAttempted) {
            return;
        }
        MatchSnapshot beforeRequest = snapshotMatch();
        matchRecoveryAttempted = true;
        Optional<UUID> requested;
        try {
            requested = requester.subscribe(
                    WireIdentity.Scope.MATCH_HUD,
                    matchTarget,
                    List.of(),
                    Optional.empty()
            );
        } catch (RuntimeException failure) {
            restoreMatch(beforeRequest);
            throw failure;
        }
        if (requested.isEmpty()) {
            restoreMatch(beforeRequest);
            return;
        }
        matchRequestId = requested.orElseThrow();
        matchLease = null;
        matchRuntime = null;
        matchPhase = Phase.RECOVERING;
        matchHudState = matchWasReady ? HudState.LOADING : HudState.HIDDEN;
    }

    private MatchSnapshot snapshotMatch() {
        return new MatchSnapshot(
                matchTarget, matchRequestId, matchLease, matchRuntime,
                matchPhase, matchRecoveryAttempted, matchWasReady, matchHudState
        );
    }

    private void restoreMatch(MatchSnapshot snapshot) {
        matchTarget = snapshot.target();
        matchRequestId = snapshot.requestId();
        matchLease = snapshot.lease();
        matchRuntime = snapshot.runtime();
        matchPhase = snapshot.phase();
        matchRecoveryAttempted = snapshot.recoveryAttempted();
        matchWasReady = snapshot.wasReady();
        matchHudState = snapshot.hudState();
    }

    private record MatchSnapshot(
            WireIdentity.MapTarget target,
            UUID requestId,
            WireIdentity.ScopeLease lease,
            WireIdentity.RuntimeIdentity runtime,
            Phase phase,
            boolean recoveryAttempted,
            boolean wasReady,
            HudState hudState
    ) {
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
        ACKNOWLEDGED,
        ACTIVE,
        RECOVERING,
        REJECTED,
        INVALIDATED
    }

    public enum HudState {
        HIDDEN,
        LOADING,
        READY,
        ERROR
    }

    public record HudProjection(
            HudState state,
            Optional<WireIdentity.MapTarget> target
    ) {
        public HudProjection {
            Objects.requireNonNull(state, "state");
            target = Objects.requireNonNull(target, "target");
            if ((state == HudState.HIDDEN) != target.isEmpty()) {
                throw new IllegalArgumentException(
                        "Only a visible HUD state may expose a target"
                );
            }
        }

        public boolean visible() {
            return state != HudState.HIDDEN;
        }
    }

    private static boolean holdsLease(Phase phase) {
        // The dispatcher releases rejected leases, so only live work deduplicates entry.
        return phase == Phase.PENDING
                || phase == Phase.ACKNOWLEDGED
                || phase == Phase.ACTIVE
                || phase == Phase.RECOVERING;
    }

    private static boolean awaitingMatchAck(Phase phase) {
        return phase == Phase.PENDING || phase == Phase.RECOVERING;
    }

    private static boolean ownsMatchLease(Phase phase) {
        return phase == Phase.PENDING
                || phase == Phase.ACKNOWLEDGED
                || phase == Phase.ACTIVE
                || phase == Phase.RECOVERING
                || phase == Phase.INVALIDATED;
    }
}
