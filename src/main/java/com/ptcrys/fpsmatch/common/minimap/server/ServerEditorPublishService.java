package com.ptcrys.fpsmatch.common.minimap.server;

import com.ptcrys.fpsmatch.common.capability.map.MinimapCapability;
import com.ptcrys.fpsmatch.core.FPSMCore;
import com.ptcrys.fpsmatch.core.map.BaseMap;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.ptcrys.fpsmatch.core.minimap.editor.publish.EditorPublishArtifacts;
import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import com.ptcrys.fpsmatch.core.minimap.model.CanvasBounds;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.storage.MinimapRepository;
import com.ptcrys.fpsmatch.core.minimap.storage.PublishOutcome;
import com.ptcrys.fpsmatch.core.minimap.storage.PublishTransaction;
import com.ptcrys.fpsmatch.core.minimap.wire.PublishWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;
import com.ptcrys.fpsmatch.core.minimap.wire.WireStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Completes empty-editor publish: repository commit + map capability binding.
 */
public final class ServerEditorPublishService {
    private static final Sha256 EMPTY_HASH = Sha256Digest.of(new byte[0]);
    private static final int DEFAULT_CANVAS = 512;
    private static final int DEFAULT_TILE_EDGE = 128;
    private static final String DEFAULT_FLOOR = "ground";

    private final MinimapRepository repository;
    private final EditorSessionManager sessions;
    private final MinimapPermissionPolicy permissions;
    private final BiConsumer<UUID, PublishWireMessage> sender;

    public ServerEditorPublishService(
            MinimapRepository repository,
            EditorSessionManager sessions,
            MinimapPermissionPolicy permissions,
            BiConsumer<UUID, PublishWireMessage> sender
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    public void publishEmpty(
            UUID actorId,
            PublishWireMessage.ReservePublish request
    ) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(request, "request");
        WireIdentity.EditorContext context = request.context();
        MapKey mapKey = context.binding().target().mapKey();
        NamespacedId dimension = context.binding().target().dimension();
        NamespacedId documentId = context.binding().documentId();
        try {
            requirePermission(actorId, mapKey, MinimapAction.RESERVE_PUBLISH);
            requirePermission(actorId, mapKey, MinimapAction.COMMIT_PUBLISH);
            EditorSession session = sessions.authorize(
                    actorId,
                    context.sessionId(),
                    mapKey,
                    dimension,
                    documentId,
                    context.draftId(),
                    context.baseRevision(),
                    MinimapAction.COMMIT_PUBLISH
            );

            long baseRevision = session.baseRevision();
            PublishTransaction reserved = repository.reserve(
                    mapKey, dimension, documentId, baseRevision
            );
            long publishRevision = reserved.descriptor().publishRevision();
            EditorPublishArtifacts.Pair pair = EditorPublishArtifacts.buildEmpty(
                    mapKey,
                    dimension,
                    documentId,
                    publishRevision,
                    new CanvasBounds(DEFAULT_CANVAS, DEFAULT_CANVAS),
                    DEFAULT_TILE_EDGE,
                    DEFAULT_FLOOR
            );
            PublishTransaction prepared = repository.prepare(
                    reserved, pair.sourceBytes(), pair.runtimeBytes()
            );
            PublishOutcome outcome = repository.commit(prepared);
            if (!outcome.committed()) {
                sendFailure(
                        actorId,
                        request,
                        context,
                        reserved.descriptor().publishToken(),
                        publishRevision,
                        MinimapErrorCode.INTERNAL_ERROR,
                        "Publish commit did not complete: " + outcome.message()
                );
                return;
            }

            MinimapCapability.Binding binding = new MinimapCapability.Binding(
                    dimension,
                    documentId,
                    publishRevision,
                    pair.sourceHash(),
                    pair.runtimeHash()
            );
            if (!bindCapability(mapKey, binding)) {
                sendFailure(
                        actorId,
                        request,
                        context,
                        reserved.descriptor().publishToken(),
                        publishRevision,
                        MinimapErrorCode.INTERNAL_ERROR,
                        "Published containers but failed to bind map capability"
                );
                return;
            }

            sender.accept(actorId, new PublishWireMessage.PublishResult(
                    request.requestId(),
                    context.lease(),
                    context.binding(),
                    reserved.descriptor().publishToken(),
                    publishRevision,
                    WireStatus.PublishOutcome.COMMITTED,
                    Optional.of(new WireStatus.HashTriple(
                            pair.sourceHash(),
                            pair.runtimeHash(),
                            pair.runtimeContainerHash()
                    )),
                    Optional.empty()
            ));
        } catch (SessionAccessException denied) {
            sendFailure(
                    actorId,
                    request,
                    context,
                    "denied",
                    0L,
                    denied.errorCode(),
                    denied.getMessage() == null ? "publish denied" : denied.getMessage()
            );
        } catch (RuntimeException failure) {
            sendFailure(
                    actorId,
                    request,
                    context,
                    "error",
                    0L,
                    MinimapErrorCode.INTERNAL_ERROR,
                    failure.getMessage() == null ? "publish failed" : failure.getMessage()
            );
        }
    }

    private void requirePermission(UUID actorId, MapKey mapKey, MinimapAction action) {
        boolean allowed;
        try {
            allowed = permissions.mayPerform(actorId, mapKey, action).orElse(false);
        } catch (RuntimeException exception) {
            allowed = false;
        }
        if (!allowed) {
            throw new SessionAccessException(
                    MinimapErrorCode.UNAUTHORIZED,
                    "Minimap editor publish action denied"
            );
        }
    }

    private static boolean bindCapability(MapKey mapKey, MinimapCapability.Binding binding) {
        if (!FPSMCore.initialized()) {
            return false;
        }
        Optional<BaseMap> map = FPSMCore.getInstance()
                .getMapByTypeWithName(mapKey.gameType(), mapKey.mapName());
        if (map.isEmpty()) {
            return false;
        }
        BaseMap baseMap = map.orElseThrow();
        if (baseMap.getCapabilityMap().get(MinimapCapability.class).isEmpty()) {
            if (!baseMap.getCapabilityMap().add(MinimapCapability.class)) {
                return false;
            }
        }
        baseMap.getCapabilityMap().write(MinimapCapability.class, binding);
        return baseMap.getCapabilityMap().get(MinimapCapability.class)
                .flatMap(MinimapCapability::binding)
                .filter(value -> value.equals(binding))
                .isPresent();
    }

    private void sendFailure(
            UUID actorId,
            PublishWireMessage.ReservePublish request,
            WireIdentity.EditorContext context,
            String token,
            long revision,
            MinimapErrorCode code,
            String detail
    ) {
        sender.accept(actorId, new PublishWireMessage.PublishResult(
                request.requestId(),
                context.lease(),
                context.binding(),
                token == null || token.isBlank() ? "error" : token,
                Math.max(0L, revision),
                WireStatus.PublishOutcome.ABORTED,
                Optional.empty(),
                Optional.of(new WireStatus.ErrorInfo(
                        code.code(),
                        WireStatus.RetryDisposition.DO_NOT_RETRY,
                        detail == null || detail.isBlank() ? "publish failed" : detail
                ))
        ));
    }

    public static Sha256 emptyHash() {
        return EMPTY_HASH;
    }
}
