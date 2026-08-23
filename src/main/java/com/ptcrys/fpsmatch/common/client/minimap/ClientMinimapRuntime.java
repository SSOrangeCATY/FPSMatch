package com.ptcrys.fpsmatch.common.client.minimap;

import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client-side generation and scope-lease state machine.
 * Pure Java: no Minecraft client types.
 */
public final class ClientMinimapRuntime {
    private final AtomicLong connectionEpoch = new AtomicLong();
    private final AtomicLong localGeneration = new AtomicLong();
    private final Map<WireIdentity.Scope, ScopeState> scopes = new EnumMap<>(WireIdentity.Scope.class);

    private RuntimeGeneration current;
    private String serverIdentity;
    private long pendingGeneration;
    private boolean transitionPending;
    private boolean loggedIn;

    private ClientMinimapRuntime() {
        for (WireIdentity.Scope scope : WireIdentity.Scope.values()) {
            scopes.put(scope, new ScopeState());
        }
    }

    public static ClientMinimapRuntime create() {
        return new ClientMinimapRuntime();
    }

    public synchronized RuntimeGeneration login(
            String serverIdentity,
            MapKey mapKey,
            NamespacedId documentId,
            long revision,
            Sha256 runtimeHash
    ) {
        connect(serverIdentity);
        current = new RuntimeGeneration(
                connectionEpoch.get(),
                serverIdentity,
                mapKey,
                documentId,
                revision,
                runtimeHash,
                NamespacedId.parse("minecraft:overworld"),
                pendingGeneration
        );
        transitionPending = false;
        return current;
    }

    public synchronized void connect(String serverIdentity) {
        this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity");
        connectionEpoch.incrementAndGet();
        invalidateAllScopes();
        current = null;
        pendingGeneration = localGeneration.incrementAndGet();
        transitionPending = true;
        loggedIn = true;
    }

    public synchronized void logout() {
        loggedIn = false;
        invalidateAllScopes();
        current = null;
        serverIdentity = null;
        pendingGeneration = 0L;
        transitionPending = false;
    }

    public synchronized void beginTransition() {
        requireConnected();
        invalidateAllScopes();
        pendingGeneration = localGeneration.incrementAndGet();
        transitionPending = true;
    }

    public synchronized RuntimeGeneration switchMap(
            MapKey mapKey,
            NamespacedId documentId,
            long revision,
            Sha256 runtimeHash
    ) {
        requireLoggedIn();
        invalidateAllScopes();
        current = new RuntimeGeneration(
                current.connectionEpoch(),
                current.serverIdentity(),
                mapKey,
                documentId,
                revision,
                runtimeHash,
                current.dimension(),
                localGeneration.incrementAndGet()
        );
        transitionPending = false;
        return current;
    }

    public synchronized RuntimeGeneration switchDimension(NamespacedId dimension) {
        requireLoggedIn();
        Objects.requireNonNull(dimension, "dimension");
        invalidateAllScopes();
        current = new RuntimeGeneration(
                current.connectionEpoch(),
                current.serverIdentity(),
                current.mapKey(),
                current.documentId(),
                current.revision(),
                current.runtimeHash(),
                dimension,
                localGeneration.incrementAndGet()
        );
        transitionPending = false;
        return current;
    }

    public synchronized RuntimeGeneration bumpRevision(long revision, Sha256 runtimeHash) {
        requireLoggedIn();
        invalidateAllScopes();
        current = new RuntimeGeneration(
                current.connectionEpoch(),
                current.serverIdentity(),
                current.mapKey(),
                current.documentId(),
                revision,
                runtimeHash,
                current.dimension(),
                localGeneration.incrementAndGet()
        );
        transitionPending = false;
        return current;
    }

    public synchronized void reloadResources() {
        if (!loggedIn || current == null) {
            return;
        }
        invalidateAllScopes();
        current = new RuntimeGeneration(
                current.connectionEpoch(),
                current.serverIdentity(),
                current.mapKey(),
                current.documentId(),
                current.revision(),
                current.runtimeHash(),
                current.dimension(),
                localGeneration.incrementAndGet()
        );
        transitionPending = false;
    }

    public synchronized void resetAll() {
        invalidateAllScopes();
        if (loggedIn && current != null) {
            current = new RuntimeGeneration(
                    current.connectionEpoch(),
                    current.serverIdentity(),
                    current.mapKey(),
                    current.documentId(),
                    current.revision(),
                    current.runtimeHash(),
                    current.dimension(),
                    localGeneration.incrementAndGet()
            );
            transitionPending = false;
        } else {
            current = null;
            if (loggedIn && serverIdentity != null) {
                pendingGeneration = localGeneration.incrementAndGet();
                transitionPending = true;
            } else {
                loggedIn = false;
                transitionPending = false;
            }
        }
    }

    public synchronized MinimapScopeLease acquire(WireIdentity.Scope scope) {
        requireLoggedIn();
        Objects.requireNonNull(scope, "scope");
        ScopeState state = scopes.get(scope);
        state.active = true;
        state.acknowledged = true;
        state.scopeEpoch += 1L;
        return new MinimapScopeLease(scope, state.scopeEpoch, current.localGeneration());
    }

    public synchronized MinimapScopeLease acquirePending(WireIdentity.Scope scope) {
        requireConnected();
        Objects.requireNonNull(scope, "scope");
        ScopeState state = scopes.get(scope);
        state.active = true;
        state.acknowledged = false;
        state.scopeEpoch += 1L;
        long generation = current == null ? pendingGeneration : current.localGeneration();
        if (transitionPending) {
            generation = pendingGeneration;
        }
        return new MinimapScopeLease(scope, state.scopeEpoch, generation);
    }

    public synchronized boolean isPending(MinimapScopeLease lease) {
        if (!loggedIn || lease == null) {
            return false;
        }
        ScopeState state = scopes.get(lease.scope());
        long expectedGeneration = transitionPending
                ? pendingGeneration
                : current == null ? pendingGeneration : current.localGeneration();
        return state.active
                && !state.acknowledged
                && state.scopeEpoch == lease.scopeEpoch()
                && lease.runtimeGeneration() == expectedGeneration;
    }

    public synchronized Optional<RuntimeGeneration> acknowledge(
            MinimapScopeLease lease,
            WireIdentity.RuntimeIdentity identity
    ) {
        Objects.requireNonNull(identity, "identity");
        if (!isPending(lease)) {
            return Optional.empty();
        }
        ScopeState state = scopes.get(lease.scope());
        if (current != null && !transitionPending) {
            if (!matches(current, identity)) {
                return Optional.empty();
            }
            state.acknowledged = true;
            return Optional.of(current);
        }
        WireIdentity.DocumentBinding binding = identity.binding();
        current = new RuntimeGeneration(
                connectionEpoch.get(),
                serverIdentity,
                binding.target().mapKey(),
                binding.documentId(),
                identity.revision(),
                identity.runtimeHash(),
                binding.target().dimension(),
                pendingGeneration
        );
        transitionPending = false;
        state.acknowledged = true;
        return Optional.of(current);
    }

    public synchronized void release(WireIdentity.Scope scope) {
        Objects.requireNonNull(scope, "scope");
        ScopeState state = scopes.get(scope);
        state.active = false;
        state.acknowledged = false;
        // Epoch advances on reopen, not on release, so old lease remains invalid once reopened.
        // Keep epoch stable on release so reopen can strictly increase it.
    }

    public synchronized boolean isCurrent(RuntimeGeneration generation) {
        return loggedIn && current != null && current.equals(generation);
    }

    public synchronized Optional<RuntimeGeneration> currentGeneration() {
        return Optional.ofNullable(current);
    }

    public synchronized boolean canCommit(RuntimeGeneration generation, MinimapScopeLease lease) {
        if (!isCurrent(generation) || lease == null) {
            return false;
        }
        ScopeState state = scopes.get(lease.scope());
        return state.active
                && state.acknowledged
                && state.scopeEpoch == lease.scopeEpoch()
                && lease.runtimeGeneration() == generation.localGeneration();
    }

    public synchronized boolean commitIfCurrent(
            RuntimeGeneration generation,
            MinimapScopeLease[] leases,
            Runnable action
    ) {
        Objects.requireNonNull(action, "action");
        if (!isCurrent(generation)) {
            return false;
        }
        boolean anyValid = false;
        if (leases != null) {
            for (MinimapScopeLease lease : leases) {
                if (canCommit(generation, lease)) {
                    anyValid = true;
                    break;
                }
            }
        }
        if (!anyValid) {
            return false;
        }
        action.run();
        return true;
    }

    public synchronized boolean commitScreenIfCurrent(
            RuntimeGeneration generation,
            WireIdentity.Scope screenScope,
            MinimapScopeLease originalLease,
            Runnable action
    ) {
        Objects.requireNonNull(screenScope, "screenScope");
        Objects.requireNonNull(action, "action");
        if (!isCurrent(generation) || originalLease == null || originalLease.scope() != screenScope) {
            return false;
        }
        if (!canCommit(generation, originalLease)) {
            return false;
        }
        action.run();
        return true;
    }

    private void requireLoggedIn() {
        if (!loggedIn || current == null) {
            throw new IllegalStateException("Minimap runtime is not logged in");
        }
    }

    private void requireConnected() {
        if (!loggedIn || serverIdentity == null) {
            throw new IllegalStateException("Minimap runtime is not connected");
        }
    }

    private void invalidateAllScopes() {
        for (ScopeState state : scopes.values()) {
            state.active = false;
            state.acknowledged = false;
            // Keep epoch so reopen can advance; do not reset to zero mid-session.
        }
    }

    private static final class ScopeState {
        private long scopeEpoch;
        private boolean active;
        private boolean acknowledged;
    }

    private static boolean matches(
            RuntimeGeneration generation,
            WireIdentity.RuntimeIdentity identity
    ) {
        WireIdentity.DocumentBinding binding = identity.binding();
        return generation.mapKey().equals(binding.target().mapKey())
                && generation.dimension().equals(binding.target().dimension())
                && generation.documentId().equals(binding.documentId())
                && generation.revision() == identity.revision()
                && generation.runtimeHash().equals(identity.runtimeHash());
    }
}
