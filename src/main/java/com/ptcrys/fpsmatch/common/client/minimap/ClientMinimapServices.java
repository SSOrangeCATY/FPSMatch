package com.ptcrys.fpsmatch.common.client.minimap;

import com.ptcrys.fpsmatch.common.client.minimap.cache.MinimapDiskCache;
import com.ptcrys.fpsmatch.common.client.minimap.cache.RuntimeEntryStore;
import com.ptcrys.fpsmatch.common.client.minimap.generated.MinecraftClientGeneratedMinimapRuntime;
import com.ptcrys.fpsmatch.common.client.minimap.sync.ClientMinimapS2CDispatcher;
import com.ptcrys.fpsmatch.common.client.minimap.sync.FragmentAccumulator;
import com.ptcrys.fpsmatch.common.client.minimap.sync.MarkerResetAccumulator;
import com.ptcrys.fpsmatch.common.client.minimap.sync.MinimapClientSyncManager;
import com.ptcrys.fpsmatch.common.client.minimap.editor.MinimapPublishRefreshRegistry;
import com.ptcrys.fpsmatch.common.client.minimap.editor.EditorResumeCheckpointRegistry;
import com.ptcrys.fpsmatch.common.packet.minimap.MinimapS2CDispatcher;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.marker.ClientMarkerStore;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.wire.EditorWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.PublishWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.RuntimeWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

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
    private final AcceptanceRecoveryOutboundGate outbound;
    private final Supplier<UUID> requestIds;
    private final ClientMinimapSubscriptionCoordinator subscriptions;
    private Object editorListenerOwner;
    private EditorTransportBinding editorTransportBinding;
    private Object installedEditorListenerOwner;
    private ClientMinimapS2CDispatcher.EditorListeners installedEditorListeners =
            ClientMinimapS2CDispatcher.EditorListeners.NONE;
    private Object connectionToken;
    private String editorServerIdentity;
    private Consumer<MinimapS2CDispatcher> dispatcherInstaller = ignored -> { };
    private MinimapS2CDispatcher currentTransport = ignored -> { };
    private AcceptanceRecoveryAckGuard acceptanceGuard;
    private AcceptanceRecoveryAckGuard acceptanceControl;
    private long transportEpoch;
    private boolean connected;

    private ClientMinimapServices(
            ClientMinimapRuntime runtime,
            MinimapDiskCache diskCache,
            ClientMarkerStore markerStore,
            ClientMinimapS2CDispatcher dispatcher,
            AcceptanceRecoveryOutboundGate outbound,
            Supplier<UUID> requestIds
    ) {
        this.runtime = runtime;
        this.diskCache = diskCache;
        this.markerStore = markerStore;
        this.dispatcher = dispatcher;
        this.outbound = outbound;
        this.requestIds = requestIds;
        this.subscriptions = new ClientMinimapSubscriptionCoordinator(
                this::subscribe, this::unsubscribe, this::hasScope
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
                        * com.ptcrys.fpsmatch.core.minimap.wire
                        .MarkerWireMessage.MAX_PAGE_ITEMS,
                MinimapHardLimits.REASSEMBLY_TTL.toMillis()
        );
        AcceptanceRecoveryOutboundGate outbound =
                new AcceptanceRecoveryOutboundGate(sender);
        ClientMinimapS2CDispatcher dispatcher = new ClientMinimapS2CDispatcher(
                runtime, syncManager, markerStore, outbound,
                nowMillis, requestIds, resets
        );
        return new ClientMinimapServices(
                runtime, diskCache, markerStore, dispatcher, outbound, requestIds
        );
    }

    public synchronized void connect(String serverIdentity) {
        connect(new Object(), serverIdentity);
    }

    public synchronized void connect(Object token, String serverIdentity) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(serverIdentity, "serverIdentity");
        clearAcceptanceRecovery(true);
        // Publication gates belong to one connection; never expose a same-named map
        // from a different server through a stale editor refresh lock.
        MinimapPublishRefreshRegistry.global().clearAll();
        EditorResumeCheckpointRegistry.global().clearAll();
        boolean editorCanResume = editorServerIdentity == null
                || editorServerIdentity.equals(serverIdentity);
        // Reset callbacks may re-enter subscription code, so old packet callbacks die first.
        invalidateDispatcherEpoch();
        if (connected) {
            detachEditorBinding();
        }
        clearDispatcher();
        runtime.connect(serverIdentity);
        MinecraftClientGeneratedMinimapRuntime.instance().reset();
        subscriptions.reset();
        connectionToken = token;
        connected = true;
        if (editorCanResume) {
            editorServerIdentity = serverIdentity;
        } else {
            resetEditorBinding();
            clearPersistentEditorListeners();
            editorServerIdentity = serverIdentity;
        }
        installNextDispatcherEpoch();
        reattachEditorListeners();
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
            outbound.accept(new RuntimeWireMessage.Unsubscribe(
                    requestIds.get(), activeLease.orElseThrow(),
                    activeTarget.orElse(target)
            ));
            dispatcher.forgetScope(scope);
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
                outbound.accept(new RuntimeWireMessage.Unsubscribe(
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
            runtime.releaseIfCurrent(lease);
            return Optional.empty();
        }
        try {
            outbound.accept(subscribe);
        } catch (RuntimeException failure) {
            if (scope == WireIdentity.Scope.TACTICAL_SCREEN
                    && subscriptions.deferTacticalFailureCleanup()) {
                // Acceptance diagnostics must be published before the tactical
                // cancellation packet. Keep this pending subscription for the
                // coordinator's closeTactical() cleanup path.
                throw failure;
            }
            throw ClientMinimapAcceptanceRecovery.rollback(
                    failure,
                    () -> outbound.accept(new RuntimeWireMessage.Unsubscribe(
                            requestIds.get(), lease.toWire(), target
                    )),
                    () -> runtime.releaseIfCurrent(lease),
                    () -> dispatcher.retireExact(requestId, subscribe.lease())
            );
        }
        return Optional.of(requestId);
    }

    public synchronized void unsubscribe(WireIdentity.Scope scope) {
        Objects.requireNonNull(scope, "scope");
        Optional<ClientMinimapS2CDispatcher.TrackedSubscription> tracked =
                dispatcher.trackedSubscription(scope);
        RuntimeException failure = null;
        if (tracked.isPresent()) {
            ClientMinimapS2CDispatcher.TrackedSubscription subscription =
                    tracked.orElseThrow();
            try {
                outbound.accept(new RuntimeWireMessage.Unsubscribe(
                        requestIds.get(), subscription.lease(), subscription.target()
                ));
            } catch (RuntimeException exception) {
                failure = exception;
            }
        }
        try {
            runtime.release(scope);
        } catch (RuntimeException exception) {
            if (failure == null) {
                failure = exception;
            } else if (failure != exception) {
                failure.addSuppressed(exception);
            }
        }
        try {
            dispatcher.forgetScope(scope);
        } catch (RuntimeException exception) {
            if (failure == null) {
                failure = exception;
            } else if (failure != exception) {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    public synchronized void disconnect() {
        disconnect(connectionToken);
    }

    public synchronized void disconnect(Object token) {
        if (token == null || connectionToken != token) {
            return;
        }
        clearAcceptanceRecovery(true);
        MinimapPublishRefreshRegistry.global().clearAll();
        EditorResumeCheckpointRegistry.global().clearAll();
        invalidateDispatcherEpoch();
        detachEditorBinding();
        clearDispatcher();
        subscriptions.reset();
        runtime.logout();
        MinecraftClientGeneratedMinimapRuntime.instance().reset();
        connected = false;
        connectionToken = null;
        installNextDispatcherEpoch();
    }

    /** Forwards the Forge client END tick to the timeout state machine. */
    public synchronized void tick() {
        if (connected) {
            dispatcher.tick();
            MinecraftClientGeneratedMinimapRuntime.instance().tick();
        }
    }

    public synchronized void reset() {
        clearAcceptanceRecovery(true);
        MinimapPublishRefreshRegistry.global().clearAll();
        EditorResumeCheckpointRegistry.global().clearAll();
        invalidateDispatcherEpoch();
        resetEditorBinding();
        clearDispatcher();
        clearPersistentEditorListeners();
        editorServerIdentity = null;
        subscriptions.reset();
        runtime.resetAll();
        MinecraftClientGeneratedMinimapRuntime.instance().reset();
        installNextDispatcherEpoch();
    }

    public synchronized void reloadResources() {
        if (!connected) {
            return;
        }
        clearAcceptanceRecovery(true);
        Optional<ClientMinimapS2CDispatcher.TrackedSubscription> hud =
                dispatcher.trackedSubscription(WireIdentity.Scope.MATCH_HUD);
        invalidateDispatcherEpoch();
        detachEditorBinding();
        for (WireIdentity.Scope scope : WireIdentity.Scope.values()) {
            dispatcher.trackedSubscription(scope).ifPresent(subscription ->
                outbound.accept(new RuntimeWireMessage.Unsubscribe(
                            requestIds.get(),
                            subscription.lease(),
                            subscription.target()
                    ))
            );
        }
        clearDispatcher();
        if (hud.isPresent()) {
            subscriptions.beginResourceReload();
        } else {
            subscriptions.reset();
        }
        if (runtime.currentGeneration().isPresent()) {
            runtime.reloadResources();
        } else {
            runtime.resetAll();
        }
        installNextDispatcherEpoch();
        reattachEditorListeners();
        hud.ifPresent(subscription ->
                subscriptions.enterMatch(subscription.target())
        );
    }

    public boolean hasActiveScope(WireIdentity.Scope scope) {
        return dispatcher.hasActiveScope(scope);
    }

    /** Includes both pending and acknowledged subscriptions. */
    public boolean hasScope(WireIdentity.Scope scope) {
        return dispatcher.trackedSubscription(
                Objects.requireNonNull(scope, "scope")
        ).isPresent();
    }

    public ClientMinimapRuntime runtime() {
        return runtime;
    }

    /** Generated tiles sourced from currently loaded client chunks. */
    public MinecraftClientGeneratedMinimapRuntime generatedMinimap() {
        return MinecraftClientGeneratedMinimapRuntime.instance();
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

    public synchronized void installDispatcherWith(
            Consumer<MinimapS2CDispatcher> installer
    ) {
        dispatcherInstaller = Objects.requireNonNull(installer, "installer");
        installNextDispatcherEpoch();
    }

    public ClientMinimapSubscriptionCoordinator subscriptions() {
        return subscriptions;
    }

    public Optional<RuntimeEntryStore.ActiveRuntime> activeRuntime() {
        return dispatcher.activeRuntime();
    }

    public synchronized EditorListenerHandle attachEditorListeners(EditorListeners listeners) {
        Objects.requireNonNull(listeners, "listeners");
        return attachEditorBinding(epoch -> listeners);
    }

    public synchronized EditorListenerHandle attachEditorBinding(
            EditorTransportBinding binding
    ) {
        Objects.requireNonNull(binding, "binding");
        Object owner = new Object();
        Object previousOwner = editorListenerOwner;
        EditorTransportBinding previousBinding = editorTransportBinding;
        Object previousInstalledOwner = installedEditorListenerOwner;
        ClientMinimapS2CDispatcher.EditorListeners previousListeners =
                installedEditorListeners;
        editorListenerOwner = owner;
        editorTransportBinding = binding;
        EditorListenerHandle handle = () -> clearEditorListeners(owner);
        try {
            if (connected) {
                ClientMinimapS2CDispatcher.EditorListeners nextListeners =
                        dispatcherListeners(binding);
                // listeners() may synchronously install a newer binding.
                if (!ownsEditorBinding(owner, binding)) {
                    return handle;
                }
                dispatcher.setEditorListeners(owner, nextListeners);
                installedEditorListenerOwner = owner;
                installedEditorListeners = nextListeners;
                binding.ready(transportEpoch);
            }
            return handle;
        } catch (RuntimeException failure) {
            if (ownsEditorBinding(owner, binding)) {
                editorListenerOwner = previousOwner;
                editorTransportBinding = previousBinding;
                Object currentInstalledOwner = installedEditorListenerOwner;
                try {
                    if (previousInstalledOwner == null) {
                        if (currentInstalledOwner != null) {
                            dispatcher.clearEditorListeners(currentInstalledOwner);
                        }
                        installedEditorListenerOwner = null;
                        installedEditorListeners =
                                ClientMinimapS2CDispatcher.EditorListeners.NONE;
                    } else {
                        // Service ownership may differ during a nested failed attachment.
                        dispatcher.setEditorListeners(previousInstalledOwner, previousListeners);
                        installedEditorListenerOwner = previousInstalledOwner;
                        installedEditorListeners = previousListeners;
                    }
                } catch (RuntimeException rollbackFailure) {
                    editorListenerOwner = null;
                    editorTransportBinding = null;
                    installedEditorListenerOwner = null;
                    installedEditorListeners =
                            ClientMinimapS2CDispatcher.EditorListeners.NONE;
                    try {
                        if (currentInstalledOwner != null) {
                            dispatcher.clearEditorListeners(currentInstalledOwner);
                        }
                        if (previousInstalledOwner != null
                                && previousInstalledOwner != currentInstalledOwner) {
                            dispatcher.clearEditorListeners(previousInstalledOwner);
                        }
                    } catch (RuntimeException clearFailure) {
                        rollbackFailure.addSuppressed(clearFailure);
                    }
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        }
    }

    private synchronized void clearEditorListeners(Object owner) {
        if (editorListenerOwner != owner) {
            return;
        }
        editorListenerOwner = null;
        editorTransportBinding = null;
        installedEditorListenerOwner = null;
        installedEditorListeners = ClientMinimapS2CDispatcher.EditorListeners.NONE;
        dispatcher.clearEditorListeners(owner);
    }

    private void clearPersistentEditorListeners() {
        Object owner = editorListenerOwner;
        editorListenerOwner = null;
        editorTransportBinding = null;
        installedEditorListenerOwner = null;
        installedEditorListeners = ClientMinimapS2CDispatcher.EditorListeners.NONE;
        if (owner != null) {
            dispatcher.clearEditorListeners(owner);
        }
    }

    private void reattachEditorListeners() {
        if (editorListenerOwner == null || editorTransportBinding == null || !connected) {
            return;
        }
        Object owner = editorListenerOwner;
        EditorTransportBinding binding = editorTransportBinding;
        ClientMinimapS2CDispatcher.EditorListeners listeners =
                dispatcherListeners(binding);
        if (!ownsEditorBinding(owner, binding)) {
            return;
        }
        dispatcher.setEditorListeners(owner, listeners);
        installedEditorListenerOwner = owner;
        installedEditorListeners = listeners;
        binding.ready(transportEpoch);
    }

    private ClientMinimapS2CDispatcher.EditorListeners dispatcherListeners(
            EditorTransportBinding binding
    ) {
        EditorListeners listeners = Objects.requireNonNull(
                binding.listeners(transportEpoch),
                "editor transport binding returned no listeners"
        );
        return new ClientMinimapS2CDispatcher.EditorListeners(
                listeners.session(), listeners.ack(), listeners.manifest(),
                listeners.fragment(), listeners.rebase(), listeners.error(),
                listeners.publish(), listeners.publishStatus()
        );
    }

    private boolean ownsEditorBinding(Object owner, EditorTransportBinding binding) {
        return editorListenerOwner == owner && editorTransportBinding == binding;
    }

    private void clearDispatcher() {
        dispatcher.clear();
        installedEditorListenerOwner = null;
        installedEditorListeners = ClientMinimapS2CDispatcher.EditorListeners.NONE;
    }


    private void installNextDispatcherEpoch() {
        long expectedEpoch = ++transportEpoch;
        MinimapS2CDispatcher delegate = dispatcher.advanceTransportEpoch();
        MinimapS2CDispatcher outer = message ->
                dispatchInbound(expectedEpoch, delegate, message);
        currentTransport = outer;
        dispatcherInstaller.accept(outer);
    }

    private void invalidateDispatcherEpoch() {
        dispatcher.invalidateTransportEpoch();
    }

    Optional<AcceptanceReplayStart> beginAcceptanceReplay(
            RuntimeGeneration baseline,
            WireIdentity.RuntimeIdentity identity,
            Runnable rejected
    ) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(rejected, "rejected");
        if (!canBeginAcceptanceReplay(baseline, identity)) {
            return Optional.empty();
        }
        Optional<WireIdentity.MapTarget> retired =
                subscriptions.retireForAcceptanceReplay();
        if (retired.isEmpty()) {
            return Optional.empty();
        }
        WireIdentity.MapTarget target = retired.orElseThrow();
        outbound.arm(target);
        synchronized (this) {
            invalidateDispatcherEpoch();
        }
        dispatcher.retireRuntimeForReplay();
        runtime.connect(baseline.serverIdentity());
        synchronized (this) {
            installNextDispatcherEpoch();
        }

        UUID requestId;
        try {
            requestId = subscriptions.enterMatch(target).orElseThrow(() ->
                    new IllegalStateException(
                            "Acceptance replay did not create a fresh subscription"
                    )
            );
        } catch (RuntimeException failure) {
            outbound.clear();
            throw failure;
        }
        RuntimeWireMessage.Subscribe fresh = dispatcher
                .pendingSubscribe(WireIdentity.Scope.MATCH_HUD)
                .filter(subscribe -> subscribe.requestId().equals(requestId))
                .orElseThrow(() -> new IllegalStateException(
                        "Acceptance replay lost its fresh subscription correlation"
                ));
        RuntimeGeneration generation = runtime.pendingGenerationSnapshot(identity)
                .orElseThrow(() -> new IllegalStateException(
                        "Acceptance replay has no pending runtime generation"
                ));
        AcceptanceRecoveryAckGuard guard = new AcceptanceRecoveryAckGuard(
                fresh, identity, generation, rejected
        );
        MinimapS2CDispatcher transport;
        synchronized (this) {
            acceptanceGuard = guard;
            acceptanceControl = guard;
            transport = currentTransport;
        }
        RuntimeWireMessage.Subscribe held = outbound.takeExact(
                fresh.requestId(), fresh.lease()
        );
        try {
            outbound.forwardConsumed(held);
        } catch (RuntimeException failure) {
            clearAcceptanceRecovery(false);
            guard.reject();
            throw ClientMinimapAcceptanceRecovery.rollback(
                    failure,
                    () -> runtime.releaseIfCurrent(local(fresh.lease())),
                    () -> dispatcher.retireExact(
                            fresh.requestId(), fresh.lease()
                    ),
                    () -> subscriptions.retireAcceptanceAttempt(
                            fresh.requestId()
                    )
            );
        }
        return Optional.of(new AcceptanceReplayStart(
                fresh, generation, transport, guard
        ));
    }

    private synchronized boolean canBeginAcceptanceReplay(
            RuntimeGeneration baseline,
            WireIdentity.RuntimeIdentity identity
    ) {
        WireIdentity.DocumentBinding binding = identity.binding();
        return connected
                && acceptanceControl == null
                && runtime.isCurrent(baseline)
                && dispatcher.hasActiveScope(WireIdentity.Scope.MATCH_HUD)
                && baseline.mapKey().equals(binding.target().mapKey())
                && baseline.dimension().equals(binding.target().dimension())
                && baseline.documentId().equals(binding.documentId())
                && baseline.revision() == identity.revision()
                && baseline.runtimeHash().equals(identity.runtimeHash());
    }

    private void dispatchInbound(
            long expectedEpoch,
            MinimapS2CDispatcher delegate,
            MinimapWireMessage message
    ) {
        Objects.requireNonNull(message, "message");
        AcceptanceRecoveryAckGuard guard = null;
        AcceptanceRecoveryAckGuard.Decision decision =
                AcceptanceRecoveryAckGuard.Decision.UNRELATED;
        synchronized (this) {
            if (expectedEpoch != transportEpoch) {
                return;
            }
            if (acceptanceGuard != null
                    && message instanceof RuntimeWireMessage.ScopeAck ack) {
                guard = acceptanceGuard;
                decision = guard.classify(ack);
                if (decision != AcceptanceRecoveryAckGuard.Decision.UNRELATED) {
                    acceptanceGuard = null;
                }
                if (decision == AcceptanceRecoveryAckGuard.Decision.REJECT) {
                    acceptanceControl = null;
                }
            }
        }
        if (decision == AcceptanceRecoveryAckGuard.Decision.REJECT) {
            guard.reject();
            retireAcceptanceAttempt(guard.subscribe());
            return;
        }
        delegate.dispatch(message);
    }

    private void retireAcceptanceAttempt(RuntimeWireMessage.Subscribe subscribe) {
        runtime.releaseIfCurrent(local(subscribe.lease()));
        dispatcher.retireExact(subscribe.requestId(), subscribe.lease());
        subscriptions.retireAcceptanceAttempt(subscribe.requestId());
    }

    synchronized void completeAcceptanceRecovery(AcceptanceRecoveryAckGuard guard) {
        if (acceptanceControl == guard) {
            acceptanceControl = null;
            acceptanceGuard = null;
        }
    }

    private synchronized void clearAcceptanceRecovery(boolean reject) {
        AcceptanceRecoveryAckGuard guard = acceptanceControl;
        acceptanceGuard = null;
        acceptanceControl = null;
        outbound.clear();
        if (reject && guard != null) {
            guard.reject();
        }
    }

    private static MinimapScopeLease local(WireIdentity.ScopeLease lease) {
        return new MinimapScopeLease(
                lease.scope(), lease.scopeEpoch(), lease.runtimeGeneration()
        );
    }

    private void detachEditorBinding() {
        if (editorTransportBinding != null) {
            editorTransportBinding.detached(transportEpoch);
        }
    }

    private void resetEditorBinding() {
        if (editorTransportBinding != null) {
            editorTransportBinding.reset();
        }
    }

    public void send(MinimapWireMessage message) {
        outbound.accept(Objects.requireNonNull(message, "message"));
    }

    record AcceptanceReplayStart(
            RuntimeWireMessage.Subscribe subscribe,
            RuntimeGeneration generation,
            MinimapS2CDispatcher transport,
            AcceptanceRecoveryAckGuard guard
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

    @FunctionalInterface
    public interface EditorTransportBinding {
        EditorListeners listeners(long transportEpoch);

        default void ready(long transportEpoch) {
        }

        default void detached(long transportEpoch) {
        }

        default void reset() {
        }
    }

    @FunctionalInterface
    public interface EditorListenerHandle extends AutoCloseable {
        EditorListenerHandle NONE = () -> { };

        @Override
        void close();
    }
}
