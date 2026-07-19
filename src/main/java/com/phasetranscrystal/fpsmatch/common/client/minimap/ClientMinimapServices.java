package com.phasetranscrystal.fpsmatch.common.client.minimap;

import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.MinimapDiskCache;
import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.RuntimeEntryStore;
import com.phasetranscrystal.fpsmatch.common.client.minimap.sync.ClientMinimapS2CDispatcher;
import com.phasetranscrystal.fpsmatch.common.client.minimap.sync.FragmentAccumulator;
import com.phasetranscrystal.fpsmatch.common.client.minimap.sync.MarkerResetAccumulator;
import com.phasetranscrystal.fpsmatch.common.client.minimap.sync.MinimapClientSyncManager;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.ClientMarkerStore;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.EditorWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.PublishWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.RuntimeWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class ClientMinimapServices {
    private final ClientMinimapRuntime runtime;
    private final MinimapDiskCache diskCache;
    private final ClientMarkerStore markerStore;
    private final ClientMinimapS2CDispatcher dispatcher;
    private final Consumer<MinimapWireMessage> sender;
    private final Supplier<UUID> requestIds;
    private final ClientMinimapSubscriptionCoordinator subscriptions;
    private boolean connected;

    private ClientMinimapServices(
            ClientMinimapRuntime runtime,
            MinimapDiskCache diskCache,
            ClientMarkerStore markerStore,
            ClientMinimapS2CDispatcher dispatcher,
            Consumer<MinimapWireMessage> sender,
            Supplier<UUID> requestIds
    ) {
        this.runtime = runtime;
        this.diskCache = diskCache;
        this.markerStore = markerStore;
        this.dispatcher = dispatcher;
        this.sender = sender;
        this.requestIds = requestIds;
        this.subscriptions = new ClientMinimapSubscriptionCoordinator(
                this::subscribe, this::unsubscribe
        );
        this.dispatcher.setScopeListener(this.subscriptions);
    }

    public static ClientMinimapServices create(
            MinimapDiskCache diskCache,
            Consumer<MinimapWireMessage> sender,
            LongSupplier nowMillis,
            Supplier<UUID> requestIds
    ) {
        Objects.requireNonNull(diskCache, "diskCache");
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(nowMillis, "nowMillis");
        Objects.requireNonNull(requestIds, "requestIds");
        ClientMinimapRuntime runtime = ClientMinimapRuntime.create();
        ClientMarkerStore markerStore = new ClientMarkerStore();
        MinimapClientSyncManager syncManager = new MinimapClientSyncManager(
                new FragmentAccumulator(
                        32,
                        MinimapHardLimits.MAX_RUNTIME_EXPANDED_BYTES,
                        MinimapHardLimits.REASSEMBLY_TTL.toMillis()
                ),
                diskCache,
                new RuntimeEntryStore(),
                (key, bytes) -> true
        );
        MarkerResetAccumulator resets = new MarkerResetAccumulator(
                8,
                MinimapHardLimits.MAX_WIRE_PAGE_COUNT,
                MinimapHardLimits.MAX_WIRE_PAGE_COUNT
                        * com.phasetranscrystal.fpsmatch.core.minimap.wire
                        .MarkerWireMessage.MAX_PAGE_ITEMS,
                MinimapHardLimits.REASSEMBLY_TTL.toMillis()
        );
        ClientMinimapS2CDispatcher dispatcher = new ClientMinimapS2CDispatcher(
                runtime, syncManager, markerStore, sender,
                nowMillis, requestIds, resets
        );
        return new ClientMinimapServices(
                runtime, diskCache, markerStore, dispatcher, sender, requestIds
        );
    }

    public synchronized void connect(String serverIdentity) {
        Objects.requireNonNull(serverIdentity, "serverIdentity");
        dispatcher.clear();
        subscriptions.reset();
        runtime.connect(serverIdentity);
        connected = true;
    }

    public synchronized Optional<UUID> subscribe(
            WireIdentity.Scope scope,
            WireIdentity.MapTarget target,
            List<ContainerPath> requiredPaths,
            Optional<WireIdentity.RuntimeHint> runtimeHint
    ) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(requiredPaths, "requiredPaths");
        Objects.requireNonNull(runtimeHint, "runtimeHint");
        if (!connected || scope == WireIdentity.Scope.EDITOR) {
            return Optional.empty();
        }
        Optional<WireIdentity.ScopeLease> activeLease = dispatcher.activeLease(scope);
        Optional<WireIdentity.MapTarget> activeTarget = dispatcher.activeTarget(scope);
        if (activeLease.isPresent()) {
            sender.accept(new RuntimeWireMessage.Unsubscribe(
                    requestIds.get(), activeLease.orElseThrow(),
                    activeTarget.orElse(target)
            ));
            runtime.release(scope);
        }
        Optional<RuntimeGeneration> current = runtime.currentGeneration();
        boolean targetChanged = current.isPresent()
                && (!current.orElseThrow().mapKey().equals(target.mapKey())
                || !current.orElseThrow().dimension().equals(target.dimension()));
        if (targetChanged) {
            for (ClientMinimapS2CDispatcher.ActiveSubscription subscription
                    : dispatcher.activeSubscriptions()) {
                if (subscription.scope() == scope && activeLease.isPresent()) {
                    continue;
                }
                sender.accept(new RuntimeWireMessage.Unsubscribe(
                        requestIds.get(), subscription.lease(), subscription.target()
                ));
                runtime.release(subscription.scope());
            }
            dispatcher.forgetActiveScopes();
            runtime.beginTransition();
        }
        MinimapScopeLease lease = runtime.acquirePending(scope);
        UUID requestId = requestIds.get();
        RuntimeWireMessage.Subscribe subscribe = new RuntimeWireMessage.Subscribe(
                requestId, lease.toWire(), target, runtimeHint
        );
        if (!dispatcher.trackSubscribe(subscribe, requiredPaths)) {
            runtime.release(scope);
            return Optional.empty();
        }
        sender.accept(subscribe);
        return Optional.of(requestId);
    }

    public synchronized void unsubscribe(WireIdentity.Scope scope) {
        Objects.requireNonNull(scope, "scope");
        Optional<ClientMinimapS2CDispatcher.TrackedSubscription> tracked =
                dispatcher.trackedSubscription(scope);
        if (tracked.isPresent()) {
            ClientMinimapS2CDispatcher.TrackedSubscription subscription =
                    tracked.orElseThrow();
            sender.accept(new RuntimeWireMessage.Unsubscribe(
                    requestIds.get(), subscription.lease(), subscription.target()
            ));
        }
        runtime.release(scope);
        dispatcher.forgetScope(scope);
    }

    public synchronized void disconnect() {
        dispatcher.clear();
        subscriptions.reset();
        runtime.logout();
        connected = false;
    }

    public synchronized void reset() {
        dispatcher.clear();
        subscriptions.reset();
        runtime.resetAll();
    }

    public synchronized void reloadResources() {
        if (!connected) {
            return;
        }
        Optional<ClientMinimapS2CDispatcher.TrackedSubscription> hud =
                dispatcher.trackedSubscription(WireIdentity.Scope.MATCH_HUD);
        for (WireIdentity.Scope scope : WireIdentity.Scope.values()) {
            dispatcher.trackedSubscription(scope).ifPresent(subscription ->
                    sender.accept(new RuntimeWireMessage.Unsubscribe(
                            requestIds.get(),
                            subscription.lease(),
                            subscription.target()
                    ))
            );
        }
        dispatcher.clear();
        subscriptions.reset();
        if (runtime.currentGeneration().isPresent()) {
            runtime.reloadResources();
        } else {
            runtime.resetAll();
        }
        hud.ifPresent(subscription ->
                subscriptions.enterMatch(subscription.target())
        );
    }

    public boolean hasActiveScope(WireIdentity.Scope scope) {
        return dispatcher.hasActiveScope(scope);
    }

    public ClientMinimapRuntime runtime() {
        return runtime;
    }

    public MinimapDiskCache diskCache() {
        return diskCache;
    }

    public ClientMarkerStore markerStore() {
        return markerStore;
    }

    public ClientMinimapS2CDispatcher dispatcher() {
        return dispatcher;
    }

    public ClientMinimapSubscriptionCoordinator subscriptions() {
        return subscriptions;
    }

    public Optional<RuntimeEntryStore.ActiveRuntime> activeRuntime() {
        return dispatcher.activeRuntime();
    }

    public void attachEditorSessionListener(
            java.util.function.Consumer<EditorWireMessage.EditorSession> listener
    ) {
        dispatcher.setEditorSessionListener(listener);
    }

    public void clearEditorSessionListener() {
        dispatcher.clearEditorSessionListener();
    }

    public void attachPublishResultListener(
            java.util.function.Consumer<PublishWireMessage.PublishResult> listener
    ) {
        dispatcher.setPublishResultListener(listener);
    }

    public void clearPublishResultListener() {
        dispatcher.clearPublishResultListener();
    }

    public void send(MinimapWireMessage message) {
        sender.accept(Objects.requireNonNull(message, "message"));
    }
}
