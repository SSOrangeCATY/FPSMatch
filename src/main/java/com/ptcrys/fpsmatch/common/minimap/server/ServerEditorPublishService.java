package com.ptcrys.fpsmatch.common.minimap.server;

import com.ptcrys.fpsmatch.common.capability.map.MinimapCapability;
import com.ptcrys.fpsmatch.core.FPSMCore;
import com.ptcrys.fpsmatch.core.map.BaseMap;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorDocumentMutator;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorEdit;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorOperation;
import com.ptcrys.fpsmatch.core.minimap.editor.document.EditorSourceCodec;
import com.ptcrys.fpsmatch.core.minimap.editor.document.EditorSourceSnapshot;
import com.ptcrys.fpsmatch.core.minimap.editor.document.EditorTileCompositor;
import com.ptcrys.fpsmatch.core.minimap.format.CompiledMapPair;
import com.ptcrys.fpsmatch.core.minimap.format.CanonicalPngCodecV1;
import com.ptcrys.fpsmatch.core.minimap.format.CanonicalZipWriter;
import com.ptcrys.fpsmatch.core.minimap.format.RuntimeCompileRequest;
import com.ptcrys.fpsmatch.core.minimap.format.RuntimeMapCompiler;
import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import com.ptcrys.fpsmatch.core.minimap.format.SourceMap;
import com.ptcrys.fpsmatch.core.minimap.format.SourceMapReader;
import com.ptcrys.fpsmatch.core.minimap.model.CompilerProfile;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.storage.MinimapRepository;
import com.ptcrys.fpsmatch.core.minimap.storage.PublishDescriptor;
import com.ptcrys.fpsmatch.core.minimap.storage.PublishOutcome;
import com.ptcrys.fpsmatch.core.minimap.storage.PublishTransaction;
import com.ptcrys.fpsmatch.core.minimap.wire.PublishWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;
import com.ptcrys.fpsmatch.core.minimap.wire.WireStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/** Publishes only server-materialized editor drafts and exposes them after binding readback. */
public final class ServerEditorPublishService {
    private static final int MAX_TERMINAL_RESULTS = 256;
    private static final Sha256 EMPTY_HASH = Sha256Digest.of(new byte[0]);
    private static final CompilerProfile PROFILE = new CompilerProfile(
            NamespacedId.parse("fpsmatch:editor"), MinimapFormatContract.CURRENT
    );

    private final MinimapRepository repository;
    private final EditorSessionManager sessions;
    private final MinimapPermissionPolicy permissions;
    private final DraftStore drafts;
    private final MinimapBindingCoordinator bindings;
    private final BiConsumer<UUID, PublishWireMessage> sender;
    private final Consumer<MapKey> invalidator;
    private final PublishAttemptObserver attemptObserver;
    private final Function<PublishTransaction, PublishOutcome> committer;
    private final LinkedHashMap<TerminalKey, PublishWireMessage.PublishResult>
            terminalResults = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(
                Map.Entry<TerminalKey, PublishWireMessage.PublishResult> eldest
        ) {
            return size() > MAX_TERMINAL_RESULTS;
        }
    };

    public ServerEditorPublishService(
            MinimapRepository repository,
            EditorSessionManager sessions,
            MinimapPermissionPolicy permissions,
            BiConsumer<UUID, PublishWireMessage> sender
    ) {
        this(
                repository,
                sessions,
                permissions,
                null,
                new MinimapBindingCoordinator(new CapabilityBindingStore()),
                sender,
                ignored -> {
                },
                PublishAttemptObserver.noOp()
        );
    }

    public ServerEditorPublishService(
            MinimapRepository repository,
            EditorSessionManager sessions,
            MinimapPermissionPolicy permissions,
            DraftStore drafts,
            MinimapBindingCoordinator bindings,
            BiConsumer<UUID, PublishWireMessage> sender,
            Consumer<MapKey> invalidator
    ) {
        this(
                repository, sessions, permissions, drafts, bindings, sender, invalidator,
                PublishAttemptObserver.noOp()
        );
    }

    public ServerEditorPublishService(
            MinimapRepository repository,
            EditorSessionManager sessions,
            MinimapPermissionPolicy permissions,
            DraftStore drafts,
            MinimapBindingCoordinator bindings,
            BiConsumer<UUID, PublishWireMessage> sender,
            Consumer<MapKey> invalidator,
            PublishAttemptObserver attemptObserver
    ) {
        this(
                repository, sessions, permissions, drafts, bindings, sender, invalidator,
                attemptObserver, repository::commit
        );
    }

    ServerEditorPublishService(
            MinimapRepository repository,
            EditorSessionManager sessions,
            MinimapPermissionPolicy permissions,
            DraftStore drafts,
            MinimapBindingCoordinator bindings,
            BiConsumer<UUID, PublishWireMessage> sender,
            Consumer<MapKey> invalidator,
            PublishAttemptObserver attemptObserver,
            Function<PublishTransaction, PublishOutcome> committer
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.drafts = drafts;
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.sender = Objects.requireNonNull(sender, "sender");
        this.invalidator = Objects.requireNonNull(invalidator, "invalidator");
        this.attemptObserver = Objects.requireNonNull(attemptObserver, "attemptObserver");
        this.committer = Objects.requireNonNull(committer, "committer");
    }

    /** @deprecated Empty publications are intentionally rejected; use {@link #publish}. */
    @Deprecated
    public void publishEmpty(UUID actorId, PublishWireMessage.ReservePublish request) {
        publish(actorId, request);
    }

    public void publish(UUID actorId, PublishWireMessage.ReservePublish request) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(request, "request");
        WireIdentity.EditorContext context = request.context();
        MapKey mapKey = context.binding().target().mapKey();
        NamespacedId dimension = context.binding().target().dimension();
        NamespacedId documentId = context.binding().documentId();
        String token = "error";
        long revision = 0;
        PublishAttemptReceipt receipt;
        PublishTransaction prepared;
        CompiledPair pair;
        MinimapCapability.Binding previous;
        try {
            if (drafts == null) {
                throw new SessionAccessException(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "Authoritative draft storage is unavailable"
                );
            }
            requirePermission(actorId, mapKey, MinimapAction.RESERVE_PUBLISH);
            requirePermission(actorId, mapKey, MinimapAction.COMMIT_PUBLISH);
            EditorSession session = sessions.authorize(
                    actorId, context.sessionId(), mapKey, dimension, documentId,
                    context.draftId(), context.baseRevision(), MinimapAction.COMMIT_PUBLISH
            );
            previous = bindings.preflight(
                    mapKey, dimension, documentId, session.baseRevision()
            );
            DraftMaterialization materialized = drafts.requireMaterialization(
                    context.draftId(), context.draftRootHash()
            );
            EditorSourceSnapshot source = materializeSource(
                    mapKey, dimension, documentId, materialized, revisionSource(session, previous)
            );
            if (!EditorSourceCodec.hasVisiblePixels(source)) {
                throw new SessionAccessException(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "Published minimap must contain visible content"
                );
            }

            PublishTransaction reserved = repository.reserve(
                    mapKey, dimension, documentId, session.baseRevision()
            );
            token = reserved.descriptor().publishToken();
            revision = reserved.descriptor().publishRevision();
            receipt = PublishAttemptReceipt.reserved(actorId, request, reserved);
            requireReceiptRecorded(receipt);
            pair = compile(source, revision);
            WireStatus.HashTriple hashes = new WireStatus.HashTriple(
                    pair.sourceHash(), pair.runtimeHash(), pair.runtimeContainerHash()
            );
            PublishDescriptor expected = new PublishDescriptor(
                    reserved.publishToken(), reserved.baseRevision(), reserved.publishRevision(),
                    reserved.descriptor().expiresAtEpochMillis(), pair.sourceHash(),
                    pair.runtimeHash(), pair.runtimeContainerHash()
            );
            receipt = receipt.expectedCaptured(expected, hashes);
            requireReceiptRecorded(receipt);
            prepared = repository.prepare(reserved, pair.sourceBytes(), pair.runtimeBytes());
            receipt = receipt.prepared(prepared);
            requireReceiptRecorded(receipt);
            receipt = receipt.commitAttempted();
            // This acknowledgement is the durable recovery gate. Commit must not run without it.
            requireReceiptRecorded(receipt);
        } catch (SessionAccessException failure) {
            sendFailure(actorId, request, token, revision, failure.errorCode(),
                    message(failure, "publish denied"));
            return;
        } catch (DraftException failure) {
            sendFailure(actorId, request, token, revision, failure.errorCode(),
                    message(failure, "draft materialization failed"));
            return;
        } catch (RuntimeException failure) {
            sendFailure(actorId, request, token, revision, MinimapErrorCode.INTERNAL_ERROR,
                    message(failure, "publish failed"));
            return;
        }

        PublishOutcome outcome;
        try {
            outcome = committer.apply(prepared);
        } catch (RuntimeException failure) {
            reportStatusUnknown(actorId, request, receipt);
            return;
        }
        if (outcome == null || !outcome.committed()) {
            reportStatusUnknown(actorId, request, receipt);
            return;
        }

        MinimapCapability.Binding next = new MinimapCapability.Binding(
                dimension, documentId, revision, pair.sourceHash(), pair.runtimeHash()
        );
        try {
            if (!bindings.bindCommitted(mapKey, previous, next)) {
                reportStatusUnknown(actorId, request, receipt);
                return;
            }
        } catch (RuntimeException failure) {
            reportStatusUnknown(actorId, request, receipt);
            return;
        }

        PublishWireMessage.PublishResult committedResult = new PublishWireMessage.PublishResult(
                request.requestId(), context.lease(), context.binding(), token, revision,
                WireStatus.PublishOutcome.COMMITTED,
                Optional.of(new WireStatus.HashTriple(
                        pair.sourceHash(), pair.runtimeHash(), pair.runtimeContainerHash()
                )), Optional.empty()
        );
        if (!recordResult(receipt, committedResult)) {
            return;
        }
        rememberTerminal(actorId, committedResult);
        notifyCommitted(actorId, mapKey, committedResult);
    }

    /** Queries only bounded terminal process state; an active editor session is not required. */
    public void query(UUID actorId, PublishWireMessage.QueryPublishStatus request) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(request, "request");
        requirePermission(
                actorId, request.binding().target().mapKey(),
                MinimapAction.QUERY_PUBLISH_STATUS
        );
        PublishWireMessage.PublishResult result;
        synchronized (terminalResults) {
            result = terminalResults.get(TerminalKey.from(actorId, request));
        }
        sender.accept(actorId, result != null
                && result.outcome() == WireStatus.PublishOutcome.COMMITTED
                ? committedStatus(request, result) : unknownStatus(request));
    }

    public void clearActor(UUID actorId) {
        Objects.requireNonNull(actorId, "actorId");
        synchronized (terminalResults) {
            terminalResults.keySet().removeIf(key -> key.actorId().equals(actorId));
        }
    }

    public int recoverBindings() {
        return bindings.recoverPending(invalidator);
    }

    private void requireReceiptRecorded(PublishAttemptReceipt receipt) {
        if (!attemptObserver.record(receipt)) {
            throw new IllegalStateException("Publish attempt receipt was not recorded");
        }
    }

    private boolean recordResult(
            PublishAttemptReceipt receipt,
            PublishWireMessage.PublishResult result
    ) {
        PublishAttemptReceipt ready = receipt.resultReady(result);
        if (!attemptObserver.record(ready)) {
            return false;
        }
        return attemptObserver.record(ready.resultRecorded());
    }

    private void reportStatusUnknown(
            UUID actorId,
            PublishWireMessage.ReservePublish request,
            PublishAttemptReceipt receipt
    ) {
        PublishWireMessage.PublishResult unknown = new PublishWireMessage.PublishResult(
                request.requestId(), request.context().lease(), request.context().binding(),
                receipt.reservedTransaction().publishToken(),
                receipt.reservedTransaction().publishRevision(),
                WireStatus.PublishOutcome.STATUS_UNKNOWN,
                Optional.empty(), Optional.of(new WireStatus.ErrorInfo(
                        MinimapErrorCode.INTERNAL_ERROR.code(),
                        WireStatus.RetryDisposition.QUERY_PUBLISH_STATUS,
                        "Publish status requires repository recovery"
                ))
        );
        if (recordResult(receipt, unknown)) {
            rememberTerminal(actorId, unknown);
            sender.accept(actorId, unknown);
        }
    }

    private void rememberTerminal(
            UUID actorId,
            PublishWireMessage.PublishResult result
    ) {
        if (result.outcome() != WireStatus.PublishOutcome.COMMITTED
                && result.outcome() != WireStatus.PublishOutcome.STATUS_UNKNOWN) {
            return;
        }
        synchronized (terminalResults) {
            terminalResults.put(TerminalKey.from(actorId, result), result);
        }
    }

    private static PublishWireMessage.PublishStatus committedStatus(
            PublishWireMessage.QueryPublishStatus request,
            PublishWireMessage.PublishResult result
    ) {
        return new PublishWireMessage.PublishStatus(
                request.requestId(), request.lease(), request.binding(),
                request.publishToken(), request.publishRevision(),
                WireStatus.PublishState.COMMITTED,
                Optional.of(result.publishRevision()), result.hashes(), Optional.empty()
        );
    }

    private static PublishWireMessage.PublishStatus unknownStatus(
            PublishWireMessage.QueryPublishStatus request
    ) {
        return new PublishWireMessage.PublishStatus(
                request.requestId(), request.lease(), request.binding(),
                request.publishToken(), request.publishRevision(),
                WireStatus.PublishState.STATUS_UNKNOWN,
                Optional.empty(), Optional.empty(), Optional.of(new WireStatus.ErrorInfo(
                        MinimapErrorCode.INTERNAL_ERROR.code(),
                        WireStatus.RetryDisposition.DO_NOT_RETRY,
                        "Publish status is unavailable"
                ))
        );
    }

    /** Keeps the irreversible committed outcome authoritative across notification failures. */
    private void notifyCommitted(
            UUID actorId,
            MapKey mapKey,
            PublishWireMessage.PublishResult result
    ) {
        Throwable failure = null;
        try {
            invalidator.accept(mapKey);
        } catch (RuntimeException ignored) {
            // The commit and terminal query state are already authoritative.
        } catch (Error next) {
            failure = next;
        }
        try {
            sender.accept(actorId, result);
        } catch (RuntimeException ignored) {
            // A transport notification failure must not turn a committed publish into an error.
        } catch (Error next) {
            if (failure == null) {
                failure = next;
            } else if (failure != next) {
                failure.addSuppressed(next);
            }
        }
        if (failure instanceof RuntimeException next) {
            throw next;
        }
        if (failure instanceof Error next) {
            throw next;
        }
    }

    private static EditorSourceSnapshot materializeSource(
            MapKey mapKey,
            NamespacedId dimension,
            NamespacedId documentId,
            DraftMaterialization materialized,
            EditorSourceSnapshot source
    ) {
        var state = materialized.state();
        if (!state.mapKey().equals(mapKey)
                || !state.dimension().equals(dimension)
                || !state.documentId().equals(documentId)) {
            throw new SessionAccessException(
                    MinimapErrorCode.SCOPE_MISMATCH,
                    "Draft materialization scope changed"
            );
        }
        EditorDocumentMutator mutator = new EditorDocumentMutator();
        try (SourceMap authoritative = SourceMapReader.read(source.originalSourceBytes())) {
            for (DraftMaterialization.Operation operation : materialized.operations()) {
                List<EditorOperation> forward = DraftDescriptorParser.parse(
                        operation.descriptorBytes()
                );
                List<EditorOperation> inverse = inverse(source, forward);
                mutator.apply(source.document(), new EditorEdit(
                        forward,
                        inverse,
                        payloadsFor(authoritative, inverse, materialized.referencedContent())
                ));
            }
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Unable to close authoritative source", failure);
        }
        return source;
    }

    /**
     * Draft uploads contain only forward PutTile payloads. Undo validation also needs
     * the bytes for inverse PutTile operations, which are authoritative in the base
     * source. Add only the missing tile payloads required by this edit.
     */
    private static Map<Sha256, byte[]> payloadsFor(
            SourceMap source,
            List<EditorOperation> inverse,
            Map<Sha256, byte[]> draftPayloads
    ) {
        Map<Sha256, byte[]> payloads = new LinkedHashMap<>();
        draftPayloads.forEach((hash, bytes) -> payloads.put(hash, bytes.clone()));
        for (EditorOperation operation : inverse) {
            if (!(operation instanceof EditorOperation.PutTile put)) {
                continue;
            }
            Sha256 expectedHash = put.newHash();
            if (payloads.containsKey(expectedHash)) {
                continue;
            }
            ContainerPath path = ContainerPath.parse(put.path());
            if (!source.paths().contains(path)) {
                throw new DraftException(
                        MinimapErrorCode.ENTRY_NOT_FOUND,
                        "Authoritative tile payload is unavailable: " + path
                );
            }
            byte[] authoritative = source.entryBytes(path);
            if (!Sha256Digest.of(authoritative).equals(expectedHash)) {
                throw new DraftException(
                        MinimapErrorCode.HASH_MISMATCH,
                        "Authoritative tile payload hash does not match: " + path
                );
            }
            payloads.put(expectedHash, authoritative.clone());
        }
        // Keep the merge deterministic and reject a malformed draft map before the
        // editor mutator can observe it. DraftStore normally performs this check too.
        for (Map.Entry<Sha256, byte[]> entry : payloads.entrySet()) {
            if (!Sha256Digest.of(entry.getValue()).equals(entry.getKey())) {
                throw new DraftException(
                        MinimapErrorCode.HASH_MISMATCH,
                        "Editor payload hash does not match its bytes"
                );
            }
        }
        return payloads;
    }

    private EditorSourceSnapshot revisionSource(
            EditorSession session,
            MinimapCapability.Binding previous
    ) {
        if (session.baseRevision() == 0) {
            return EditorSourceCodec.createEmpty(
                    session.mapKey(), session.dimension(), session.documentId(), 0,
                    new com.ptcrys.fpsmatch.core.minimap.model.CanvasBounds(512, 512),
                    128, "ground"
            );
        }
        if (previous == null || previous.revision() != session.baseRevision()) {
            throw new SessionAccessException(
                    MinimapErrorCode.REVISION_CONFLICT,
                    "Published minimap binding changed"
            );
        }
        java.nio.file.Path sourcePath = repository.mapDirectory(session.mapKey())
                .resolve("revisions")
                .resolve(Long.toString(session.baseRevision()))
                .resolve("source.fpsmap");
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(sourcePath);
            if (!Sha256Digest.of(bytes).equals(previous.sourceHash())) {
                throw new SessionAccessException(
                        MinimapErrorCode.REVISION_CONFLICT,
                        "Published minimap source changed"
                );
            }
            return EditorSourceCodec.decode(bytes);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Unable to read published minimap source", failure);
        }
    }

    private static List<EditorOperation> inverse(
            EditorSourceSnapshot source,
            List<EditorOperation> forward
    ) {
        List<EditorOperation> inverse = new ArrayList<>();
        for (int index = forward.size() - 1; index >= 0; index--) {
            EditorOperation operation = forward.get(index);
            if (operation instanceof EditorOperation.SetOpacity value) {
                inverse.add(new EditorOperation.SetOpacity(
                        value.floorId(), value.layerId(),
                        source.document().layer(value.floorId(), value.layerId()).opacity()
                ));
            } else if (operation instanceof EditorOperation.SetVisibility value) {
                inverse.add(new EditorOperation.SetVisibility(
                        value.floorId(), value.layerId(),
                        source.document().layer(value.floorId(), value.layerId()).visible()
                ));
            } else if (operation instanceof EditorOperation.SetLocked value) {
                inverse.add(new EditorOperation.SetLocked(
                        value.floorId(), value.layerId(),
                        source.document().layer(value.floorId(), value.layerId()).locked()
                ));
            } else if (operation instanceof EditorOperation.PutTile value) {
                inverse.add(value.oldHash().<EditorOperation>map(old -> new EditorOperation.PutTile(
                        value.floorId(), value.layerId(), value.tileX(), value.tileY(),
                        Optional.of(value.newHash()), old
                )).orElseGet(() -> new EditorOperation.DeleteTile(
                        value.floorId(), value.layerId(), value.tileX(), value.tileY(),
                        value.newHash()
                )));
            } else if (operation instanceof EditorOperation.DeleteTile value) {
                inverse.add(new EditorOperation.PutTile(
                        value.floorId(), value.layerId(), value.tileX(), value.tileY(),
                        Optional.empty(), value.oldHash()
                ));
            }
        }
        return List.copyOf(inverse);
    }

    private static CompiledPair compile(EditorSourceSnapshot source, long revision) {
        byte[] sourceBytes = EditorSourceCodec.encode(source, revision);
        try (SourceMap sourceMap = SourceMapReader.read(sourceBytes)) {
            List<CanonicalZipWriter.EntrySource> tiles = runtimeTiles(source);
            CompiledMapPair compiled = RuntimeMapCompiler.compile(
                    sourceMap,
                    RuntimeCompileRequest.forSource(sourceMap.manifest(), revision, PROFILE, tiles)
            );
            return new CompiledPair(
                    sourceBytes, compiled.runtimeBytes(), compiled.sourceHash(),
                    compiled.runtimeHash(), compiled.runtimeContainerHash()
            );
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Unable to close source compilation input", failure);
        }
    }

    private static List<CanonicalZipWriter.EntrySource> runtimeTiles(
            EditorSourceSnapshot source
    ) {
        int tileEdge = source.document().tileEdge();
        int tilesX = Math.max(1, (source.document().canvas().width() + tileEdge - 1) / tileEdge);
        int tilesY = Math.max(1, (source.document().canvas().height() + tileEdge - 1) / tileEdge);
        List<CanonicalZipWriter.EntrySource> runtime = new ArrayList<>();
        for (String floorId : source.document().floorIds()) {
            for (int tileY = 0; tileY < tilesY; tileY++) {
                for (int tileX = 0; tileX < tilesX; tileX++) {
                    EditorTileCompositor.CompositedTile tile =
                            EditorTileCompositor.composite(
                                    source.document(), floorId, tileX, tileY
                            );
                    runtime.add(new CanonicalZipWriter.Entry(
                            ContainerPath.parse("floors/" + floorId + "/tiles/0/"
                                    + tileX + "_" + tileY + ".png"),
                            CanonicalPngCodecV1.encode(
                                    tile.width(), tile.height(), tile.rgba()
                            )
                    ));
                }
            }
        }
        return List.copyOf(runtime);
    }

    private void requirePermission(UUID actorId, MapKey mapKey, MinimapAction action) {
        boolean allowed;
        try {
            allowed = permissions.mayPerform(actorId, mapKey, action).orElse(false);
        } catch (RuntimeException failure) {
            allowed = false;
        }
        if (!allowed) {
            throw new SessionAccessException(
                    MinimapErrorCode.UNAUTHORIZED, "Minimap editor publish action denied"
            );
        }
    }

    private void sendFailure(
            UUID actorId,
            PublishWireMessage.ReservePublish request,
            String token,
            long revision,
            MinimapErrorCode code,
            String detail
    ) {
        sender.accept(actorId, new PublishWireMessage.PublishResult(
                request.requestId(), request.context().lease(), request.context().binding(),
                token == null || token.isBlank() ? "error" : token,
                Math.max(0, revision), WireStatus.PublishOutcome.ABORTED,
                Optional.empty(), Optional.of(new WireStatus.ErrorInfo(
                        code.code(), WireStatus.RetryDisposition.DO_NOT_RETRY, detail
                ))
        ));
    }

    private static String message(Throwable failure, String fallback) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? fallback : failure.getMessage();
    }

    public static Sha256 emptyHash() {
        return EMPTY_HASH;
    }

    private record CompiledPair(
            byte[] sourceBytes,
            byte[] runtimeBytes,
            Sha256 sourceHash,
            Sha256 runtimeHash,
            Sha256 runtimeContainerHash
    ) {
    }

    private record TerminalKey(
            UUID actorId,
            WireIdentity.ScopeLease lease,
            WireIdentity.DocumentBinding binding,
            String publishToken,
            long publishRevision
    ) {
        private static TerminalKey from(
                UUID actorId,
                PublishWireMessage.QueryPublishStatus request
        ) {
            return new TerminalKey(
                    actorId, request.lease(), request.binding(),
                    request.publishToken(), request.publishRevision()
            );
        }

        private static TerminalKey from(
                UUID actorId,
                PublishWireMessage.PublishResult result
        ) {
            return new TerminalKey(
                    actorId, result.lease(), result.binding(),
                    result.publishToken(), result.publishRevision()
            );
        }
    }

    public static final class CapabilityBindingStore
            implements MinimapBindingCoordinator.BindingStore {
        @Override
        public Optional<MinimapCapability.Binding> read(MapKey mapKey) {
            return map(mapKey).flatMap(baseMap -> baseMap.getCapabilityMap()
                    .get(MinimapCapability.class)).flatMap(MinimapCapability::binding);
        }

        @Override
        public void write(MapKey mapKey, MinimapCapability.Binding binding) {
            BaseMap map = map(mapKey).orElseThrow(
                    () -> new IllegalStateException("Minimap map is unavailable")
            );
            if (map.getCapabilityMap().get(MinimapCapability.class).isEmpty()
                    && !map.getCapabilityMap().add(MinimapCapability.class)) {
                throw new IllegalStateException("Unable to mount minimap capability");
            }
            map.getCapabilityMap().write(MinimapCapability.class, binding);
        }

        @Override
        public void clear(MapKey mapKey) {
            map(mapKey).flatMap(baseMap -> baseMap.getCapabilityMap()
                    .get(MinimapCapability.class)).ifPresent(MinimapCapability::clearBinding);
        }

        @Override
        public MinimapCapability.BindingClearResult compareAndClear(
                MapKey mapKey,
                MinimapCapability.Binding expected
        ) {
            Optional<BaseMap> map = map(mapKey);
            if (map.isEmpty()) {
                return MinimapCapability.BindingClearResult.UNAVAILABLE;
            }
            return map.orElseThrow().getCapabilityMap()
                    .get(MinimapCapability.class)
                    .map(capability -> capability.compareAndClearBinding(expected))
                    .orElse(MinimapCapability.BindingClearResult.ALREADY_ABSENT);
        }

        private static Optional<BaseMap> map(MapKey mapKey) {
            if (!FPSMCore.initialized()) {
                return Optional.empty();
            }
            return FPSMCore.getInstance().getMapByTypeWithName(
                    mapKey.gameType(), mapKey.mapName()
            );
        }
    }
}
