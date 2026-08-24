package com.ptcrys.fpsmatch.common.client.minimap.sync;

import com.ptcrys.fpsmatch.common.client.minimap.ClientMinimapRuntime;
import com.ptcrys.fpsmatch.common.client.minimap.MinimapScopeLease;
import com.ptcrys.fpsmatch.common.client.minimap.RuntimeGeneration;
import com.ptcrys.fpsmatch.common.client.minimap.cache.RuntimeEntryStore;
import com.ptcrys.fpsmatch.common.packet.minimap.MinimapS2CDispatcher;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapOpcode;
import com.ptcrys.fpsmatch.core.minimap.marker.ClientMarkerStore;
import com.ptcrys.fpsmatch.core.minimap.marker.MarkerDelta;
import com.ptcrys.fpsmatch.core.minimap.marker.MarkerSnapshot;
import com.ptcrys.fpsmatch.core.minimap.marker.MarkerStreamException;
import com.ptcrys.fpsmatch.core.minimap.format.MinimapContainerLayout;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeEntryDescriptor;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeFloor;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeManifest;
import com.ptcrys.fpsmatch.core.minimap.wire.MarkerWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.EditorWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.PublishWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.RuntimeWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;
import com.ptcrys.fpsmatch.core.minimap.wire.WireMarker;
import com.ptcrys.fpsmatch.core.minimap.wire.WireStatus;
import com.ptcrys.fpsmatch.core.minimap.wire.WireTransfer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.ptcrys.fpsmatch.common.client.minimap.sync.ClientMinimapTransferState.ActiveScope;
import com.ptcrys.fpsmatch.common.client.minimap.sync.ClientMinimapTransferState.PendingEntryRequest;
import com.ptcrys.fpsmatch.common.client.minimap.sync.ClientMinimapTransferState.PendingEntryTransfer;
import com.ptcrys.fpsmatch.common.client.minimap.sync.ClientMinimapTransferState.PendingSubscribe;

public final class ClientMinimapS2CDispatcher implements MinimapS2CDispatcher {
    private static final Logger LOGGER = LogManager.getLogger(
            ClientMinimapS2CDispatcher.class
    );
    private static final long PENDING_SUBSCRIBE_TIMEOUT_MILLIS = 10_000L;
    private static final long RUNTIME_INACTIVITY_TIMEOUT_MILLIS = 10_000L;
    private final ClientMinimapRuntime runtime;
    private final MinimapClientSyncManager syncManager;
    private final ClientMarkerStore markerStore;
    private final Consumer<MinimapWireMessage> sender;
    private final LongSupplier nowMillis;
    private final Supplier<UUID> requestIds;
    private final MarkerResetAccumulator resetAccumulator;
    private final Map<UUID, PendingSubscribe> pendingSubscribes = new HashMap<>();
    private final Map<WireIdentity.Scope, ActiveScope> activeScopes =
            new EnumMap<>(WireIdentity.Scope.class);
    private final Map<WireIdentity.Scope, PendingMarkerReset> pendingMarkerResets =
            new EnumMap<>(WireIdentity.Scope.class);
    private final Map<UUID, PendingEntryRequest> pendingEntryRequests = new HashMap<>();
    private final Set<RecoveryKey> recoveryRequests = new HashSet<>();
    private ScopeListener scopeListener = ScopeListener.NONE;
    private EditorListeners editorListeners = EditorListeners.NONE;
    private Object editorListenerOwner;
    private long transportEpoch;

    public ClientMinimapS2CDispatcher(
            ClientMinimapRuntime runtime,
            MinimapClientSyncManager syncManager,
            ClientMarkerStore markerStore,
            Consumer<MinimapWireMessage> sender,
            LongSupplier nowMillis,
            Supplier<UUID> requestIds,
            MarkerResetAccumulator resetAccumulator
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.syncManager = Objects.requireNonNull(syncManager, "syncManager");
        this.markerStore = Objects.requireNonNull(markerStore, "markerStore");
        this.sender = Objects.requireNonNull(sender, "sender");
        this.nowMillis = Objects.requireNonNull(nowMillis, "nowMillis");
        this.requestIds = Objects.requireNonNull(requestIds, "requestIds");
        this.resetAccumulator = Objects.requireNonNull(
                resetAccumulator, "resetAccumulator"
        );
    }

    public synchronized boolean trackSubscribe(
            RuntimeWireMessage.Subscribe subscribe,
            List<ContainerPath> requiredPaths
    ) {
        Objects.requireNonNull(subscribe, "subscribe");
        Objects.requireNonNull(requiredPaths, "requiredPaths");
        MinimapScopeLease lease = local(subscribe.lease());
        if (!runtime.isPending(lease)) {
            return false;
        }
        pendingSubscribes.entrySet().removeIf(entry ->
                entry.getValue().subscribe().lease().scope() == subscribe.lease().scope()
        );
        pendingSubscribes.put(
                subscribe.requestId(),
                new PendingSubscribe(
                        subscribe,
                        List.copyOf(requiredPaths),
                        safeAdd(nowMillis.getAsLong(), PENDING_SUBSCRIBE_TIMEOUT_MILLIS)
                )
        );
        return true;
    }

    /** Called by the client END tick so a missing ACK cannot keep a lease alive forever. */
    public synchronized void tick() {
        long now = nowMillis.getAsLong();
        List<PendingSubscribe> expired = pendingSubscribes.values().stream()
                .filter(pending -> pending.expiresAtMillis() <= now)
                .toList();
        RuntimeException failure = null;
        for (PendingSubscribe pending : expired) {
            pendingSubscribes.remove(pending.subscribe().requestId());
            try {
                runtime.release(pending.subscribe().lease().scope());
            } catch (RuntimeException exception) {
                failure = accumulate(failure, exception);
            }
            try {
                sender.accept(new RuntimeWireMessage.Unsubscribe(
                        requestIds.get(),
                        pending.subscribe().lease(),
                        pending.subscribe().target()
                ));
            } catch (RuntimeException exception) {
                failure = accumulate(failure, exception);
            }
            try {
                scopeListener.rejected(
                        pending.subscribe().requestId(),
                        pending.subscribe().lease(),
                        new WireStatus.ErrorInfo(
                                MinimapErrorCode.SESSION_EXPIRED.code(),
                                WireStatus.RetryDisposition.DO_NOT_RETRY,
                                "Minimap subscription request timed out"
                        )
                );
            } catch (RuntimeException exception) {
                failure = accumulate(failure, exception);
            }
        }
        Set<WireIdentity.Scope> inactiveScopes = new HashSet<>();
        for (ActiveScope active : activeScopes.values()) {
            if (!active.manifestComplete()
                    && active.manifestDeadlineMillis() <= now) {
                inactiveScopes.add(active.lease().scope());
            }
        }
        for (PendingEntryRequest pending : pendingEntryRequests.values()) {
            if (safeAdd(
                    pending.lastProgressMillis(), RUNTIME_INACTIVITY_TIMEOUT_MILLIS
            ) <= now) {
                inactiveScopes.add(pending.transfer().scope().lease().scope());
            }
        }
        for (WireIdentity.Scope scope : inactiveScopes) {
            ActiveScope active = activeScopes.get(scope);
            if (active == null) {
                continue;
            }
            try {
                timeoutActiveScope(active);
            } catch (RuntimeException exception) {
                failure = accumulate(failure, exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    public synchronized void setScopeListener(ScopeListener scopeListener) {
        this.scopeListener = Objects.requireNonNull(scopeListener, "scopeListener");
    }

    public synchronized void setEditorListeners(Object owner, EditorListeners listeners) {
        Object nextOwner = Objects.requireNonNull(owner, "owner");
        EditorListeners nextListeners = Objects.requireNonNull(listeners, "listeners");
        editorListenerOwner = nextOwner;
        editorListeners = nextListeners;
    }

    public synchronized void clearEditorListeners(Object owner) {
        if (editorListenerOwner == owner) {
            editorListenerOwner = null;
            editorListeners = EditorListeners.NONE;
        }
    }

    @Override
    public synchronized void dispatch(MinimapWireMessage message) {
        Objects.requireNonNull(message, "message");
        try {
            if (message instanceof RuntimeWireMessage.ScopeAck ack) {
                dispatchScopeAck(ack);
            } else if (message instanceof RuntimeWireMessage.Manifest manifest) {
                dispatchManifest(manifest);
            } else if (message instanceof RuntimeWireMessage.EntryFragment fragment) {
                dispatchEntryFragment(fragment);
            } else if (message instanceof MarkerWireMessage.Reset reset) {
                dispatchMarkerReset(reset);
            } else if (message instanceof MarkerWireMessage.Delta delta) {
                dispatchMarkerDelta(delta);
            } else if (message instanceof EditorWireMessage.EditorSession session) {
                editorListeners.session().accept(session);
            } else if (message instanceof EditorWireMessage.EditorAck ack) {
                editorListeners.ack().accept(ack);
            } else if (message instanceof EditorWireMessage.SourceManifest manifest) {
                editorListeners.manifest().accept(manifest);
            } else if (message instanceof EditorWireMessage.SourceFragment fragment) {
                editorListeners.fragment().accept(fragment);
            } else if (message instanceof PublishWireMessage.EditorRebaseResult rebaseResult) {
                editorListeners.rebase().accept(rebaseResult);
            } else if (message instanceof PublishWireMessage.PublishResult publishResult) {
                editorListeners.publish().accept(publishResult);
            } else if (message instanceof PublishWireMessage.PublishStatus publishStatus) {
                editorListeners.publishStatus().accept(publishStatus);
            } else if (message instanceof PublishWireMessage.ErrorMessage error) {
                dispatchError(error);
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            // Hostile or stale messages fail closed on the client thread.
            LOGGER.warn(
                    "Rejected minimap client message {}",
                    message.opcode(),
                    exception
            );
        }
    }

    public synchronized void clear() {
        clearRuntimeState();
        editorListenerOwner = null;
        editorListeners = EditorListeners.NONE;
    }

    public synchronized void retireRuntimeForReplay() {
        clearRuntimeState();
    }

    private void clearRuntimeState() {
        pendingSubscribes.clear();
        activeScopes.clear();
        pendingMarkerResets.clear();
        pendingEntryRequests.clear();
        recoveryRequests.clear();
        resetAccumulator.clear();
        markerStore.clear();
        syncManager.clearTransientState();
    }

    public synchronized boolean hasActiveScope(WireIdentity.Scope scope) {
        Objects.requireNonNull(scope, "scope");
        ActiveScope active = activeScopes.get(scope);
        return active != null
                && active.activated()
                && runtime.canCommit(active.generation(), local(active.lease()));
    }

    public synchronized Optional<WireIdentity.ScopeLease> activeLease(
            WireIdentity.Scope scope
    ) {
        ActiveScope active = activeScopes.get(Objects.requireNonNull(scope, "scope"));
        return active == null ? Optional.empty() : Optional.of(active.lease());
    }

    public synchronized Optional<WireIdentity.MapTarget> activeTarget(
            WireIdentity.Scope scope
    ) {
        ActiveScope active = activeScopes.get(Objects.requireNonNull(scope, "scope"));
        return active == null
                ? Optional.empty()
                : Optional.of(active.identity().binding().target());
    }

    public synchronized Optional<TrackedSubscription> trackedSubscription(
            WireIdentity.Scope scope
    ) {
        Objects.requireNonNull(scope, "scope");
        ActiveScope active = activeScopes.get(scope);
        if (active != null) {
            return Optional.of(new TrackedSubscription(
                    active.lease(), active.identity().binding().target()
            ));
        }
        return pendingSubscribes.values().stream()
                .map(PendingSubscribe::subscribe)
                .filter(subscribe -> subscribe.lease().scope() == scope)
                .findFirst()
                .map(subscribe -> new TrackedSubscription(
                        subscribe.lease(), subscribe.target()
                ));
    }

    public synchronized Optional<RuntimeWireMessage.Subscribe> pendingSubscribe(
            WireIdentity.Scope scope
    ) {
        Objects.requireNonNull(scope, "scope");
        return pendingSubscribes.values().stream()
                .map(PendingSubscribe::subscribe)
                .filter(subscribe -> subscribe.lease().scope() == scope)
                .findFirst();
    }

    public synchronized boolean retireExact(
            UUID requestId,
            WireIdentity.ScopeLease lease
    ) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(lease, "lease");
        PendingSubscribe pending = pendingSubscribes.get(requestId);
        if (pending == null || !pending.subscribe().lease().equals(lease)) {
            return false;
        }
        pendingSubscribes.remove(requestId);
        return true;
    }

    public synchronized List<ActiveSubscription> activeSubscriptions() {
        return activeScopes.values().stream()
                .filter(active -> active.activated() && runtime.canCommit(
                        active.generation(), local(active.lease())
                ))
                .map(active -> new ActiveSubscription(
                        active.lease().scope(), active.lease(),
                        active.identity().binding().target()
                ))
                .toList();
    }

    public synchronized Optional<RuntimeEntryStore.ActiveRuntime> activeRuntime() {
        Optional<RuntimeGeneration> current = runtime.currentGeneration();
        if (current.isEmpty()) {
            return Optional.empty();
        }
        RuntimeGeneration generation = current.orElseThrow();
        boolean leased = activeScopes.values().stream().anyMatch(active ->
                active.activated()
                        && active.generation().equals(generation)
                        && runtime.canCommit(
                        active.generation(), local(active.lease())
                )
        );
        if (!leased) {
            return Optional.empty();
        }
        return syncManager.activeRuntime(generation.mapKey())
                .filter(active -> matches(active, generation));
    }

    public synchronized void forgetActiveScopes() {
        activeScopes.clear();
        pendingMarkerResets.clear();
        pendingSubscribes.clear();
        pendingEntryRequests.clear();
        recoveryRequests.clear();
    }

    public synchronized void forgetScope(WireIdentity.Scope scope) {
        Objects.requireNonNull(scope, "scope");
        activeScopes.remove(scope);
        pendingMarkerResets.remove(scope);
        pendingSubscribes.entrySet().removeIf(entry ->
                entry.getValue().subscribe().lease().scope() == scope
        );
        pendingEntryRequests.entrySet().removeIf(entry ->
                entry.getValue().transfer().scope().lease().scope() == scope
        );
        recoveryRequests.removeIf(key -> key.scope() == scope);
    }

    private void dispatchScopeAck(RuntimeWireMessage.ScopeAck ack) {
        PendingSubscribe pending = pendingSubscribes.get(ack.requestId());
        if (pending == null
                || !pending.subscribe().lease().equals(ack.lease())
                || !pending.subscribe().target().equals(ack.runtime().binding().target())) {
            return;
        }
        MinimapScopeLease lease = local(ack.lease());
        Optional<RuntimeGeneration> acknowledged = runtime.acknowledge(
                lease, ack.runtime()
        );
        if (acknowledged.isEmpty()) {
            return;
        }
        activeScopes.put(
                ack.lease().scope(),
                new ActiveScope(
                        ack.requestId(), ack.lease(), ack.runtime(), acknowledged.orElseThrow(),
                        pending.requiredPaths(),
                        safeAdd(nowMillis.getAsLong(), RUNTIME_INACTIVITY_TIMEOUT_MILLIS)
                )
        );
        pendingSubscribes.remove(ack.requestId());
        scopeListener.acknowledged(ack.requestId(), ack.lease(), ack.runtime());
    }

    private void dispatchError(PublishWireMessage.ErrorMessage error) {
        if (error.lease().filter(lease -> lease.scope() == WireIdentity.Scope.EDITOR).isPresent()) {
            editorListeners.error().accept(error);
            return;
        }
        if (error.requestId().isEmpty()) {
            dispatchInvalidation(error);
            return;
        }
        if (error.lease().isEmpty() || error.failedOpcode().isEmpty()) {
            return;
        }
        int failedOpcode = error.failedOpcode().orElseThrow();
        if (failedOpcode == MinimapOpcode.C2S_REQUEST_ENTRIES.code()) {
            dispatchEntryRequestError(error);
            return;
        }
        if (failedOpcode != MinimapOpcode.C2S_SUBSCRIBE.code()) {
            return;
        }
        UUID requestId = error.requestId().orElseThrow();
        WireIdentity.ScopeLease lease = error.lease().orElseThrow();
        PendingSubscribe pending = pendingSubscribes.get(requestId);
        if (pending == null) {
            dispatchAcknowledgedSubscribeError(requestId, lease, error);
            return;
        }
        if (!pending.subscribe().lease().equals(lease)) {
            return;
        }
        if (error.binding().isPresent()
                && !error.binding().orElseThrow().target().equals(pending.subscribe().target())) {
            return;
        }
        pendingSubscribes.remove(requestId);
        runtime.release(lease.scope());
        scopeListener.rejected(requestId, lease, error.error());
    }

    private void dispatchAcknowledgedSubscribeError(
            UUID requestId,
            WireIdentity.ScopeLease lease,
            PublishWireMessage.ErrorMessage error
    ) {
        if (error.binding().isEmpty()
                || !runtimeInvalidation(error.error().retryDisposition())) {
            return;
        }
        ActiveScope active = activeScopes.get(lease.scope());
        if (active == null
                || !active.subscribeRequestId().equals(requestId)
                || !active.lease().equals(lease)
                || !active.identity().binding().equals(error.binding().orElseThrow())) {
            return;
        }
        invalidateActiveScope(active, error.error());
    }

    private void dispatchEntryRequestError(
            PublishWireMessage.ErrorMessage error
    ) {
        if (error.binding().isEmpty()
                || !runtimeInvalidation(error.error().retryDisposition())) {
            return;
        }
        UUID requestId = error.requestId().orElseThrow();
        WireIdentity.ScopeLease lease = error.lease().orElseThrow();
        PendingEntryRequest pending = pendingEntryRequests.get(requestId);
        if (pending == null) {
            return;
        }
        ActiveScope expected = pending.transfer().scope();
        ActiveScope active = tracked(lease, expected.identity());
        if (active == null
                || !active.equals(expected)
                || !active.identity().binding().equals(
                error.binding().orElseThrow()
        )) {
            return;
        }
        invalidateActiveScope(active, error.error());
    }

    /**
     * Invalidates packet callbacks captured by the previous connection or
     * resource generation and returns a dispatcher bound to the new epoch.
     */
    public synchronized void invalidateTransportEpoch() {
        transportEpoch++;
    }

    public synchronized MinimapS2CDispatcher advanceTransportEpoch() {
        long expectedEpoch = ++transportEpoch;
        return message -> dispatch(expectedEpoch, message);
    }

    private synchronized void dispatch(long expectedEpoch, MinimapWireMessage message) {
        if (expectedEpoch != transportEpoch) {
            return;
        }
        dispatch(message);
    }

    private void dispatchInvalidation(PublishWireMessage.ErrorMessage error) {
        if (error.lease().isEmpty()
                || error.binding().isEmpty()
                || error.failedOpcode().filter(
                        opcode -> opcode == MinimapOpcode.S2C_MARKER_DELTA.code()
                ).isEmpty()) {
            return;
        }
        WireStatus.RetryDisposition disposition = error.error().retryDisposition();
        if (!runtimeInvalidation(disposition)) {
            return;
        }
        WireIdentity.ScopeLease lease = error.lease().orElseThrow();
        ActiveScope active = activeScopes.get(lease.scope());
        if (active == null
                || !active.lease().equals(lease)
                || !active.identity().binding().equals(error.binding().orElseThrow())) {
            return;
        }
        invalidateActiveScope(active, error.error());
    }

    private void invalidateActiveScope(
            ActiveScope active,
            WireStatus.ErrorInfo error
    ) {
        if (!active.equals(activeScopes.get(active.lease().scope()))) {
            return;
        }
        WireIdentity.ScopeLease lease = active.lease();
        WireStatus.RetryDisposition disposition = error.retryDisposition();
        if (disposition == WireStatus.RetryDisposition.RESYNC_SCOPE) {
            clearTracking();
            runtime.beginTransition();
            markerStore.clear();
            resetAccumulator.clear();
            syncManager.clearTransientState();
        } else {
            removeScopeTracking(active);
            runtime.release(lease.scope());
        }
        scopeListener.invalidated(lease, active.identity(), error);
    }

    private void timeoutActiveScope(ActiveScope active) {
        if (!active.equals(activeScopes.get(active.lease().scope()))) {
            return;
        }
        removeScopeTracking(active);
        RuntimeException failure = null;
        try {
            runtime.release(active.lease().scope());
        } catch (RuntimeException exception) {
            failure = accumulate(failure, exception);
        }
        try {
            sender.accept(new RuntimeWireMessage.Unsubscribe(
                    requestIds.get(), active.lease(), active.identity().binding().target()
            ));
        } catch (RuntimeException exception) {
            failure = accumulate(failure, exception);
        }
        try {
            scopeListener.invalidated(
                    active.lease(), active.identity(),
                    new WireStatus.ErrorInfo(
                            MinimapErrorCode.SESSION_EXPIRED.code(),
                            WireStatus.RetryDisposition.RETRY_NEW_REQUEST,
                            "Minimap runtime transfer timed out"
                    )
            );
        } catch (RuntimeException exception) {
            failure = accumulate(failure, exception);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void removeScopeTracking(ActiveScope active) {
        WireIdentity.Scope scope = active.lease().scope();
        activeScopes.remove(scope, active);
        pendingMarkerResets.remove(scope);
        pendingSubscribes.entrySet().removeIf(entry ->
                entry.getValue().subscribe().lease().scope() == scope
        );
        pendingEntryRequests.entrySet().removeIf(entry ->
                entry.getValue().transfer().scope().equals(active)
        );
        recoveryRequests.removeIf(key -> key.scope() == scope);
    }

    private void clearTracking() {
        activeScopes.clear();
        pendingMarkerResets.clear();
        pendingSubscribes.clear();
        pendingEntryRequests.clear();
        recoveryRequests.clear();
    }

    private void dispatchManifest(RuntimeWireMessage.Manifest manifest) {
        ActiveScope scope = tracked(manifest.lease(), manifest.runtime());
        if (scope == null || manifest.requestId().isPresent()
                && !manifest.requestId().orElseThrow().equals(scope.subscribeRequestId())) {
            return;
        }
        long now = nowMillis.getAsLong();
        WireTransfer.TransferFragment transfer = manifest.transfer();
        MinimapClientSyncManager.ManifestFragmentResult accepted =
                syncManager.acceptManifestWithProgress(
                        scope.generation(),
                        transferKey("runtime-manifest.json", transfer),
                        transfer.fragmentIndex(),
                        transfer.fragmentData(),
                        now
                );
        if (!accepted.progressed()) {
            return;
        }
        scope.refreshManifestDeadline(safeAdd(
                now, RUNTIME_INACTIVITY_TIMEOUT_MILLIS
        ));
        if (accepted.manifest().isEmpty()) {
            return;
        }
        List<ContainerPath> requiredPaths = requiredPaths(
                scope.requiredPaths(), accepted.manifest().orElseThrow()
        );
        Optional<List<RuntimeEntryDescriptor>> resolution =
                syncManager.stageCachedRequiredEntries(
                        scope.generation(), requiredPaths
                );
        if (resolution.isEmpty()) {
            invalidateActivation(scope);
            return;
        }
        // Parsed metadata is not activation-ready until its required entries can be staged.
        scope.manifestComplete(requiredPaths);
        List<RuntimeEntryDescriptor> missing = resolution.orElseThrow();
        if (missing.isEmpty()) {
            activateScope(scope);
            return;
        }
        PendingEntryTransfer pendingTransfer = new PendingEntryTransfer(
                scope, requiredPaths, new HashSet<>()
        );
        ArrayList<RuntimeWireMessage.RequestEntries> requests = new ArrayList<>();
        for (int start = 0; start < missing.size();
             start += MinimapHardLimits.MAX_ENTRY_REQUESTS) {
            int end = Math.min(
                    start + MinimapHardLimits.MAX_ENTRY_REQUESTS, missing.size()
            );
            UUID requestId = requestIds.get();
            java.util.LinkedHashMap<ContainerPath, RuntimeEntryDescriptor> remaining =
                    new java.util.LinkedHashMap<>();
            ArrayList<WireTransfer.EntryRequest> entries = new ArrayList<>(end - start);
            for (RuntimeEntryDescriptor descriptor : missing.subList(start, end)) {
                remaining.put(descriptor.path(), descriptor);
                entries.add(new WireTransfer.EntryRequest(
                        descriptor.path(), descriptor.sha256()
                ));
            }
            pendingTransfer.requestIds().add(requestId);
            pendingEntryRequests.put(
                    requestId,
                    new PendingEntryRequest(pendingTransfer, remaining, now)
            );
            requests.add(new RuntimeWireMessage.RequestEntries(
                    requestId, scope.lease(), scope.identity(), entries
            ));
        }
        requests.forEach(sender);
    }

    private void dispatchEntryFragment(RuntimeWireMessage.EntryFragment fragment) {
        PendingEntryRequest pending = pendingEntryRequests.get(fragment.requestId());
        if (pending == null) {
            return;
        }
        ActiveScope scope = tracked(fragment.lease(), fragment.runtime());
        RuntimeEntryDescriptor descriptor = pending.remaining().get(fragment.path());
        if (scope == null || !scope.equals(pending.transfer().scope()) || descriptor == null
                || !descriptor.sha256().equals(fragment.transfer().objectHash())) {
            return;
        }
        long now = nowMillis.getAsLong();
        WireTransfer.TransferFragment transfer = fragment.transfer();
        MinimapClientSyncManager.EntryFragmentResult accepted =
                syncManager.acceptEntryWithProgress(
                scope.generation(), fragment.path(),
                transferKey(fragment.path().value(), transfer),
                transfer.fragmentIndex(), transfer.fragmentData(), now
        );
        if (!accepted.progressed()) {
            return;
        }
        pending.transfer().requestIds().stream()
                .map(pendingEntryRequests::get)
                .filter(Objects::nonNull)
                .forEach(request -> request.progressed(now));
        if (accepted.payload().isEmpty()) {
            return;
        }
        pending.remaining().remove(fragment.path());
        if (!pending.remaining().isEmpty()) {
            return;
        }
        pendingEntryRequests.remove(fragment.requestId());
        pending.transfer().requestIds().remove(fragment.requestId());
        if (pending.transfer().requestIds().isEmpty()) {
            activateScope(scope);
        }
    }

    private void activateScope(ActiveScope scope) {
        if (!scope.equals(activeScopes.get(scope.lease().scope())) || scope.activated()) {
            return;
        }
        if (!syncManager.activateGeneration(scope.generation(), scope.requiredPaths())) {
            invalidateActivation(scope);
            return;
        }
        scope.markActivated();
        replayPendingMarkerReset(scope);
        recoveryRequests.removeIf(key -> key.scope() == scope.lease().scope());
        scopeListener.activated(
                scope.subscribeRequestId(), scope.lease(), scope.identity()
        );
    }

    private void invalidateActivation(ActiveScope scope) {
        invalidateActiveScope(
                scope,
                new WireStatus.ErrorInfo(
                        MinimapErrorCode.VALIDATION_FAILED.code(),
                        WireStatus.RetryDisposition.DO_NOT_RETRY,
                        "Minimap runtime activation failed validation"
                )
        );
    }

    private void dispatchMarkerReset(MarkerWireMessage.Reset reset) {
        ActiveScope scope = tracked(reset.lease(), reset.runtime());
        if (scope == null) {
            return;
        }
        Optional<List<WireMarker.Marker>> assembled = resetAccumulator.accept(
                reset, nowMillis.getAsLong()
        );
        if (assembled.isEmpty()) {
            return;
        }
        List<MarkerSnapshot.Marker> markers = assembled.orElseThrow().stream()
                .map(ClientMinimapS2CDispatcher::marker)
                .toList();
        PendingMarkerReset pending = new PendingMarkerReset(
                scope.lease(), scope.identity(), reset.streamEpoch(),
                reset.sequence(), markers
        );
        if (!scope.activated()) {
            // The server can send the initial reset before runtime entries finish; retain
            // the validated snapshot until the same lease is activated.
            pendingMarkerResets.put(scope.lease().scope(), pending);
        } else {
            applyMarkerReset(scope, pending);
        }
        recoveryRequests.remove(new RecoveryKey(
                reset.lease().scope(), scope.generation().localGeneration()
        ));
    }

    private void replayPendingMarkerReset(ActiveScope scope) {
        PendingMarkerReset pending = pendingMarkerResets.remove(
                scope.lease().scope()
        );
        if (pending != null && pending.matches(scope)) {
            applyMarkerReset(scope, pending);
        }
    }

    private void applyMarkerReset(
            ActiveScope scope,
            PendingMarkerReset pending
    ) {
        runtime.commitIfCurrent(
                scope.generation(),
                new MinimapScopeLease[] {local(scope.lease())},
                () -> markerStore.applyReset(
                        pending.streamEpoch(), pending.sequence(), pending.markers()
                )
        );
    }

    private void dispatchMarkerDelta(MarkerWireMessage.Delta delta) {
        ActiveScope scope = active(delta.lease(), delta.runtime());
        if (scope == null) {
            return;
        }
        List<MarkerDelta> operations = delta.operations().stream()
                .map(ClientMinimapS2CDispatcher::operation)
                .toList();
        try {
            runtime.commitIfCurrent(
                    scope.generation(),
                    new MinimapScopeLease[] {local(scope.lease())},
                    () -> markerStore.applyDelta(
                            delta.streamEpoch(), delta.sequence(), operations
                    )
            );
        } catch (MarkerStreamException exception) {
            requestMarkerReset(scope);
        }
    }

    private ActiveScope active(
            WireIdentity.ScopeLease lease,
            WireIdentity.RuntimeIdentity identity
    ) {
        ActiveScope scope = tracked(lease, identity);
        return scope != null && scope.activated() ? scope : null;
    }

    private ActiveScope tracked(
            WireIdentity.ScopeLease lease,
            WireIdentity.RuntimeIdentity identity
    ) {
        ActiveScope scope = activeScopes.get(lease.scope());
        if (scope == null
                || !scope.lease().equals(lease)
                || !scope.identity().equals(identity)
                || !runtime.canCommit(scope.generation(), local(lease))) {
            return null;
        }
        return scope;
    }

    private void requestMarkerReset(ActiveScope scope) {
        RecoveryKey key = new RecoveryKey(
                scope.lease().scope(), scope.generation().localGeneration()
        );
        if (!recoveryRequests.add(key)) {
            return;
        }
        Optional<WireIdentity.MarkerStreamCursor> cursor = markerStore.streamEpoch() == null
                || markerStore.lastSequence() < 0
                ? Optional.empty()
                : Optional.of(new WireIdentity.MarkerStreamCursor(
                        markerStore.streamEpoch(), markerStore.lastSequence()
                ));
        sender.accept(new RuntimeWireMessage.RequestMarkerReset(
                requestIds.get(), scope.lease(), scope.identity(), cursor
        ));
    }

    private static MarkerSnapshot.Marker marker(WireMarker.Marker marker) {
        return new MarkerSnapshot.Marker(
                marker.markerId(), marker.typeId(), marker.styleId(),
                marker.x(), marker.y(), marker.z(), marker.yaw(), marker.updatedTick(),
                marker.expiresTick(), marker.floorSlug(), marker.stateFields()
        );
    }

    private static MarkerDelta operation(WireMarker.DeltaOperation operation) {
        if (operation instanceof WireMarker.Add add) {
            return new MarkerDelta.Add(marker(add.marker()));
        }
        if (operation instanceof WireMarker.Update update) {
            return new MarkerDelta.Update(marker(update.marker()));
        }
        return new MarkerDelta.Remove(((WireMarker.Remove) operation).markerId());
    }

    private static MinimapScopeLease local(WireIdentity.ScopeLease lease) {
        return new MinimapScopeLease(
                lease.scope(), lease.scopeEpoch(), lease.runtimeGeneration()
        );
    }

    private static boolean matches(
            RuntimeEntryStore.ActiveRuntime active,
            RuntimeGeneration generation
    ) {
        return active.serverIdentity().equals(generation.serverIdentity())
                && active.dimension().equals(generation.dimension())
                && active.mapKey().equals(generation.mapKey())
                && active.documentId().equals(generation.documentId())
                && active.revision() == generation.revision()
                && active.runtimeHash().equals(generation.runtimeHash());
    }

    private static List<ContainerPath> requiredPaths(
            List<ContainerPath> requested,
            RuntimeManifest manifest
    ) {
        if (!requested.isEmpty() || manifest.floors().isEmpty()) {
            return requested;
        }
        // Floor changes are client-local, so an empty viewport must seed every selectable floor.
        Map<String, Integer> lowestZoomByFloor = new HashMap<>();
        for (RuntimeFloor floor : manifest.floors()) {
            lowestZoomByFloor.put(floor.selection().id(), floor.zoomLevels() - 1);
        }
        return manifest.entries().stream()
                .map(RuntimeEntryDescriptor::path)
                .filter(path -> MinimapContainerLayout.parseRuntimeTile(path)
                        .map(address -> {
                            Integer zoom = lowestZoomByFloor.get(address.floorId());
                            return zoom != null && address.zoom() == zoom;
                        })
                        .orElse(false))
                .toList();
    }

    private static TransferKey transferKey(
            String stablePath,
            WireTransfer.TransferFragment transfer
    ) {
        return new TransferKey(
                stablePath,
                transfer.objectHash(),
                Math.toIntExact(transfer.totalLength()),
                transfer.fragmentCount()
        );
    }

    private record PendingMarkerReset(
            WireIdentity.ScopeLease lease,
            WireIdentity.RuntimeIdentity runtime,
            UUID streamEpoch,
            long sequence,
            List<MarkerSnapshot.Marker> markers
    ) {
        private PendingMarkerReset {
            Objects.requireNonNull(lease, "lease");
            Objects.requireNonNull(runtime, "runtime");
            Objects.requireNonNull(streamEpoch, "streamEpoch");
            markers = List.copyOf(Objects.requireNonNull(markers, "markers"));
        }

        private boolean matches(ActiveScope scope) {
            return lease.equals(scope.lease()) && runtime.equals(scope.identity());
        }
    }

    private record RecoveryKey(WireIdentity.Scope scope, long runtimeGeneration) {
    }

    public record ActiveSubscription(
            WireIdentity.Scope scope,
            WireIdentity.ScopeLease lease,
            WireIdentity.MapTarget target
    ) {
    }

    public record TrackedSubscription(
            WireIdentity.ScopeLease lease,
            WireIdentity.MapTarget target
    ) {
    }

    public record EditorListeners(
            Consumer<EditorWireMessage.EditorSession> session,
            Consumer<EditorWireMessage.EditorAck> ack,
            Consumer<EditorWireMessage.SourceManifest> manifest,
            Consumer<EditorWireMessage.SourceFragment> fragment,
            Consumer<PublishWireMessage.EditorRebaseResult> rebase,
            Consumer<PublishWireMessage.ErrorMessage> error,
            Consumer<PublishWireMessage.PublishResult> publish,
            Consumer<PublishWireMessage.PublishStatus> publishStatus
    ) {
        public static final EditorListeners NONE = new EditorListeners(
                ignored -> { }, ignored -> { }, ignored -> { }, ignored -> { },
                ignored -> { }, ignored -> { }, ignored -> { }, ignored -> { }
        );

        public EditorListeners(
                Consumer<EditorWireMessage.EditorSession> session,
                Consumer<EditorWireMessage.EditorAck> ack,
                Consumer<EditorWireMessage.SourceManifest> manifest,
                Consumer<EditorWireMessage.SourceFragment> fragment,
                Consumer<PublishWireMessage.EditorRebaseResult> rebase,
                Consumer<PublishWireMessage.ErrorMessage> error,
                Consumer<PublishWireMessage.PublishResult> publish
        ) {
            this(session, ack, manifest, fragment, rebase, error, publish, ignored -> { });
        }

        public EditorListeners {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(ack, "ack");
            Objects.requireNonNull(manifest, "manifest");
            Objects.requireNonNull(fragment, "fragment");
            Objects.requireNonNull(rebase, "rebase");
            Objects.requireNonNull(error, "error");
            Objects.requireNonNull(publish, "publish");
            Objects.requireNonNull(publishStatus, "publishStatus");
        }

    }

    public interface ScopeListener {
        ScopeListener NONE = new ScopeListener() {
        };

        default void acknowledged(
                UUID requestId,
                WireIdentity.ScopeLease lease,
                WireIdentity.RuntimeIdentity runtime
        ) {
        }

        default void activated(
                UUID requestId,
                WireIdentity.ScopeLease lease,
                WireIdentity.RuntimeIdentity runtime
        ) {
        }

        default void rejected(
                UUID requestId,
                WireIdentity.ScopeLease lease,
                WireStatus.ErrorInfo error
        ) {
        }

        default void invalidated(
                WireIdentity.ScopeLease lease,
                WireIdentity.RuntimeIdentity runtime,
                WireStatus.ErrorInfo error
        ) {
        }
    }

    private static long safeAdd(long value, long delta) {
        if (delta > 0 && value > Long.MAX_VALUE - delta) {
            return Long.MAX_VALUE;
        }
        return value + delta;
    }

    private static boolean runtimeInvalidation(
            WireStatus.RetryDisposition disposition
    ) {
        return disposition == WireStatus.RetryDisposition.DO_NOT_RETRY
                || disposition == WireStatus.RetryDisposition.RETRY_NEW_REQUEST
                || disposition == WireStatus.RetryDisposition.RESYNC_SCOPE;
    }

    private static RuntimeException accumulate(
            RuntimeException current,
            RuntimeException next
    ) {
        if (current == null) {
            return next;
        }
        if (current != next) {
            current.addSuppressed(next);
        }
        return current;
    }
}
