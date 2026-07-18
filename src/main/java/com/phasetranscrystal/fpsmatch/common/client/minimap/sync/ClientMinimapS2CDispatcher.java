package com.phasetranscrystal.fpsmatch.common.client.minimap.sync;

import com.phasetranscrystal.fpsmatch.common.client.minimap.ClientMinimapRuntime;
import com.phasetranscrystal.fpsmatch.common.client.minimap.MinimapScopeLease;
import com.phasetranscrystal.fpsmatch.common.client.minimap.RuntimeGeneration;
import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.RuntimeEntryStore;
import com.phasetranscrystal.fpsmatch.common.packet.minimap.MinimapS2CDispatcher;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapOpcode;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.ClientMarkerStore;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.MarkerDelta;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.MarkerSnapshot;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.MarkerStreamException;
import com.phasetranscrystal.fpsmatch.core.minimap.format.MinimapContainerLayout;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeEntryDescriptor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeFloor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeManifest;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MarkerWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.PublishWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.RuntimeWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireMarker;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireStatus;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireTransfer;

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

public final class ClientMinimapS2CDispatcher implements MinimapS2CDispatcher {
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
    private final Map<UUID, PendingEntryRequest> pendingEntryRequests = new HashMap<>();
    private final Set<RecoveryKey> recoveryRequests = new HashSet<>();
    private ScopeListener scopeListener = ScopeListener.NONE;

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
                new PendingSubscribe(subscribe, List.copyOf(requiredPaths))
        );
        return true;
    }

    public synchronized void setScopeListener(ScopeListener scopeListener) {
        this.scopeListener = Objects.requireNonNull(scopeListener, "scopeListener");
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
            } else if (message instanceof PublishWireMessage.ErrorMessage error) {
                dispatchError(error);
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            // Hostile or stale messages fail closed on the client thread.
        }
    }

    public synchronized void clear() {
        pendingSubscribes.clear();
        activeScopes.clear();
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

    public synchronized List<ActiveSubscription> activeSubscriptions() {
        return activeScopes.values().stream()
                .filter(active -> runtime.canCommit(
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
                active.generation().equals(generation)
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
        pendingSubscribes.clear();
        pendingEntryRequests.clear();
        recoveryRequests.clear();
    }

    public synchronized void forgetScope(WireIdentity.Scope scope) {
        Objects.requireNonNull(scope, "scope");
        activeScopes.remove(scope);
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
                        pending.requiredPaths()
                )
        );
        pendingSubscribes.remove(ack.requestId());
        recoveryRequests.removeIf(key -> key.scope() == ack.lease().scope());
        scopeListener.acknowledged(ack.requestId(), ack.lease(), ack.runtime());
    }

    private void dispatchError(PublishWireMessage.ErrorMessage error) {
        if (error.requestId().isEmpty()
                || error.lease().isEmpty()
                || error.failedOpcode().filter(
                        opcode -> opcode == MinimapOpcode.C2S_SUBSCRIBE.code()
                ).isEmpty()) {
            return;
        }
        UUID requestId = error.requestId().orElseThrow();
        WireIdentity.ScopeLease lease = error.lease().orElseThrow();
        PendingSubscribe pending = pendingSubscribes.get(requestId);
        if (pending == null || !pending.subscribe().lease().equals(lease)) {
            return;
        }
        pendingSubscribes.remove(requestId);
        runtime.release(lease.scope());
        scopeListener.rejected(requestId, lease, error.error());
    }

    private void dispatchManifest(RuntimeWireMessage.Manifest manifest) {
        ActiveScope scope = active(manifest.lease(), manifest.runtime());
        if (scope == null || manifest.requestId().isPresent()
                && !manifest.requestId().orElseThrow().equals(scope.subscribeRequestId())) {
            return;
        }
        WireTransfer.TransferFragment transfer = manifest.transfer();
        Optional<com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeManifest> accepted =
                syncManager.acceptManifest(
                        scope.generation(),
                        transferKey("runtime-manifest.json", transfer),
                        transfer.fragmentIndex(),
                        transfer.fragmentData(),
                        nowMillis.getAsLong()
                );
        if (accepted.isEmpty()) {
            return;
        }
        List<ContainerPath> requiredPaths = requiredPaths(
                scope.requiredPaths(), accepted.orElseThrow()
        );
        Optional<List<RuntimeEntryDescriptor>> resolution =
                syncManager.stageCachedRequiredEntries(
                        scope.generation(), requiredPaths
                );
        if (resolution.isEmpty()) {
            return;
        }
        List<RuntimeEntryDescriptor> missing = resolution.orElseThrow();
        if (missing.isEmpty()) {
            syncManager.activateGeneration(scope.generation(), requiredPaths);
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
                    new PendingEntryRequest(pendingTransfer, remaining)
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
        ActiveScope scope = active(fragment.lease(), fragment.runtime());
        RuntimeEntryDescriptor descriptor = pending.remaining().get(fragment.path());
        if (scope == null || !scope.equals(pending.transfer().scope()) || descriptor == null
                || !descriptor.sha256().equals(fragment.transfer().objectHash())) {
            return;
        }
        WireTransfer.TransferFragment transfer = fragment.transfer();
        Optional<byte[]> accepted = syncManager.acceptEntry(
                scope.generation(), fragment.path(),
                transferKey(fragment.path().value(), transfer),
                transfer.fragmentIndex(), transfer.fragmentData(), nowMillis.getAsLong()
        );
        if (accepted.isEmpty()) {
            return;
        }
        pending.remaining().remove(fragment.path());
        if (!pending.remaining().isEmpty()) {
            return;
        }
        pendingEntryRequests.remove(fragment.requestId());
        pending.transfer().requestIds().remove(fragment.requestId());
        if (pending.transfer().requestIds().isEmpty()) {
            syncManager.activateGeneration(
                    scope.generation(), pending.transfer().requiredPaths()
            );
        }
    }

    private void dispatchMarkerReset(MarkerWireMessage.Reset reset) {
        ActiveScope scope = active(reset.lease(), reset.runtime());
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
        runtime.commitIfCurrent(
                scope.generation(),
                new MinimapScopeLease[] {local(scope.lease())},
                () -> markerStore.applyReset(reset.streamEpoch(), reset.sequence(), markers)
        );
        recoveryRequests.remove(new RecoveryKey(
                reset.lease().scope(), scope.generation().localGeneration()
        ));
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
        RuntimeFloor floor = manifest.floors().get(0);
        int zoom = floor.zoomLevels() - 1;
        return manifest.entries().stream()
                .map(RuntimeEntryDescriptor::path)
                .filter(path -> MinimapContainerLayout.parseRuntimeTile(path)
                        .filter(address -> address.floorId().equals(floor.selection().id())
                                && address.zoom() == zoom)
                        .isPresent())
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

    private record PendingSubscribe(
            RuntimeWireMessage.Subscribe subscribe,
            List<ContainerPath> requiredPaths
    ) {
    }

    private record ActiveScope(
            UUID subscribeRequestId,
            WireIdentity.ScopeLease lease,
            WireIdentity.RuntimeIdentity identity,
            RuntimeGeneration generation,
            List<ContainerPath> requiredPaths
    ) {
    }

    private record PendingEntryRequest(
            PendingEntryTransfer transfer,
            Map<ContainerPath, RuntimeEntryDescriptor> remaining
    ) {
    }

    private record PendingEntryTransfer(
            ActiveScope scope,
            List<ContainerPath> requiredPaths,
            Set<UUID> requestIds
    ) {
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

    public interface ScopeListener {
        ScopeListener NONE = new ScopeListener() {
        };

        default void acknowledged(
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
    }
}
