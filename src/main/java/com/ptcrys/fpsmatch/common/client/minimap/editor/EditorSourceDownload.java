package com.ptcrys.fpsmatch.common.client.minimap.editor;

import com.ptcrys.fpsmatch.core.minimap.codec.MinimapModelCodecs;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorCommandException;
import com.ptcrys.fpsmatch.core.minimap.editor.document.EditorSourceCodec;
import com.ptcrys.fpsmatch.core.minimap.editor.document.EditorSourceSnapshot;
import com.ptcrys.fpsmatch.core.minimap.format.CanonicalModelJson;
import com.ptcrys.fpsmatch.core.minimap.format.CanonicalZipWriter;
import com.ptcrys.fpsmatch.core.minimap.format.ContainerLimits;
import com.ptcrys.fpsmatch.core.minimap.format.MinimapContainerLayout;
import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.MediaType;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.model.SourceEntryDescriptor;
import com.ptcrys.fpsmatch.core.minimap.model.SourceManifest;
import com.ptcrys.fpsmatch.core.minimap.wire.EditorWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireEditor;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;
import com.ptcrys.fpsmatch.core.minimap.wire.WireTransfer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Strict manifest-to-canonical-source download state machine. */
final class EditorSourceDownload {
    private final WireIdentity.EditorContext context;
    private final Sha256 sourceHash;
    private final Consumer<MinimapWireMessage> sender;
    private final Supplier<UUID> requestIds;
    private final Consumer<UUID> requestStarted;
    private final Consumer<UUID> requestFinished;
    private final Consumer<EditorSourceSnapshot> completion;
    private final Set<UUID> activeRequests = new LinkedHashSet<>();
    private final Map<UUID, Set<ContainerPath>> requested = new LinkedHashMap<>();
    private final Map<UUID, EditorSourceTransferAccumulator> transfers = new LinkedHashMap<>();
    private final Map<ContainerPath, SourceEntryDescriptor> expected = new LinkedHashMap<>();
    private final Map<ContainerPath, byte[]> entries = new LinkedHashMap<>();
    private UUID manifestRequestId;
    private boolean manifestAccepted;
    private boolean complete;
    private boolean cancelled;

    EditorSourceDownload(
            WireIdentity.EditorContext context,
            Sha256 sourceHash,
            Consumer<MinimapWireMessage> sender,
            Supplier<UUID> requestIds,
            Consumer<UUID> requestStarted,
            Consumer<UUID> requestFinished,
            Consumer<EditorSourceSnapshot> completion
    ) {
        this.context = Objects.requireNonNull(context, "context");
        this.sourceHash = Objects.requireNonNull(sourceHash, "sourceHash");
        this.sender = Objects.requireNonNull(sender, "sender");
        this.requestIds = Objects.requireNonNull(requestIds, "requestIds");
        this.requestStarted = Objects.requireNonNull(requestStarted, "requestStarted");
        this.requestFinished = Objects.requireNonNull(requestFinished, "requestFinished");
        this.completion = Objects.requireNonNull(completion, "completion");
    }

    void start() {
        if (manifestRequestId != null) {
            throw new EditorCommandException("Editor source download already started");
        }
        manifestRequestId = requestIds.get();
        register(manifestRequestId);
        try {
            sender.accept(new EditorWireMessage.RequestSourceEntries(
                    manifestRequestId, context, sourceHash, List.of()
            ));
        } catch (RuntimeException failure) {
            cancel();
            throw failure;
        }
    }

    boolean owns(UUID requestId) {
        return !cancelled && activeRequests.contains(requestId);
    }

    Set<UUID> requestIds() {
        return Set.copyOf(activeRequests);
    }

    void cancel() {
        if (cancelled) return;
        cancelled = true;
        List.copyOf(activeRequests).forEach(this::finish);
        manifestRequestId = null;
        requested.clear();
        transfers.clear();
    }

    void accept(EditorWireMessage.SourceManifest message) {
        requireActive(message.requestId(), message.context(), message.sourceHash());
        if (!message.requestId().equals(manifestRequestId) || manifestAccepted) {
            throw new EditorCommandException("Unexpected editor source manifest");
        }
        byte[] bytes = acceptObject(message.requestId(), true, message.transfer());
        if (bytes == null) return;
        finish(message.requestId());
        manifestRequestId = null;
        if (!message.manifestHash().equals(Sha256Digest.of(bytes))) {
            throw new EditorCommandException("Editor source manifest hash mismatch");
        }
        SourceManifest manifest = CanonicalModelJson.decode(
                bytes, MinimapModelCodecs.SOURCE_MANIFEST
        ).value();
        requireManifestIdentity(manifest);
        entries.put(MinimapContainerLayout.SOURCE_MANIFEST, bytes);
        for (SourceEntryDescriptor descriptor : manifest.entries()) {
            if (expected.putIfAbsent(descriptor.path(), descriptor) != null
                    || descriptor.path().equals(MinimapContainerLayout.SOURCE_MANIFEST)) {
                throw new EditorCommandException("Editor source manifest path is duplicated");
            }
        }
        manifestAccepted = true;
        dispatchEntryRequests();
        if (!cancelled) completeIfReady();
    }

    void accept(EditorWireMessage.SourceFragment message) {
        requireActive(message.requestId(), message.context(), message.sourceHash());
        Set<ContainerPath> batch = requested.get(message.requestId());
        SourceEntryDescriptor descriptor = expected.get(message.path());
        if (!manifestAccepted || batch == null || !batch.contains(message.path())
                || descriptor == null || entries.containsKey(message.path())) {
            throw new EditorCommandException("Editor source fragment was not requested");
        }
        if (!descriptor.sha256().equals(message.transfer().objectHash())
                || mediaType(descriptor.mediaType()) != message.mediaType()) {
            throw new EditorCommandException("Editor source fragment identity mismatch");
        }
        byte[] bytes = acceptObject(message.requestId(), false, message.transfer());
        if (bytes == null) return;
        if (bytes.length != descriptor.byteLength()
                || !descriptor.sha256().equals(Sha256Digest.of(bytes))) {
            throw new EditorCommandException("Editor source entry hash or length mismatch");
        }
        entries.put(message.path(), bytes);
        batch.remove(message.path());
        if (batch.isEmpty()) {
            requested.remove(message.requestId());
            finish(message.requestId());
        }
        completeIfReady();
    }

    private void dispatchEntryRequests() {
        List<WireTransfer.EntryRequest> all = expected.values().stream()
                .map(entry -> new WireTransfer.EntryRequest(entry.path(), entry.sha256()))
                .toList();
        int maximum = com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits
                .MAX_ENTRY_REQUESTS;
        for (int start = 0; start < all.size(); start += maximum) {
            if (cancelled) return;
            List<WireTransfer.EntryRequest> batch = List.copyOf(
                    all.subList(start, Math.min(start + maximum, all.size()))
            );
            UUID requestId = requestIds.get();
            requested.put(requestId, new LinkedHashSet<>(
                    batch.stream().map(WireTransfer.EntryRequest::path).toList()
            ));
            register(requestId);
            try {
                sender.accept(new EditorWireMessage.RequestSourceEntries(
                        requestId, context, sourceHash, batch
                ));
            } catch (RuntimeException failure) {
                cancel();
                throw failure;
            }
        }
    }

    private byte[] acceptObject(
            UUID requestId, boolean manifest, WireTransfer.TransferFragment fragment
    ) {
        EditorSourceTransferAccumulator accumulator = transfers.computeIfAbsent(
                fragment.transferId(), ignored -> new EditorSourceTransferAccumulator(
                        requestId, manifest, fragment
                )
        );
        if (!accumulator.accept(requestId, manifest, fragment)) return null;
        transfers.remove(fragment.transferId());
        return accumulator.bytes();
    }

    private void completeIfReady() {
        if (complete || cancelled || !manifestAccepted
                || entries.size() != expected.size() + 1) return;
        byte[] archive = CanonicalZipWriter.write(entries, ContainerLimits.sourceHardLimits());
        if (!sourceHash.equals(Sha256Digest.of(archive))) {
            throw new EditorCommandException("Editor canonical source hash mismatch");
        }
        EditorSourceSnapshot snapshot = EditorSourceCodec.decode(archive);
        SourceManifest manifest = snapshot.definition().manifest();
        requireManifestIdentity(manifest);
        complete = true;
        completion.accept(snapshot);
    }

    private void requireActive(
            UUID requestId, WireIdentity.EditorContext actual, Sha256 actualSourceHash
    ) {
        if (complete || !owns(requestId) || !context.equals(actual)
                || !sourceHash.equals(actualSourceHash)) {
            throw new EditorCommandException("Editor source response identity changed");
        }
    }

    private void register(UUID requestId) {
        if (cancelled || !activeRequests.add(requestId)) {
            throw new EditorCommandException("Editor source request identity was reused");
        }
        requestStarted.accept(requestId);
    }

    private void finish(UUID requestId) {
        if (activeRequests.remove(requestId)) {
            requestFinished.accept(requestId);
        }
    }

    private void requireManifestIdentity(SourceManifest manifest) {
        if (!manifest.documentId().equals(context.binding().documentId())
                || !manifest.binding().equals(context.binding().target().mapKey())
                || manifest.revision() != context.baseRevision()
                || !manifest.dimension().equals(context.binding().target().dimension())) {
            throw new EditorCommandException("Editor source manifest binding changed");
        }
    }

    private static WireEditor.MediaType mediaType(MediaType type) {
        return switch (type) {
            case APPLICATION_JSON -> WireEditor.MediaType.JSON;
            case IMAGE_PNG -> WireEditor.MediaType.PNG;
        };
    }
}
