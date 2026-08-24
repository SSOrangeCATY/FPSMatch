package com.ptcrys.fpsmatch.common.minimap.server.sync;

import com.ptcrys.fpsmatch.common.minimap.server.DraftAck;
import com.ptcrys.fpsmatch.common.minimap.server.DraftException;
import com.ptcrys.fpsmatch.common.minimap.server.DraftState;
import com.ptcrys.fpsmatch.common.minimap.server.DraftStore;
import com.ptcrys.fpsmatch.common.minimap.server.EditorSession;
import com.ptcrys.fpsmatch.common.minimap.server.EditorSessionManager;
import com.ptcrys.fpsmatch.common.minimap.server.MinimapAction;
import com.ptcrys.fpsmatch.common.minimap.server.MinimapBindingCoordinator;
import com.ptcrys.fpsmatch.common.minimap.server.MinimapPermissionPolicy;
import com.ptcrys.fpsmatch.common.minimap.server.ServerEditorContextAuthority;
import com.ptcrys.fpsmatch.common.minimap.server.ServerEditorPublishService;
import com.ptcrys.fpsmatch.common.minimap.server.SessionAccessException;
import com.ptcrys.fpsmatch.common.minimap.server.UploadException;
import com.ptcrys.fpsmatch.common.minimap.server.UploadManager;
import com.ptcrys.fpsmatch.common.minimap.server.UploadOwnerScope;
import com.ptcrys.fpsmatch.common.minimap.server.UploadProgress;
import com.ptcrys.fpsmatch.common.minimap.server.UploadReservation;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.format.ContainerLimits;
import com.ptcrys.fpsmatch.core.minimap.format.MinimapContainerLayout;
import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import com.ptcrys.fpsmatch.core.minimap.format.SourceMap;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.MediaType;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.model.SourceEntryDescriptor;
import com.ptcrys.fpsmatch.core.minimap.wire.EditorWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.PublishWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireEditor;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;
import com.ptcrys.fpsmatch.core.minimap.wire.WireTransfer;
import com.ptcrys.fpsmatch.core.minimap.wire.WireStatus;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Owns the authoritative editor protocol behind the runtime router's synchronized boundary. */
public final class ServerMinimapEditorRouter {
    private static final Sha256 EMPTY = Sha256Digest.of(new byte[0]);
    private static final Comparator<UploadOwnerScope> UPLOAD_SCOPE_ORDER =
            Comparator.comparing(UploadOwnerScope::actorId)
                    .thenComparing(UploadOwnerScope::sessionId)
                    .thenComparing(UploadOwnerScope::draftId)
                    .thenComparingLong(UploadOwnerScope::baseRevision);

    private final EditorSessionManager sessions;
    private final MinimapPermissionPolicy permissions;
    private final DraftStore drafts;
    private final UploadManager uploads;
    private final MinimapBindingCoordinator bindings;
    private final ServerEditorPublishService publish;
    private final ServerEditorContextAuthority contextAuthority;
    private final ServerMinimapRuntimeRouter.Sender sender;
    private final SourceResolver sourceResolver;
    private final Supplier<UUID> transferIds;
    private final java.util.Map<UploadOwnerScope, java.util.Map<UUID, UploadReservation>>
            activeUploads = new ConcurrentHashMap<>();
    private final java.util.Map<UploadOwnerScope, MapKey> uploadMaps =
            new ConcurrentHashMap<>();

    public ServerMinimapEditorRouter(
            EditorSessionManager sessions,
            MinimapPermissionPolicy permissions,
            DraftStore drafts,
            UploadManager uploads,
            MinimapBindingCoordinator bindings,
            ServerEditorPublishService publish,
            ServerMinimapRuntimeRouter.Sender sender
    ) {
        this(
                sessions, permissions, drafts, uploads, bindings, publish, sender,
                (actorId, context) -> Optional.empty(), UUID::randomUUID
        );
    }

    /** Constructor used by the runtime factory and deterministic protocol tests. */
    public ServerMinimapEditorRouter(
            EditorSessionManager sessions,
            MinimapPermissionPolicy permissions,
            DraftStore drafts,
            UploadManager uploads,
            MinimapBindingCoordinator bindings,
            ServerEditorPublishService publish,
            ServerMinimapRuntimeRouter.Sender sender,
            SourceResolver sourceResolver,
            Supplier<UUID> transferIds
    ) {
        this(
                sessions, permissions, drafts, uploads, bindings, publish, sender,
                sourceResolver, transferIds,
                new ServerEditorContextAuthority(sessions, drafts, bindings)
        );
    }

    public ServerMinimapEditorRouter(
            EditorSessionManager sessions,
            MinimapPermissionPolicy permissions,
            DraftStore drafts,
            UploadManager uploads,
            MinimapBindingCoordinator bindings,
            ServerEditorPublishService publish,
            ServerMinimapRuntimeRouter.Sender sender,
            SourceResolver sourceResolver,
            Supplier<UUID> transferIds,
            ServerEditorContextAuthority contextAuthority
    ) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.drafts = Objects.requireNonNull(drafts, "drafts");
        this.uploads = Objects.requireNonNull(uploads, "uploads");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.publish = Objects.requireNonNull(publish, "publish");
        this.contextAuthority = Objects.requireNonNull(
                contextAuthority, "contextAuthority"
        );
        this.sender = Objects.requireNonNull(sender, "sender");
        this.sourceResolver = Objects.requireNonNull(sourceResolver, "sourceResolver");
        this.transferIds = Objects.requireNonNull(transferIds, "transferIds");
    }

    /** Convenience overload retaining random transfer IDs for embedders. */
    public ServerMinimapEditorRouter(
            EditorSessionManager sessions,
            MinimapPermissionPolicy permissions,
            DraftStore drafts,
            UploadManager uploads,
            MinimapBindingCoordinator bindings,
            ServerEditorPublishService publish,
            ServerMinimapRuntimeRouter.Sender sender,
            SourceResolver sourceResolver
    ) {
        this(
                sessions, permissions, drafts, uploads, bindings, publish, sender,
                sourceResolver, UUID::randomUUID
        );
    }

    public boolean allow(UUID actorId, MinimapWireMessage message) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(message, "message");
        try {
            if (message instanceof EditorWireMessage.EditorOpen open) {
                return permissions.mayPerform(
                        actorId, open.target().mapKey(), MinimapAction.OPEN_EDITOR
                ).orElse(false);
            }
            if (message instanceof EditorWireMessage.EditorResume resume) {
                return allowResume(actorId, resume);
            }
            if (message instanceof PublishWireMessage.QueryPublishStatus query) {
                return permissions.mayPerform(
                        actorId, query.binding().target().mapKey(),
                        MinimapAction.QUERY_PUBLISH_STATUS
                ).orElse(false);
            }
            Authorization authorization = authorization(message);
            if (authorization == null) {
                return false;
            }
            authorize(actorId, authorization.context(), authorization.action());
            return true;
        } catch (RuntimeException rejected) {
            return false;
        }
    }

    public void dispatch(UUID actorId, MinimapWireMessage message) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(message, "message");
        try {
            if (message instanceof EditorWireMessage.EditorOpen value) {
                open(actorId, value);
            } else if (message instanceof EditorWireMessage.EditorResume value) {
                resume(actorId, value);
            } else if (message instanceof EditorWireMessage.RequestSourceEntries value) {
                sourceEntries(actorId, value);
            } else if (message instanceof EditorWireMessage.EditorOperation value) {
                operation(actorId, value);
            } else if (message instanceof EditorWireMessage.UploadFragment value) {
                upload(actorId, value);
            } else if (message instanceof EditorWireMessage.SaveDraft value) {
                save(actorId, value);
            } else if (message instanceof EditorWireMessage.EditorClose value) {
                close(actorId, value);
            } else if (message instanceof PublishWireMessage.ReservePublish value) {
                publish.publish(actorId, value);
            } else if (message instanceof PublishWireMessage.QueryPublishStatus value) {
                publish.query(actorId, value);
            } else if (message instanceof PublishWireMessage.EditorRebase value) {
                error(actorId, value.requestId(), value.context(), value.opcode().code(),
                        MinimapErrorCode.REVISION_CONFLICT,
                        WireStatus.RetryDisposition.REOPEN_SESSION,
                        "Rebase requires reopening the authoritative revision");
            }
        } catch (SessionAccessException failure) {
            errorFor(actorId, message,
                    failure.errorCode(), WireStatus.RetryDisposition.REOPEN_SESSION,
                    detail(failure, "Editor action denied"));
        } catch (DraftException failure) {
            errorFor(actorId, message,
                    failure.errorCode(), WireStatus.RetryDisposition.RESYNC_SCOPE,
                    detail(failure, "Draft action failed"));
        } catch (UploadException failure) {
            errorFor(actorId, message,
                    failure.errorCode(), WireStatus.RetryDisposition.RETRY_NEW_REQUEST,
                    detail(failure, "Upload action failed"));
        }
    }

    public void onPlayerLogout(UUID actorId) {
        LifecycleFailures.runAll(
                () -> sessions.invalidateActor(actorId),
                () -> contextAuthority.clearActor(actorId),
                () -> publish.clearActor(actorId),
                () -> closeUploadScopes(scope -> scope.actorId().equals(actorId))
        );
    }

    public void invalidateMap(MapKey mapKey) {
        LifecycleFailures.runAll(
                () -> sessions.invalidateMap(mapKey),
                () -> contextAuthority.clearMap(mapKey),
                () -> closeUploadScopes(scope -> mapKey.equals(uploadMaps.get(scope)))
        );
    }

    private void open(UUID actorId, EditorWireMessage.EditorOpen open) {
        var current = bindings.preflight(
                open.target().mapKey(), open.target().dimension(),
                open.documentId(), open.expectedRevision()
        );
        if (open.openMode() == WireEditor.OpenMode.CREATE_EMPTY && current != null) {
            throw new SessionAccessException(
                    MinimapErrorCode.REVISION_CONFLICT,
                    "CREATE_EMPTY requires an unbound map"
            );
        }
        if (open.openMode() != WireEditor.OpenMode.CREATE_EMPTY
                && open.openMode() != WireEditor.OpenMode.OPEN_EXISTING) {
            throw new SessionAccessException(
                    MinimapErrorCode.MALFORMED_MESSAGE,
                    "Editor open mode is not server-supported"
            );
        }
        Sha256 baseHash = current == null ? EMPTY : current.sourceHash();
        DraftState draft = drafts.create(
                open.target().mapKey(), open.target().dimension(), open.documentId(),
                open.expectedRevision(), baseHash, EMPTY
        );
        EditorSession session = sessions.open(
                actorId, open.target().mapKey(), open.target().dimension(),
                open.documentId(), draft.draftId(), open.expectedRevision()
        );
        LifecycleFailures.runAll(
                () -> contextAuthority.clearActor(actorId),
                () -> closeUploadScopes(scope -> scope.actorId().equals(actorId))
        );
        WireIdentity.EditorContext context = new WireIdentity.EditorContext(
                open.lease(), new WireIdentity.DocumentBinding(open.target(), open.documentId()),
                session.sessionId(), draft.draftId(), session.baseRevision(), baseHash,
                draft.draftRootHash(), draft.ackCursor()
        );
        contextAuthority.activate(actorId, context);
        try {
            sender.send(actorId, new EditorWireMessage.EditorSession(
                    open.requestId(), context, session.expiresAt().toEpochMilli(),
                    current == null ? WireEditor.SourceAvailability.NONE
                            : WireEditor.SourceAvailability.FULL_SOURCE
            ));
        } catch (RuntimeException | Error failure) {
            contextAuthority.clearIfMatches(actorId, context);
            throw failure;
        }
    }

    private void resume(UUID actorId, EditorWireMessage.EditorResume resume) {
        DraftState draft = drafts.requireRoot(resume.draftId(), resume.draftRootHash());
        authorizeResume(actorId, resume, draft);
        bindings.preflight(
                draft.mapKey(), draft.dimension(), draft.documentId(), draft.baseRevision()
        );
        EditorSession session = sessions.open(
                actorId, draft.mapKey(), draft.dimension(), draft.documentId(),
                draft.draftId(), draft.baseRevision()
        );
        LifecycleFailures.runAll(
                () -> contextAuthority.clearActor(actorId),
                () -> closeUploadScopes(scope -> scope.actorId().equals(actorId))
        );
        WireIdentity.EditorContext context = new WireIdentity.EditorContext(
                resume.lease(), resume.binding(), session.sessionId(), draft.draftId(),
                draft.baseRevision(), draft.baseSourceHash(), draft.draftRootHash(),
                draft.ackCursor()
        );
        WireEditor.SourceAvailability availability = sourceAvailability(actorId, context);
        contextAuthority.activate(actorId, context);
        try {
            sender.send(actorId, new EditorWireMessage.EditorSession(
                    resume.requestId(), context, session.expiresAt().toEpochMilli(),
                    availability
            ));
        } catch (RuntimeException | Error failure) {
            contextAuthority.clearIfMatches(actorId, context);
            throw failure;
        }
    }

    private boolean allowResume(UUID actorId, EditorWireMessage.EditorResume resume) {
        DraftState draft = drafts.requireRoot(resume.draftId(), resume.draftRootHash());
        authorizeResume(actorId, resume, draft);
        bindings.preflight(
                draft.mapKey(), draft.dimension(), draft.documentId(), draft.baseRevision()
        );
        return true;
    }

    private void authorizeResume(
            UUID actorId,
            EditorWireMessage.EditorResume resume,
            DraftState draft
    ) {
        if (draft.ackCursor() != resume.ackCursor()) {
            throw new DraftException(
                    MinimapErrorCode.REVISION_CONFLICT, "Draft ACK cursor changed"
            );
        }
        if (!draft.mapKey().equals(resume.binding().target().mapKey())
                || !draft.dimension().equals(resume.binding().target().dimension())
                || !draft.documentId().equals(resume.binding().documentId())) {
            throw new SessionAccessException(
                    MinimapErrorCode.SCOPE_MISMATCH, "Editor resume binding changed"
            );
        }
        boolean allowed;
        try {
            allowed = permissions.mayPerform(
                    actorId, draft.mapKey(), MinimapAction.OPEN_EDITOR
            ).orElse(false);
        } catch (RuntimeException policyFailure) {
            allowed = false;
        }
        if (!allowed) {
            throw new SessionAccessException(
                    MinimapErrorCode.UNAUTHORIZED, "Minimap editor action denied"
            );
        }
    }

    private WireEditor.SourceAvailability sourceAvailability(
            UUID actorId,
            WireIdentity.EditorContext context
    ) {
        SourceMap source = null;
        try {
            source = sourceResolver.resolve(actorId, context).orElse(null);
            if (source == null) {
                return WireEditor.SourceAvailability.NONE;
            }
            if (!source.sourceHash().equals(context.baseSourceHash())) {
                throw new SessionAccessException(
                        MinimapErrorCode.SCOPE_MISMATCH,
                        "Authoritative editor source changed during resume"
                );
            }
            return WireEditor.SourceAvailability.FULL_SOURCE;
        } catch (SessionAccessException rejected) {
            throw rejected;
        } catch (RuntimeException unavailable) {
            throw new SessionAccessException(
                    MinimapErrorCode.MAP_UNAVAILABLE,
                    "Authoritative editor source is unavailable"
            );
        } finally {
            if (source != null) {
                try {
                    source.close();
                } catch (IOException ignored) {
                    // Source availability is already decided; close failure is non-authoritative.
                }
            }
        }
    }

    /** Serves only the source bound to the already-authorized editor session. */
    private void sourceEntries(
            UUID actorId,
            EditorWireMessage.RequestSourceEntries request
    ) {
        WireIdentity.EditorContext context = request.context();
        authorize(actorId, context, MinimapAction.FETCH_SOURCE);
        // Capability visibility is the serving authority; do not read a committed
        // container after its runtime binding has been removed or changed.
        bindings.preflight(
                context.binding().target().mapKey(),
                context.binding().target().dimension(),
                context.binding().documentId(),
                context.baseRevision()
        );
        SourceMap source = null;
        try {
            source = sourceResolver.resolve(actorId, context).orElse(null);
            if (source == null) {
                sourceError(
                        actorId, request, MinimapErrorCode.MAP_UNAVAILABLE,
                        WireStatus.RetryDisposition.RETRY_NEW_REQUEST,
                        "Authoritative editor source is unavailable"
                );
                return;
            }
            if (!source.sourceHash().equals(request.sourceHash())) {
                sourceError(
                        actorId, request, MinimapErrorCode.HASH_MISMATCH,
                        WireStatus.RetryDisposition.RESYNC_SCOPE,
                        "Editor source hash changed"
                );
                return;
            }
            if (!source.sourceHash().equals(context.baseSourceHash())) {
                sourceError(
                        actorId, request, MinimapErrorCode.SCOPE_MISMATCH,
                        WireStatus.RetryDisposition.REOPEN_SESSION,
                        "Editor source scope changed"
                );
                return;
            }
            var manifest = source.manifest();
            if (manifest.revision() != context.baseRevision()
                    || !manifest.binding().equals(context.binding().target().mapKey())
                    || !manifest.dimension().equals(context.binding().target().dimension())
                    || !manifest.documentId().equals(context.binding().documentId())) {
                sourceError(
                        actorId, request, MinimapErrorCode.REVISION_CONFLICT,
                        WireStatus.RetryDisposition.REOPEN_SESSION,
                        "Editor source binding changed"
                );
                return;
            }

            Map<ContainerPath, SourceEntryDescriptor> descriptors = new HashMap<>();
            for (SourceEntryDescriptor descriptor : manifest.entries()) {
                if (descriptors.put(descriptor.path(), descriptor) != null) {
                    sourceError(
                            actorId, request, MinimapErrorCode.VALIDATION_FAILED,
                            WireStatus.RetryDisposition.RETRY_NEW_REQUEST,
                            "Editor source manifest contains duplicate paths"
                    );
                    return;
                }
            }
            if (request.entries().isEmpty()) {
                sendSourceManifest(actorId, request, source);
                return;
            }

            Set<ContainerPath> seen = new HashSet<>();
            long requestedBytes = 0L;
            for (WireTransfer.EntryRequest entry : request.entries()) {
                if (!seen.add(entry.path())) {
                    sourceError(
                            actorId, request, MinimapErrorCode.MALFORMED_MESSAGE,
                            WireStatus.RetryDisposition.DO_NOT_RETRY,
                            "Editor source entry request contains duplicate paths"
                    );
                    return;
                }
                SourceEntryDescriptor descriptor = descriptors.get(entry.path());
                if (descriptor == null
                        || entry.path().equals(MinimapContainerLayout.SOURCE_MANIFEST)
                        || !MinimapContainerLayout.isSourcePath(entry.path())) {
                    sourceError(
                            actorId, request, MinimapErrorCode.ENTRY_NOT_FOUND,
                            WireStatus.RetryDisposition.DO_NOT_RETRY,
                            "Editor source entry is not declared"
                    );
                    return;
                }
                if (!descriptor.sha256().equals(entry.expectedHash())) {
                    sourceError(
                            actorId, request, MinimapErrorCode.HASH_MISMATCH,
                            WireStatus.RetryDisposition.RESYNC_SCOPE,
                            "Editor source entry hash changed"
                    );
                    return;
                }
                try {
                    requestedBytes = Math.addExact(requestedBytes, descriptor.byteLength());
                } catch (ArithmeticException overflow) {
                    sourceError(
                            actorId, request, MinimapErrorCode.QUOTA_EXCEEDED,
                            WireStatus.RetryDisposition.DO_NOT_RETRY,
                            "Editor source request is too large"
                    );
                    return;
                }
                if (requestedBytes > ContainerLimits.sourceHardLimits().maxExpandedBytes()) {
                    sourceError(
                            actorId, request, MinimapErrorCode.QUOTA_EXCEEDED,
                            WireStatus.RetryDisposition.DO_NOT_RETRY,
                            "Editor source request is too large"
                    );
                    return;
                }
            }
            for (WireTransfer.EntryRequest entry : request.entries()) {
                SourceEntryDescriptor descriptor = descriptors.get(entry.path());
                sendSourceFragments(
                        actorId, request, entry.path(), descriptor,
                        source.sourceHash(), source.openEntry(entry.path()),
                        mediaType(descriptor.mediaType())
                );
            }
        } catch (IOException | RuntimeException unavailable) {
            sourceError(
                    actorId, request, MinimapErrorCode.MAP_UNAVAILABLE,
                    WireStatus.RetryDisposition.RETRY_NEW_REQUEST,
                    "Editor source entry is unavailable"
            );
        } finally {
            if (source != null) {
                try {
                    source.close();
                } catch (IOException ignored) {
                    // A permission or storage failure must not escape the network callback.
                }
            }
        }
    }

    private void sendSourceManifest(
            UUID actorId,
            EditorWireMessage.RequestSourceEntries request,
            SourceMap source
    ) {
        byte[] bytes = source.entryBytes(MinimapContainerLayout.SOURCE_MANIFEST);
        if (bytes.length > MinimapHardLimits.MAX_SOURCE_MANIFEST_BYTES) {
            sourceError(
                    actorId, request, MinimapErrorCode.MAP_UNAVAILABLE,
                    WireStatus.RetryDisposition.RETRY_NEW_REQUEST,
                    "Editor source manifest is unavailable"
            );
            return;
        }
        Sha256 manifestHash = Sha256Digest.of(bytes);
        for (WireTransfer.TransferFragment transfer : fragments(bytes, manifestHash)) {
            sender.send(actorId, new EditorWireMessage.SourceManifest(
                    request.requestId(), request.context(), source.sourceHash(),
                    manifestHash, transfer
            ));
        }
    }

    private void sendSourceFragments(
            UUID actorId,
            EditorWireMessage.RequestSourceEntries request,
            ContainerPath path,
            SourceEntryDescriptor descriptor,
            Sha256 sourceHash,
            InputStream input,
            WireEditor.MediaType mediaType
    ) throws IOException {
        try (input) {
            long length = descriptor.byteLength();
            if (length <= 0 || length > MinimapHardLimits.MAX_WIRE_TRANSFER_BYTES) {
                throw new IOException("Editor source entry length is invalid");
            }
            int count = Math.toIntExact(
                    (length - 1L) / MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES + 1L
            );
            UUID transferId = nextTransferId();
            long remaining = length;
            for (int index = 0; index < count; index++) {
                int size = Math.toIntExact(Math.min(
                        remaining, MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES
                ));
                byte[] bytes = input.readNBytes(size);
                if (bytes.length != size) {
                    throw new IOException("Editor source entry ended before its declared length");
                }
                sender.send(actorId, new EditorWireMessage.SourceFragment(
                        request.requestId(), request.context(), sourceHash,
                        path, mediaType,
                        new WireTransfer.TransferFragment(
                                transferId, index, count, length, descriptor.sha256(),
                                Sha256Digest.of(bytes), bytes
                        )
                ));
                remaining -= size;
            }
            if (input.read() != -1) {
                throw new IOException("Editor source entry exceeds its declared length");
            }
        }
    }

    private List<WireTransfer.TransferFragment> fragments(byte[] payload, Sha256 objectHash) {
        int count = (payload.length - 1) / MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES + 1;
        UUID transferId = nextTransferId();
        java.util.ArrayList<WireTransfer.TransferFragment> result =
                new java.util.ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int from = index * MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES;
            int to = Math.min(payload.length, from + MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES);
            byte[] bytes = java.util.Arrays.copyOfRange(payload, from, to);
            result.add(new WireTransfer.TransferFragment(
                    transferId, index, count, payload.length, objectHash,
                    Sha256Digest.of(bytes), bytes
            ));
        }
        return List.copyOf(result);
    }

    private UUID nextTransferId() {
        return Objects.requireNonNull(transferIds.get(), "transferIds returned null");
    }

    private void sourceError(
            UUID actorId,
            EditorWireMessage.RequestSourceEntries request,
            MinimapErrorCode code,
            WireStatus.RetryDisposition retry,
            String detail
    ) {
        error(
                actorId, request.requestId(), request.context(), request.opcode().code(),
                code, retry, detail
        );
    }

    private static WireEditor.MediaType mediaType(MediaType mediaType) {
        return switch (mediaType) {
            case APPLICATION_JSON -> WireEditor.MediaType.JSON;
            case IMAGE_PNG -> WireEditor.MediaType.PNG;
        };
    }

    private void operation(UUID actorId, EditorWireMessage.EditorOperation operation) {
        WireIdentity.EditorContext context = operation.context();
        authorize(actorId, context, MinimapAction.MUTATE_DRAFT);
        java.util.Map<Sha256, byte[]> content = new HashMap<>();
        for (WireEditor.DraftMutation mutation : operation.mutations()) {
            if (mutation instanceof WireEditor.Put put) {
                byte[] bytes = claimUpload(
                        actorId, context, put.completedUploadId(), put.path(), put.newHash()
                );
                content.putIfAbsent(put.newHash(), bytes);
            }
        }
        byte[] descriptor = com.ptcrys.fpsmatch.core.minimap.editor.command
                .EditorCommandHasher.descriptorBytes(wireOperations(operation.mutations()));
        if (!Sha256Digest.of(descriptor).equals(operation.descriptorHash())) {
            throw new DraftException(
                    MinimapErrorCode.HASH_MISMATCH,
                    "Editor operation descriptor hash does not match"
            );
        }
        DraftAck ack = drafts.apply(
                context.draftId(), operation.expectedRootHash(), operation.opSequence(),
                operation.descriptorHash(), descriptor, content
        );
        WireIdentity.EditorContext next = withDraft(context, ack);
        sender.send(actorId, new EditorWireMessage.EditorAck(
                operation.requestId(), next, new WireEditor.OperationAck()
        ));
    }

    private byte[] claimUpload(
            UUID actorId,
            WireIdentity.EditorContext context,
            UUID uploadId,
            ContainerPath path,
            Sha256 hash
    ) {
        UploadOwnerScope scope = owner(actorId, context);
        java.util.Map<UUID, UploadReservation> scoped = activeUploads.get(scope);
        UploadReservation reservation = scoped == null ? null : scoped.get(uploadId);
        if (reservation == null) {
            throw new UploadException(
                    MinimapErrorCode.ENTRY_NOT_FOUND, "Referenced editor upload is unavailable"
            );
        }
        forgetUpload(scope, uploadId);
        try (var completed = uploads.claimCompleted(
                reservation.uploadId(), scope, WireEditor.UploadPurpose.SOURCE_ENTRY,
                Optional.of(path), hash
        )) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(8192);
            while (completed.read(buffer) >= 0) {
                buffer.flip();
                bytes.write(buffer.array(), 0, buffer.remaining());
                buffer.clear();
            }
            return bytes.toByteArray();
        } catch (IOException failure) {
            throw new UploadException(
                    MinimapErrorCode.MAP_UNAVAILABLE, "Unable to read editor upload", failure
            );
        }
    }

    private void upload(UUID actorId, EditorWireMessage.UploadFragment message) {
        WireIdentity.EditorContext context = message.context();
        authorize(actorId, context, MinimapAction.UPLOAD);
        UploadOwnerScope scope = owner(actorId, context);
        if (message.data() instanceof WireEditor.UploadBegin begin) {
            UploadReservation reservation = uploads.begin(scope, begin);
            activeUploads.computeIfAbsent(scope, ignored -> new ConcurrentHashMap<>())
                    .put(reservation.uploadId(), reservation);
            uploadMaps.put(scope, context.binding().target().mapKey());
            uploadAck(actorId, message.requestId(), context,
                    reservation.uploadId(), 0, 0, false, Optional.empty());
        } else if (message.data() instanceof WireEditor.UploadData data) {
            UploadReservation reservation = requireUpload(scope, data.transfer().transferId());
            var fragment = data.transfer();
            if (!reservation.uploadId().equals(fragment.transferId())) {
                throw new UploadException(MinimapErrorCode.SCOPE_MISMATCH, "Upload ID changed");
            }
            UploadProgress progress = uploads.accept(
                    scope, reservation.uploadId(), fragment.fragmentIndex(), fragment.fragmentData()
            );
            uploadAck(actorId, message.requestId(), context, reservation.uploadId(),
                    progress.receivedFragments(), progress.receivedBytes(), false, Optional.empty());
        } else if (message.data() instanceof WireEditor.UploadFinish finish) {
            UploadReservation reservation = requireUpload(scope, finish.uploadId());
            if (!reservation.uploadId().equals(finish.uploadId())) {
                throw new UploadException(MinimapErrorCode.SCOPE_MISMATCH, "Upload ID changed");
            }
            var completed = uploads.finish(scope, reservation.uploadId());
            uploadAck(actorId, message.requestId(), context, reservation.uploadId(),
                    reservation.fragmentCount(), reservation.totalLength(), true,
                    Optional.of(completed.expectedHash()));
        } else if (message.data() instanceof WireEditor.UploadAbort abort) {
            boolean aborted = false;
            try {
                aborted = uploads.abort(scope, abort.uploadId());
            } finally {
                if (aborted || !uploads.tracks(scope, abort.uploadId())) {
                    forgetUpload(scope, abort.uploadId());
                }
            }
            uploadAck(actorId, message.requestId(), context, abort.uploadId(),
                    0, 0, false, Optional.empty());
        }
    }

    private void save(UUID actorId, EditorWireMessage.SaveDraft save) {
        WireIdentity.EditorContext context = save.context();
        authorize(actorId, context, MinimapAction.SAVE_DRAFT);
        DraftState state = drafts.requireRoot(context.draftId(), save.expectedRootHash());
        if (state.ackCursor() != save.expectedAckCursor()) {
            throw new DraftException(
                    MinimapErrorCode.REVISION_CONFLICT, "Draft ACK cursor changed"
            );
        }
        sender.send(actorId, new EditorWireMessage.EditorAck(
                save.requestId(), context, new WireEditor.DraftSaved(false)
        ));
    }

    private void close(UUID actorId, EditorWireMessage.EditorClose close) {
        WireIdentity.EditorContext context = close.context();
        MinimapAction action = close.closeMode() == WireEditor.CloseMode.DISCARD_DRAFT
                ? MinimapAction.DISCARD_DRAFT : MinimapAction.FORCE_CLOSE_SESSION;
        sessions.close(
                actorId, context.sessionId(), context.binding().target().mapKey(),
                context.binding().target().dimension(), context.binding().documentId(),
                context.draftId(), context.baseRevision(), action
        );
        LifecycleFailures.runAll(
                () -> contextAuthority.clearIfMatches(actorId, context),
                () -> closeUploadScopes(scope -> scope.equals(owner(actorId, context))),
                () -> {
                    if (close.closeMode() == WireEditor.CloseMode.DISCARD_DRAFT) {
                        drafts.discard(context.draftId());
                    }
                }
        );
        sender.send(actorId, new EditorWireMessage.EditorAck(
                close.requestId(), context, new WireEditor.Closed(close.closeMode())
        ));
    }

    int pruneStaleUploadScopes() {
        int removed = 0;
        for (var entry : activeUploads.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(UPLOAD_SCOPE_ORDER)).toList()) {
            UploadOwnerScope scope = entry.getKey();
            entry.getValue().keySet().removeIf(uploadId -> !uploads.tracks(scope, uploadId));
            if (entry.getValue().isEmpty()
                    && activeUploads.remove(scope, entry.getValue())) {
                uploadMaps.remove(scope);
                removed++;
            }
        }
        return removed;
    }

    private void closeUploadScopes(Predicate<UploadOwnerScope> selected) {
        List<UploadOwnerScope> scopes = activeUploads.keySet().stream()
                .filter(selected).sorted(UPLOAD_SCOPE_ORDER).toList();
        scopes.forEach(scope -> {
            activeUploads.remove(scope);
            uploadMaps.remove(scope);
        });
        LifecycleFailures.runAll(scopes.stream()
                .map(scope -> (Runnable) () -> uploads.closeScope(scope))
                .toArray(Runnable[]::new));
    }

    private void forgetUpload(UploadOwnerScope scope, UUID uploadId) {
        Map<UUID, UploadReservation> scoped = activeUploads.get(scope);
        if (scoped != null) {
            scoped.remove(uploadId);
            if (scoped.isEmpty() && activeUploads.remove(scope, scoped)) {
                uploadMaps.remove(scope);
            }
        }
    }

    private EditorSession authorize(
            UUID actorId,
            WireIdentity.EditorContext context,
            MinimapAction action
    ) {
        return sessions.authorize(
                actorId, context.sessionId(), context.binding().target().mapKey(),
                context.binding().target().dimension(), context.binding().documentId(),
                context.draftId(), context.baseRevision(), action
        );
    }

    private UploadReservation requireUpload(UploadOwnerScope scope, UUID uploadId) {
        java.util.Map<UUID, UploadReservation> scoped = activeUploads.get(scope);
        UploadReservation reservation = scoped == null ? null : scoped.get(uploadId);
        if (reservation == null) {
            throw new UploadException(MinimapErrorCode.SESSION_NOT_FOUND, "Upload was not found");
        }
        return reservation;
    }

    private void uploadAck(
            UUID actorId,
            UUID requestId,
            WireIdentity.EditorContext context,
            UUID uploadId,
            int fragments,
            long bytes,
            boolean complete,
            Optional<Sha256> hash
    ) {
        sender.send(actorId, new EditorWireMessage.EditorAck(
                requestId, context,
                new WireEditor.UploadAck(uploadId, fragments, bytes, complete, hash)
        ));
    }

    private void error(
            UUID actorId,
            UUID requestId,
            WireIdentity.EditorContext context,
            int opcode,
            MinimapErrorCode code,
            WireStatus.RetryDisposition retry,
            String detail
    ) {
        sender.send(actorId, new PublishWireMessage.ErrorMessage(
                Optional.ofNullable(requestId),
                context == null ? Optional.empty() : Optional.of(context.lease()),
                context == null ? Optional.empty() : Optional.of(context.binding()),
                Optional.of(opcode), new WireStatus.ErrorInfo(code.code(), retry, detail)
        ));
    }

    private void errorFor(
            UUID actorId,
            MinimapWireMessage message,
            MinimapErrorCode code,
            WireStatus.RetryDisposition retry,
            String detail
    ) {
        if (message instanceof EditorWireMessage.EditorResume resume) {
            sender.send(actorId, new PublishWireMessage.ErrorMessage(
                    Optional.of(resume.requestId()), Optional.of(resume.lease()),
                    Optional.of(resume.binding()), Optional.of(resume.opcode().code()),
                    new WireStatus.ErrorInfo(code.code(), retry, detail)
            ));
            return;
        }
        if (message instanceof PublishWireMessage.QueryPublishStatus query) {
            sender.send(actorId, new PublishWireMessage.ErrorMessage(
                    Optional.of(query.requestId()), Optional.of(query.lease()),
                    Optional.of(query.binding()), Optional.of(query.opcode().code()),
                    new WireStatus.ErrorInfo(
                            code.code(), WireStatus.RetryDisposition.DO_NOT_RETRY, detail
                    )
            ));
            return;
        }
        error(
                actorId, requestId(message), context(message), message.opcode().code(),
                code, retry, detail
        );
    }

    private static WireIdentity.EditorContext withDraft(
            WireIdentity.EditorContext context,
            DraftAck ack
    ) {
        return new WireIdentity.EditorContext(
                context.lease(), context.binding(), context.sessionId(), context.draftId(),
                context.baseRevision(), context.baseSourceHash(),
                ack.draftRootHash(), ack.ackCursor()
        );
    }

    private static UploadOwnerScope owner(UUID actorId, WireIdentity.EditorContext context) {
        return new UploadOwnerScope(
                actorId, context.sessionId(), context.draftId(), context.baseRevision()
        );
    }

    private static List<com.ptcrys.fpsmatch.core.minimap.editor.command.EditorOperation>
            wireOperations(java.util.List<WireEditor.DraftMutation> mutations) {
        return mutations.stream().map(mutation -> {
            if (mutation instanceof WireEditor.Put put) {
                var address = com.ptcrys.fpsmatch.core.minimap.format
                        .MinimapContainerLayout.parseSourceTile(put.path()).orElseThrow();
                return (com.ptcrys.fpsmatch.core.minimap.editor.command.EditorOperation)
                        new com.ptcrys.fpsmatch.core.minimap.editor.command
                                .EditorOperation.PutTile(
                                address.floorId(), address.ownerId(), address.x(), address.y(),
                                put.oldHash(), put.newHash()
                        );
            }
            WireEditor.Delete delete = (WireEditor.Delete) mutation;
            var address = com.ptcrys.fpsmatch.core.minimap.format
                    .MinimapContainerLayout.parseSourceTile(delete.path()).orElseThrow();
            return new com.ptcrys.fpsmatch.core.minimap.editor.command
                    .EditorOperation.DeleteTile(
                    address.floorId(), address.ownerId(), address.x(), address.y(),
                    delete.oldHash()
            );
        }).toList();
    }

    private static Authorization authorization(MinimapWireMessage message) {
        if (message instanceof EditorWireMessage.RequestSourceEntries value) {
            return new Authorization(value.context(), MinimapAction.FETCH_SOURCE);
        }
        if (message instanceof EditorWireMessage.EditorOperation value) {
            return new Authorization(value.context(), MinimapAction.MUTATE_DRAFT);
        }
        if (message instanceof EditorWireMessage.UploadFragment value) {
            return new Authorization(value.context(), MinimapAction.UPLOAD);
        }
        if (message instanceof EditorWireMessage.SaveDraft value) {
            return new Authorization(value.context(), MinimapAction.SAVE_DRAFT);
        }
        if (message instanceof EditorWireMessage.EditorClose value) {
            return new Authorization(value.context(),
                    value.closeMode() == WireEditor.CloseMode.DISCARD_DRAFT
                            ? MinimapAction.DISCARD_DRAFT : MinimapAction.FORCE_CLOSE_SESSION);
        }
        if (message instanceof PublishWireMessage.ReservePublish value) {
            return new Authorization(value.context(), MinimapAction.RESERVE_PUBLISH);
        }
        if (message instanceof PublishWireMessage.EditorRebase value) {
            return new Authorization(value.context(), MinimapAction.SAVE_DRAFT);
        }
        return null;
    }

    private static UUID requestId(MinimapWireMessage message) {
        if (message instanceof EditorWireMessage.EditorOpen value) return value.requestId();
        if (message instanceof EditorWireMessage.EditorResume value) return value.requestId();
        if (message instanceof EditorWireMessage.RequestSourceEntries value) {
            return value.requestId();
        }
        if (message instanceof EditorWireMessage.EditorOperation value) return value.requestId();
        if (message instanceof EditorWireMessage.UploadFragment value) return value.requestId();
        if (message instanceof EditorWireMessage.SaveDraft value) return value.requestId();
        if (message instanceof EditorWireMessage.EditorClose value) return value.requestId();
        if (message instanceof PublishWireMessage.ReservePublish value) return value.requestId();
        if (message instanceof PublishWireMessage.QueryPublishStatus value) {
            return value.requestId();
        }
        if (message instanceof PublishWireMessage.EditorRebase value) return value.requestId();
        return null;
    }

    private static WireIdentity.EditorContext context(MinimapWireMessage message) {
        Authorization authorization = authorization(message);
        return authorization == null ? null : authorization.context();
    }

    private static String detail(Throwable failure, String fallback) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? fallback : failure.getMessage();
    }

    private record Authorization(WireIdentity.EditorContext context, MinimapAction action) {
    }

    @FunctionalInterface
    public interface SourceResolver {
        Optional<SourceMap> resolve(
                UUID actorId,
                WireIdentity.EditorContext context
        );
    }
}
