package com.phasetranscrystal.fpsmatch.common.minimap.server.sync;

import com.phasetranscrystal.fpsmatch.common.packet.minimap.MinimapC2SDispatcher;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.MarkerSnapshot;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.MarkerStreamManager;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.MarkerStreamUpdate;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import com.phasetranscrystal.fpsmatch.common.minimap.server.EditorSession;
import com.phasetranscrystal.fpsmatch.common.minimap.server.EditorSessionManager;
import com.phasetranscrystal.fpsmatch.common.minimap.server.ServerEditorPublishService;
import com.phasetranscrystal.fpsmatch.common.minimap.server.MinimapAction;
import com.phasetranscrystal.fpsmatch.common.minimap.server.MinimapPermissionPolicy;
import com.phasetranscrystal.fpsmatch.common.minimap.server.SessionAccessException;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.EditorWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MarkerWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.PublishWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.RuntimeWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireEditor;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireMarker;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireStatus;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireTransfer;

import java.util.Arrays;
import java.util.Collections;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class ServerMinimapRuntimeRouter implements MinimapC2SDispatcher {
    private final RuntimeResolver resolver;
    private final MarkerResolver markerResolver;
    private final Sender sender;
    private final Supplier<UUID> transferIds;
    private final IntSupplier markerHz;
    private final Map<SubscriptionKey, Subscription> subscriptions = new HashMap<>();
    private final Map<UUID, MarkerStreamManager> markerStreams = new HashMap<>();
    private final EditorSessionManager editorSessions;
    private final MinimapPermissionPolicy editorPermissions;
    private final ServerEditorPublishService editorPublish;
    private Map<UUID, Subscription> markerSubscriptionSnapshot;

    public ServerMinimapRuntimeRouter(
            RuntimeResolver resolver,
            Sender sender,
            Supplier<UUID> transferIds
    ) {
        this(
                resolver, (actorId, target) -> Optional.empty(), sender,
                transferIds, () -> 5
        );
    }

    public ServerMinimapRuntimeRouter(
            RuntimeResolver resolver,
            MarkerResolver markerResolver,
            Sender sender,
            Supplier<UUID> transferIds
    ) {
        this(resolver, markerResolver, sender, transferIds, () -> 5);
    }

    public ServerMinimapRuntimeRouter(
            RuntimeResolver resolver,
            MarkerResolver markerResolver,
            Sender sender,
            Supplier<UUID> transferIds,
            IntSupplier markerHz
    ) {
        this(resolver, markerResolver, sender, transferIds, markerHz, null, null, null);
    }

    public ServerMinimapRuntimeRouter(
            RuntimeResolver resolver,
            MarkerResolver markerResolver,
            Sender sender,
            Supplier<UUID> transferIds,
            IntSupplier markerHz,
            EditorSessionManager editorSessions,
            MinimapPermissionPolicy editorPermissions
    ) {
        this(resolver, markerResolver, sender, transferIds, markerHz,
                editorSessions, editorPermissions, null);
    }

    public ServerMinimapRuntimeRouter(
            RuntimeResolver resolver,
            MarkerResolver markerResolver,
            Sender sender,
            Supplier<UUID> transferIds,
            IntSupplier markerHz,
            EditorSessionManager editorSessions,
            MinimapPermissionPolicy editorPermissions,
            ServerEditorPublishService editorPublish
    ) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.markerResolver = Objects.requireNonNull(
                markerResolver, "markerResolver"
        );
        this.sender = Objects.requireNonNull(sender, "sender");
        this.transferIds = Objects.requireNonNull(transferIds, "transferIds");
        this.markerHz = Objects.requireNonNull(markerHz, "markerHz");
        this.editorSessions = editorSessions;
        this.editorPermissions = editorPermissions;
        this.editorPublish = editorPublish;
    }

    @Override
    public synchronized void dispatch(UUID actorId, MinimapWireMessage message) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(message, "message");
        if (message instanceof RuntimeWireMessage.Subscribe subscribe) {
            subscribe(actorId, subscribe);
        } else if (message instanceof RuntimeWireMessage.Unsubscribe unsubscribe) {
            unsubscribe(actorId, unsubscribe);
        } else if (message instanceof RuntimeWireMessage.RequestEntries request) {
            requestEntries(actorId, request);
        } else if (message instanceof RuntimeWireMessage.RequestMarkerReset request) {
            requestMarkerReset(actorId, request);
        } else if (message instanceof EditorWireMessage.EditorOpen open) {
            openEditor(actorId, open);
        } else if (message instanceof EditorWireMessage.EditorClose close) {
            closeEditor(actorId, close);
        } else if (message instanceof EditorWireMessage.SaveDraft save) {
            saveDraft(actorId, save);
        } else if (message instanceof PublishWireMessage.ReservePublish reserve) {
            reservePublish(actorId, reserve);
        }
    }

    private void reservePublish(UUID actorId, PublishWireMessage.ReservePublish reserve) {
        if (editorPublish == null) {
            return;
        }
        editorPublish.publishEmpty(actorId, reserve);
    }

    public boolean allowEditor(UUID actorId, MinimapWireMessage message) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(message, "message");
        if (editorSessions == null || editorPermissions == null) {
            return false;
        }
        try {
            if (message instanceof EditorWireMessage.EditorOpen open) {
                return editorPermissions.mayPerform(
                        actorId,
                        open.target().mapKey(),
                        MinimapAction.OPEN_EDITOR
                ).orElse(false);
            }
            if (message instanceof EditorWireMessage.EditorClose close) {
                WireIdentity.EditorContext context = close.context();
                editorSessions.authorize(
                        actorId,
                        context.sessionId(),
                        context.binding().target().mapKey(),
                        context.binding().target().dimension(),
                        context.binding().documentId(),
                        context.draftId(),
                        context.baseRevision(),
                        close.closeMode() == WireEditor.CloseMode.DISCARD_DRAFT
                                ? MinimapAction.DISCARD_DRAFT
                                : MinimapAction.FORCE_CLOSE_SESSION
                );
                return true;
            }
            if (message instanceof EditorWireMessage.SaveDraft save) {
                WireIdentity.EditorContext context = save.context();
                editorSessions.authorize(
                        actorId,
                        context.sessionId(),
                        context.binding().target().mapKey(),
                        context.binding().target().dimension(),
                        context.binding().documentId(),
                        context.draftId(),
                        context.baseRevision(),
                        MinimapAction.SAVE_DRAFT
                );
                return true;
            }
            if (message instanceof PublishWireMessage.ReservePublish reserve) {
                WireIdentity.EditorContext context = reserve.context();
                editorSessions.authorize(
                        actorId,
                        context.sessionId(),
                        context.binding().target().mapKey(),
                        context.binding().target().dimension(),
                        context.binding().documentId(),
                        context.draftId(),
                        context.baseRevision(),
                        MinimapAction.RESERVE_PUBLISH
                );
                return true;
            }
            return false;
        } catch (RuntimeException rejected) {
            return false;
        }
    }

    private void openEditor(UUID actorId, EditorWireMessage.EditorOpen open) {
        if (editorSessions == null || editorPermissions == null) {
            return;
        }
        try {
            UUID draftId = transferIds.get();
            EditorSession session = editorSessions.open(
                    actorId,
                    open.target().mapKey(),
                    open.target().dimension(),
                    open.documentId(),
                    draftId,
                    open.expectedRevision()
            );
            Sha256 empty = Sha256Digest.of(new byte[0]);
            WireIdentity.EditorContext context = new WireIdentity.EditorContext(
                    open.lease(),
                    new WireIdentity.DocumentBinding(open.target(), open.documentId()),
                    session.sessionId(),
                    session.draftId(),
                    session.baseRevision(),
                    empty,
                    empty,
                    0L
            );
            sender.send(actorId, new EditorWireMessage.EditorSession(
                    open.requestId(),
                    context,
                    session.expiresAt().toEpochMilli(),
                    WireEditor.SourceAvailability.NONE
            ));
        } catch (SessionAccessException denied) {
            sender.send(actorId, new PublishWireMessage.ErrorMessage(
                    Optional.of(open.requestId()),
                    Optional.of(open.lease()),
                    Optional.of(new WireIdentity.DocumentBinding(
                            open.target(), open.documentId()
                    )),
                    Optional.of(open.opcode().code()),
                    new WireStatus.ErrorInfo(
                            denied.errorCode().code(),
                            WireStatus.RetryDisposition.DO_NOT_RETRY,
                            denied.getMessage() == null ? "editor open denied" : denied.getMessage()
                    )
            ));
        }
    }

    private void closeEditor(UUID actorId, EditorWireMessage.EditorClose close) {
        if (editorSessions == null) {
            return;
        }
        WireIdentity.EditorContext context = close.context();
        try {
            editorSessions.authorize(
                    actorId,
                    context.sessionId(),
                    context.binding().target().mapKey(),
                    context.binding().target().dimension(),
                    context.binding().documentId(),
                    context.draftId(),
                    context.baseRevision(),
                    close.closeMode() == WireEditor.CloseMode.DISCARD_DRAFT
                            ? MinimapAction.DISCARD_DRAFT
                            : MinimapAction.FORCE_CLOSE_SESSION
            );
            editorSessions.invalidateActor(actorId);
            sender.send(actorId, new EditorWireMessage.EditorAck(
                    close.requestId(),
                    context,
                    new WireEditor.Closed(close.closeMode())
            ));
        } catch (SessionAccessException denied) {
            sender.send(actorId, new PublishWireMessage.ErrorMessage(
                    Optional.of(close.requestId()),
                    Optional.of(context.lease()),
                    Optional.of(context.binding()),
                    Optional.of(close.opcode().code()),
                    new WireStatus.ErrorInfo(
                            denied.errorCode().code(),
                            WireStatus.RetryDisposition.REOPEN_SESSION,
                            denied.getMessage() == null ? "editor close denied" : denied.getMessage()
                    )
            ));
        }
    }

    private void saveDraft(UUID actorId, EditorWireMessage.SaveDraft save) {
        if (editorSessions == null) {
            return;
        }
        WireIdentity.EditorContext context = save.context();
        try {
            EditorSession session = editorSessions.authorize(
                    actorId,
                    context.sessionId(),
                    context.binding().target().mapKey(),
                    context.binding().target().dimension(),
                    context.binding().documentId(),
                    context.draftId(),
                    context.baseRevision(),
                    MinimapAction.SAVE_DRAFT
            );
            WireIdentity.EditorContext ackContext = new WireIdentity.EditorContext(
                    context.lease(),
                    context.binding(),
                    session.sessionId(),
                    session.draftId(),
                    session.baseRevision(),
                    context.baseSourceHash(),
                    save.expectedRootHash(),
                    save.expectedAckCursor()
            );
            sender.send(actorId, new EditorWireMessage.EditorAck(
                    save.requestId(),
                    ackContext,
                    new WireEditor.DraftSaved(save.compact())
            ));
        } catch (SessionAccessException denied) {
            sender.send(actorId, new PublishWireMessage.ErrorMessage(
                    Optional.of(save.requestId()),
                    Optional.of(context.lease()),
                    Optional.of(context.binding()),
                    Optional.of(save.opcode().code()),
                    new WireStatus.ErrorInfo(
                            denied.errorCode().code(),
                            WireStatus.RetryDisposition.REOPEN_SESSION,
                            denied.getMessage() == null ? "editor save denied" : denied.getMessage()
                    )
            ));
        }
    }

    public synchronized void onPlayerLogout(UUID actorId) {
        Objects.requireNonNull(actorId, "actorId");
        if (subscriptions.keySet().removeIf(key -> key.actorId().equals(actorId))) {
            invalidateMarkerSubscriptionSnapshot();
        }
        markerStreams.remove(actorId);
        if (editorSessions != null) {
            editorSessions.invalidateActor(actorId);
        }
    }

    public synchronized void tick(long nowTick) {
        if (nowTick < 0L) {
            throw new IllegalArgumentException("Marker tick must be non-negative");
        }
        int frequency = markerHz.getAsInt();
        if (frequency < 1 || frequency > 20) {
            throw new IllegalStateException("Marker frequency must be in [1, 20]");
        }
        IdentityHashMap<MarkerSnapshot.Marker, WireMarker.Marker>
                wireMarkerCache = new IdentityHashMap<>();
        for (Map.Entry<UUID, Subscription> entry : markerSubscriptions().entrySet()) {
            if (markerDue(entry.getKey(), nowTick, frequency)) {
                tickMarkers(entry.getKey(), entry.getValue(), wireMarkerCache);
            }
        }
    }

    private void tickMarkers(
            UUID actorId,
            Subscription subscription,
            IdentityHashMap<MarkerSnapshot.Marker, WireMarker.Marker>
                    wireMarkerCache
    ) {
        try (RuntimeMapSource source = resolve(
                actorId, subscription.identity().binding().target()
        ).orElse(null)) {
            if (source == null) {
                revokeMarkerSubscriptions(
                        actorId, subscription, MinimapErrorCode.UNAUTHORIZED,
                        WireStatus.RetryDisposition.DO_NOT_RETRY,
                        "Runtime map authorization changed"
                );
                return;
            }
            if (!source.identity().equals(subscription.identity())) {
                revokeMarkerSubscriptions(
                        actorId, subscription, MinimapErrorCode.REVISION_CONFLICT,
                        WireStatus.RetryDisposition.RESYNC_SCOPE,
                        "Runtime identity changed"
                );
                return;
            }
            Optional<RuntimeMarkerSnapshot> resolved = markerResolver.resolve(
                    actorId, subscription.identity().binding().target()
            );
            if (resolved.isEmpty()) {
                revokeMarkerSubscriptions(
                        actorId, subscription, MinimapErrorCode.UNAUTHORIZED,
                        WireStatus.RetryDisposition.DO_NOT_RETRY,
                        "Marker visibility context is unavailable"
                );
                return;
            }
            RuntimeMarkerSnapshot snapshot = resolved.orElseThrow();
            MarkerStreamManager stream = markerStreams.computeIfAbsent(
                    actorId, ignored -> markerStream()
            );
            sendMarkerUpdate(
                    actorId, subscription, stream, snapshot,
                    stream.tick(
                            snapshot.viewer(),
                            snapshot.markerSnapshot()
                    ),
                    wireMarkerCache
            );
        } catch (IOException | RuntimeException unavailable) {
            resetMarkerStream(actorId, subscription);
            markerError(
                    actorId, subscription, MinimapErrorCode.MAP_UNAVAILABLE,
                    WireStatus.RetryDisposition.RETRY_NEW_REQUEST,
                    "Marker snapshot is unavailable"
            );
        }
    }

    private void sendMarkerUpdate(
            UUID actorId,
            Subscription subscription,
            MarkerStreamManager stream,
            RuntimeMarkerSnapshot snapshot,
            MarkerStreamUpdate update,
            IdentityHashMap<MarkerSnapshot.Marker, WireMarker.Marker>
                    wireMarkerCache
    ) {
        if (update.kind() == MarkerStreamUpdate.Kind.RESET) {
            sendMarkerReset(
                    actorId, Optional.empty(), subscription.lease(),
                    subscription.identity(), update
            );
        } else if (update.operations().size() > MarkerWireMessage.MAX_PAGE_ITEMS) {
            sendMarkerReset(
                    actorId, Optional.empty(), subscription.lease(),
                    subscription.identity(),
                    stream.subscribe(snapshot.viewer(), snapshot.markers())
            );
        } else if (!update.operations().isEmpty()) {
            sender.send(actorId, new MarkerWireMessage.Delta(
                    subscription.lease(), subscription.identity(),
                    update.streamEpoch(), update.sequence(),
                    update.operations().stream()
                            .map(operation -> wireOperation(
                                    operation, wireMarkerCache
                            ))
                            .toList()
            ));
        }
    }

    private void revokeMarkerSubscriptions(
            UUID actorId,
            Subscription subscription,
            MinimapErrorCode code,
            WireStatus.RetryDisposition retry,
            String detail
    ) {
        resetMarkerStream(actorId, subscription);
        if (subscriptions.keySet().removeIf(key -> key.actorId().equals(actorId))) {
            invalidateMarkerSubscriptionSnapshot();
        }
        markerStreams.remove(actorId);
        markerError(actorId, subscription, code, retry, detail);
    }

    private void resetMarkerStream(UUID actorId, Subscription subscription) {
        MarkerStreamManager replacement = markerStream();
        markerStreams.put(actorId, replacement);
        MarkerStreamUpdate reset = replacement.subscribe(
                emptyViewer(), List.of()
        );
        sendMarkerReset(
                actorId, Optional.empty(), subscription.lease(),
                subscription.identity(), reset
        );
    }

    private void markerError(
            UUID actorId,
            Subscription subscription,
            MinimapErrorCode code,
            WireStatus.RetryDisposition retry,
            String detail
    ) {
        sender.send(actorId, new PublishWireMessage.ErrorMessage(
                Optional.empty(), Optional.of(subscription.lease()),
                Optional.of(subscription.identity().binding()),
                Optional.of(com.phasetranscrystal.fpsmatch.core.minimap.contract
                        .MinimapOpcode.S2C_MARKER_DELTA.code()),
                new WireStatus.ErrorInfo(code.code(), retry, detail)
        ));
    }

    private void subscribe(UUID actorId, RuntimeWireMessage.Subscribe message) {
        try (RuntimeMapSource source = resolve(actorId, message.target()).orElse(null)) {
            if (source == null) {
                error(actorId, message.requestId(), message.lease(), Optional.empty(),
                        message.opcode().code(), MinimapErrorCode.UNAUTHORIZED,
                        WireStatus.RetryDisposition.DO_NOT_RETRY, "Runtime map is unavailable");
                return;
            }
            List<WireTransfer.TransferFragment> manifestFragments = fragments(
                    source.manifestBytes(), source.identity().runtimeHash()
            );
            Optional<RuntimeMarkerSnapshot> markers = markerResolver.resolve(
                    actorId, message.target()
            );
            Optional<MarkerStreamUpdate> initialReset = markers.map(snapshot ->
                    markerStreams.computeIfAbsent(
                            actorId, ignored -> markerStream()
                    ).subscribe(snapshot.viewer(), snapshot.markers())
            );
            subscriptions.entrySet().removeIf(entry ->
                    entry.getKey().actorId().equals(actorId)
                            && entry.getKey().scope() == message.lease().scope()
            );
            subscriptions.put(
                    new SubscriptionKey(actorId, message.lease().scope()),
                    new Subscription(message.lease(), source.identity())
            );
            invalidateMarkerSubscriptionSnapshot();
            sender.send(actorId, new RuntimeWireMessage.ScopeAck(
                    message.requestId(), message.lease(), source.identity()
            ));
            for (WireTransfer.TransferFragment transfer : manifestFragments) {
                sender.send(actorId, new RuntimeWireMessage.Manifest(
                        Optional.of(message.requestId()), message.lease(),
                        source.identity(), transfer
                ));
            }
            if (initialReset.isPresent()) {
                sendMarkerReset(
                        actorId, Optional.of(message.requestId()), message.lease(),
                        source.identity(), initialReset.orElseThrow()
                );
            }
        } catch (IOException | RuntimeException unavailable) {
            error(actorId, message.requestId(), message.lease(), Optional.empty(),
                    message.opcode().code(), MinimapErrorCode.MAP_UNAVAILABLE,
                    WireStatus.RetryDisposition.RETRY_NEW_REQUEST,
                    "Runtime map is unavailable");
        }
    }

    private void unsubscribe(UUID actorId, RuntimeWireMessage.Unsubscribe message) {
        SubscriptionKey key = new SubscriptionKey(actorId, message.lease().scope());
        Subscription current = subscriptions.get(key);
        if (current != null
                && current.lease().equals(message.lease())
                && current.identity().binding().target().equals(message.target())) {
            subscriptions.remove(key);
            invalidateMarkerSubscriptionSnapshot();
            if (subscriptions.keySet().stream().noneMatch(candidate ->
                    candidate.actorId().equals(actorId))) {
                markerStreams.remove(actorId);
            }
        }
    }

    private void requestEntries(
            UUID actorId,
            RuntimeWireMessage.RequestEntries message
    ) {
        Subscription subscription = subscriptions.get(new SubscriptionKey(
                actorId, message.lease().scope()
        ));
        if (subscription == null
                || !subscription.lease().equals(message.lease())
                || !subscription.identity().equals(message.runtime())) {
            error(actorId, message.requestId(), message.lease(),
                    Optional.of(message.runtime().binding()), message.opcode().code(),
                    MinimapErrorCode.SCOPE_MISMATCH,
                    WireStatus.RetryDisposition.RESYNC_SCOPE,
                    "Runtime scope lease is stale");
            return;
        }
        try (RuntimeMapSource source = resolve(
                actorId, message.runtime().binding().target()
        ).orElse(null)) {
            if (source == null) {
                subscriptions.remove(new SubscriptionKey(
                        actorId, message.lease().scope()
                ));
                invalidateMarkerSubscriptionSnapshot();
                error(actorId, message.requestId(), message.lease(),
                        Optional.of(message.runtime().binding()), message.opcode().code(),
                        MinimapErrorCode.UNAUTHORIZED,
                        WireStatus.RetryDisposition.DO_NOT_RETRY,
                        "Runtime map authorization changed");
                return;
            }
            if (!source.identity().equals(message.runtime())) {
                error(actorId, message.requestId(), message.lease(),
                        Optional.of(message.runtime().binding()), message.opcode().code(),
                        MinimapErrorCode.REVISION_CONFLICT,
                        WireStatus.RetryDisposition.RESYNC_SCOPE,
                        "Runtime identity changed");
                return;
            }
            for (WireTransfer.EntryRequest request : message.entries()) {
                Optional<com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeEntryDescriptor>
                        descriptor = source.descriptor(request.path());
                if (descriptor.isEmpty()) {
                    error(actorId, message.requestId(), message.lease(),
                            Optional.of(message.runtime().binding()), message.opcode().code(),
                            MinimapErrorCode.ENTRY_NOT_FOUND,
                            WireStatus.RetryDisposition.DO_NOT_RETRY,
                            "Runtime entry is not declared");
                    return;
                }
                if (!descriptor.orElseThrow().sha256().equals(request.expectedHash())) {
                    error(actorId, message.requestId(), message.lease(),
                            Optional.of(message.runtime().binding()), message.opcode().code(),
                            MinimapErrorCode.HASH_MISMATCH,
                            WireStatus.RetryDisposition.RESYNC_SCOPE,
                            "Runtime entry hash changed");
                    return;
                }
            }
            for (WireTransfer.EntryRequest request : message.entries()) {
                var descriptor = source.descriptor(request.path()).orElseThrow();
                try (InputStream input = source.openEntry(request.path())) {
                    sendEntryFragments(actorId, message, request.path(), descriptor, input);
                }
            }
        } catch (IOException | RuntimeException unavailable) {
            error(actorId, message.requestId(), message.lease(),
                    Optional.of(message.runtime().binding()), message.opcode().code(),
                    MinimapErrorCode.MAP_UNAVAILABLE,
                    WireStatus.RetryDisposition.RETRY_NEW_REQUEST,
                    "Runtime entry source is unavailable");
        }
    }

    private void requestMarkerReset(
            UUID actorId,
            RuntimeWireMessage.RequestMarkerReset message
    ) {
        Subscription subscription = subscriptions.get(new SubscriptionKey(
                actorId, message.lease().scope()
        ));
        if (subscription == null
                || !subscription.lease().equals(message.lease())
                || !subscription.identity().equals(message.runtime())) {
            error(actorId, message.requestId(), message.lease(),
                    Optional.of(message.runtime().binding()), message.opcode().code(),
                    MinimapErrorCode.SCOPE_MISMATCH,
                    WireStatus.RetryDisposition.RESYNC_SCOPE,
                    "Runtime scope lease is stale");
            return;
        }
        try (RuntimeMapSource source = resolve(
                actorId, message.runtime().binding().target()
        ).orElse(null)) {
            if (source == null) {
                subscriptions.remove(new SubscriptionKey(
                        actorId, message.lease().scope()
                ));
                invalidateMarkerSubscriptionSnapshot();
                markerStreams.remove(actorId);
                error(actorId, message.requestId(), message.lease(),
                        Optional.of(message.runtime().binding()),
                        message.opcode().code(), MinimapErrorCode.UNAUTHORIZED,
                        WireStatus.RetryDisposition.DO_NOT_RETRY,
                        "Runtime map authorization changed");
                return;
            }
            if (!source.identity().equals(message.runtime())) {
                error(actorId, message.requestId(), message.lease(),
                        Optional.of(message.runtime().binding()),
                        message.opcode().code(), MinimapErrorCode.REVISION_CONFLICT,
                        WireStatus.RetryDisposition.RESYNC_SCOPE,
                        "Runtime identity changed");
                return;
            }
            Optional<RuntimeMarkerSnapshot> resolved = markerResolver.resolve(
                    actorId, message.runtime().binding().target()
            );
            if (resolved.isEmpty()) {
                error(actorId, message.requestId(), message.lease(),
                        Optional.of(message.runtime().binding()),
                        message.opcode().code(), MinimapErrorCode.UNAUTHORIZED,
                        WireStatus.RetryDisposition.DO_NOT_RETRY,
                        "Marker visibility context is unavailable");
                return;
            }
            RuntimeMarkerSnapshot snapshot = resolved.orElseThrow();
            MarkerStreamUpdate reset = markerStreams.computeIfAbsent(
                    actorId,
                    ignored -> markerStream()
            ).subscribe(snapshot.viewer(), snapshot.markers());
            sendMarkerReset(
                    actorId, Optional.of(message.requestId()), message.lease(),
                    message.runtime(), reset
            );
        } catch (IOException | RuntimeException unavailable) {
            error(actorId, message.requestId(), message.lease(),
                    Optional.of(message.runtime().binding()),
                    message.opcode().code(), MinimapErrorCode.MAP_UNAVAILABLE,
                    WireStatus.RetryDisposition.RETRY_NEW_REQUEST,
                    "Marker snapshot is unavailable");
        }
    }

    private void sendMarkerReset(
            UUID actorId,
            Optional<UUID> requestId,
            WireIdentity.ScopeLease lease,
            WireIdentity.RuntimeIdentity runtime,
            MarkerStreamUpdate reset
    ) {
        List<WireMarker.Marker> markers = reset.markers().stream()
                .map(ServerMinimapRuntimeRouter::wireMarker)
                .toList();
        int pageCount = Math.max(
                1,
                (markers.size() + MarkerWireMessage.MAX_PAGE_ITEMS - 1)
                        / MarkerWireMessage.MAX_PAGE_ITEMS
        );
        UUID resetId = transferIds.get();
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            int from = pageIndex * MarkerWireMessage.MAX_PAGE_ITEMS;
            int to = Math.min(
                    markers.size(), from + MarkerWireMessage.MAX_PAGE_ITEMS
            );
            sender.send(actorId, new MarkerWireMessage.Reset(
                    requestId,
                    lease,
                    runtime,
                    reset.streamEpoch(),
                    reset.sequence(),
                    resetId,
                    pageIndex,
                    pageCount,
                    markers.subList(from, to)
            ));
        }
    }

    private static WireMarker.Marker wireMarker(MarkerSnapshot.Marker marker) {
        return new WireMarker.Marker(
                marker.markerId(), marker.typeId(), marker.styleId(),
                marker.x(), marker.y(), marker.z(), marker.yaw(),
                marker.updatedTick(), marker.expiresTick(), marker.floorSlug(),
                marker.stateFields()
        );
    }

    private static WireMarker.DeltaOperation wireOperation(
            com.phasetranscrystal.fpsmatch.core.minimap.marker.MarkerDelta operation,
            IdentityHashMap<MarkerSnapshot.Marker, WireMarker.Marker>
                    wireMarkerCache
    ) {
        if (operation instanceof com.phasetranscrystal.fpsmatch.core.minimap.marker
                .MarkerDelta.Add add) {
            return new WireMarker.Add(wireMarkerCache.computeIfAbsent(
                    add.marker(), ServerMinimapRuntimeRouter::wireMarker
            ));
        }
        if (operation instanceof com.phasetranscrystal.fpsmatch.core.minimap.marker
                .MarkerDelta.Update update) {
            return new WireMarker.Update(wireMarkerCache.computeIfAbsent(
                    update.marker(), ServerMinimapRuntimeRouter::wireMarker
            ));
        }
        return new WireMarker.Remove(
                ((com.phasetranscrystal.fpsmatch.core.minimap.marker.MarkerDelta.Remove)
                        operation).markerId()
        );
    }

    private Map<UUID, Subscription> markerSubscriptions() {
        if (markerSubscriptionSnapshot != null) {
            return markerSubscriptionSnapshot;
        }
        java.util.TreeMap<UUID, Subscription> result = new java.util.TreeMap<>();
        subscriptions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((left, right) -> {
                    int actor = left.actorId().compareTo(right.actorId());
                    if (actor != 0) {
                        return actor;
                    }
                    return Integer.compare(
                            left.scope().ordinal(), right.scope().ordinal()
                    );
                }))
                .forEach(entry -> result.putIfAbsent(
                        entry.getKey().actorId(), entry.getValue()
                ));
        markerSubscriptionSnapshot = Collections.unmodifiableMap(result);
        return markerSubscriptionSnapshot;
    }

    private void invalidateMarkerSubscriptionSnapshot() {
        markerSubscriptionSnapshot = null;
    }

    private static MarkerStreamManager markerStream() {
        return new MarkerStreamManager(
                context -> List.of(),
                (context, candidates) -> List.of()
        );
    }

    private static com.phasetranscrystal.fpsmatch.core.minimap.marker
            .MinimapViewerContext emptyViewer() {
        return new com.phasetranscrystal.fpsmatch.core.minimap.marker
                .MinimapViewerContext(
                com.phasetranscrystal.fpsmatch.core.minimap.marker.ViewerRole
                        .SPECTATOR_TEAM,
                "unavailable", Optional.empty(), false, false
        );
    }

    private static long markerSlot(long nowTick, int frequency) {
        return Math.addExact(
                Math.multiplyExact(nowTick / 20L, frequency),
                nowTick % 20L * frequency / 20L
        );
    }

    private static boolean markerDue(
            UUID actorId,
            long nowTick,
            int frequency
    ) {
        long mixed = actorId.getMostSignificantBits()
                ^ actorId.getLeastSignificantBits();
        long phase = Math.floorMod(mixed - 1L, 20L);
        long offset = Math.floorMod(20L - phase, 20L);
        long shiftedTick = Math.addExact(nowTick, offset);
        return markerSlot(shiftedTick, frequency)
                > markerSlot(shiftedTick - 1L, frequency);
    }

    private void sendEntryFragments(
            UUID actorId,
            RuntimeWireMessage.RequestEntries message,
            ContainerPath path,
            com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeEntryDescriptor descriptor,
            InputStream input
    ) throws IOException {
        int count = Math.toIntExact(
                (descriptor.byteLength() - 1L)
                        / MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES + 1L
        );
        UUID transferId = transferIds.get();
        long remaining = descriptor.byteLength();
        for (int index = 0; index < count; index++) {
            int length = (int) Math.min(
                    remaining, MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES
            );
            byte[] bytes = input.readNBytes(length);
            if (bytes.length != length) {
                throw new IOException("Runtime entry ended before its declared length");
            }
            sender.send(actorId, new RuntimeWireMessage.EntryFragment(
                    message.requestId(), message.lease(), message.runtime(), path,
                    new WireTransfer.TransferFragment(
                            transferId, index, count, descriptor.byteLength(),
                            descriptor.sha256(), Sha256Digest.of(bytes), bytes
                    )
            ));
            remaining -= length;
        }
        if (input.read() != -1) {
            throw new IOException("Runtime entry exceeds its declared length");
        }
    }

    private Optional<RuntimeMapSource> resolve(
            UUID actorId,
            WireIdentity.MapTarget target
    ) {
        return resolver.resolve(actorId, target);
    }

    private List<WireTransfer.TransferFragment> fragments(
            byte[] payload,
            com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256 objectHash
    ) {
        int count = (payload.length - 1)
                / MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES + 1;
        UUID transferId = transferIds.get();
        java.util.ArrayList<WireTransfer.TransferFragment> fragments =
                new java.util.ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int from = index * MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES;
            int to = Math.min(
                    payload.length, from + MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES
            );
            byte[] bytes = Arrays.copyOfRange(payload, from, to);
            fragments.add(new WireTransfer.TransferFragment(
                    transferId, index, count, payload.length, objectHash,
                    Sha256Digest.of(bytes), bytes
            ));
        }
        return List.copyOf(fragments);
    }

    private void error(
            UUID actorId,
            UUID requestId,
            WireIdentity.ScopeLease lease,
            Optional<WireIdentity.DocumentBinding> binding,
            int opcode,
            MinimapErrorCode code,
            WireStatus.RetryDisposition retry,
            String detail
    ) {
        sender.send(actorId, new PublishWireMessage.ErrorMessage(
                Optional.of(requestId), Optional.of(lease), binding,
                Optional.of(opcode),
                new WireStatus.ErrorInfo(code.code(), retry, detail)
        ));
    }

    @FunctionalInterface
    public interface RuntimeResolver {
        Optional<RuntimeMapSource> resolve(
                UUID actorId,
                WireIdentity.MapTarget target
        );
    }

    @FunctionalInterface
    public interface MarkerResolver {
        Optional<RuntimeMarkerSnapshot> resolve(
                UUID actorId,
                WireIdentity.MapTarget target
        );
    }

    @FunctionalInterface
    public interface Sender {
        void send(UUID actorId, MinimapWireMessage message);
    }

    private record SubscriptionKey(UUID actorId, WireIdentity.Scope scope) {
    }

    private record Subscription(
            WireIdentity.ScopeLease lease,
            WireIdentity.RuntimeIdentity identity
    ) {
    }
}
