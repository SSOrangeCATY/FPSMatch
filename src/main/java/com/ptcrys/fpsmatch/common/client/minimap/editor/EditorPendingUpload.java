package com.ptcrys.fpsmatch.common.client.minimap.editor;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorCommandException;
import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.wire.EditorWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireEditor;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;
import com.ptcrys.fpsmatch.core.minimap.wire.WireTransfer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Owns one tile upload's request identities and ACK state. */
final class EditorPendingUpload {
    private final UUID beginRequest;
    private final long sequence;
    private final ContainerPath path;
    private final Sha256 hash;
    private final byte[] payload;
    private final Set<UUID> dataRequests = new HashSet<>();
    private UUID uploadId;
    private UUID finishRequest;
    private boolean beginPending = true;
    private boolean dispatched;
    private boolean complete;

    EditorPendingUpload(
            UUID beginRequest,
            long sequence,
            ContainerPath path,
            byte[] payload
    ) {
        this.beginRequest = Objects.requireNonNull(beginRequest, "beginRequest");
        this.sequence = sequence;
        this.path = Objects.requireNonNull(path, "path");
        this.payload = Objects.requireNonNull(payload, "payload").clone();
        if (payload.length == 0 || payload.length > MinimapHardLimits.MAX_SOURCE_ENTRY_UPLOAD_BYTES) {
            throw new EditorCommandException("Tile payload exceeds the source-entry upload limit");
        }
        this.hash = Sha256Digest.of(payload);
    }

    Outbound begin(WireIdentity.EditorContext context) {
        int fragmentCount = fragmentCount();
        return new Outbound(
                beginRequest,
                EditorRequestKind.UPLOAD_BEGIN,
                new EditorWireMessage.UploadFragment(
                        beginRequest,
                        context,
                        new WireEditor.UploadBegin(
                                WireEditor.UploadPurpose.SOURCE_ENTRY,
                                Optional.of(path),
                                payload.length,
                                fragmentCount,
                                hash
                        )
                )
        );
    }

    List<Outbound> dispatch(
            WireIdentity.EditorContext context,
            Supplier<UUID> requestIds
    ) {
        UUID assignedUploadId = Objects.requireNonNull(uploadId, "uploadId");
        if (dispatched) return List.of();
        dispatched = true;
        int count = fragmentCount();
        List<Outbound> outbound = new ArrayList<>(count + 1);
        for (int index = 0; index < count; index++) {
            int offset = index * MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES;
            int length = Math.min(
                    MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES,
                    payload.length - offset
            );
            byte[] fragment = Arrays.copyOfRange(payload, offset, offset + length);
            UUID dataRequest = requestIds.get();
            dataRequests.add(dataRequest);
            outbound.add(new Outbound(
                    dataRequest,
                    EditorRequestKind.UPLOAD_DATA,
                    new EditorWireMessage.UploadFragment(
                            dataRequest,
                            context,
                            new WireEditor.UploadData(new WireTransfer.TransferFragment(
                                    assignedUploadId,
                                    index,
                                    count,
                                    payload.length,
                                    hash,
                                    Sha256Digest.of(fragment),
                                    fragment
                            ))
                    )
            ));
        }
        finishRequest = requestIds.get();
        outbound.add(new Outbound(
                finishRequest,
                EditorRequestKind.UPLOAD_FINISH,
                new EditorWireMessage.UploadFragment(
                        finishRequest,
                        context,
                        new WireEditor.UploadFinish(assignedUploadId)
                )
        ));
        return List.copyOf(outbound);
    }

    AckResult accept(
            UUID requestId,
            EditorRequestKind requestKind,
            WireEditor.UploadAck ack
    ) {
        if (requestKind == EditorRequestKind.UPLOAD_BEGIN) {
            if (!beginPending || !beginRequest.equals(requestId) || uploadId != null
                    || ack.complete() || ack.receivedFragments() != 0
                    || ack.receivedBytes() != 0L || ack.objectHash().isPresent()) {
                return AckResult.IGNORED;
            }
            uploadId = ack.uploadId();
            beginPending = false;
            return AckResult.BEGIN_ACCEPTED;
        }
        if (uploadId == null || !uploadId.equals(ack.uploadId())) {
            return AckResult.IGNORED;
        }
        if (requestKind == EditorRequestKind.UPLOAD_DATA) {
            return dataRequests.remove(requestId)
                    ? AckResult.DATA_ACCEPTED : AckResult.IGNORED;
        }
        if (requestKind != EditorRequestKind.UPLOAD_FINISH
                || !Objects.equals(finishRequest, requestId) || !ack.complete()) {
            return AckResult.IGNORED;
        }
        if (ack.objectHash().isEmpty() || !hash.equals(ack.objectHash().orElseThrow())) {
            return AckResult.HASH_MISMATCH;
        }
        complete = true;
        finishRequest = null;
        return AckResult.COMPLETED;
    }

    UUID beginRequest() {
        return beginRequest;
    }

    long sequence() {
        return sequence;
    }

    ContainerPath path() {
        return path;
    }

    UUID uploadId() {
        return Objects.requireNonNull(uploadId, "uploadId");
    }

    boolean complete() {
        return complete;
    }

    boolean matches(long expectedSequence, String expectedPath) {
        return sequence == expectedSequence && path.value().equals(expectedPath);
    }

    boolean ownsRequest(UUID requestId) {
        return (beginPending && beginRequest.equals(requestId))
                || dataRequests.contains(requestId)
                || requestId.equals(finishRequest);
    }

    Set<UUID> activeRequestIds() {
        Set<UUID> result = new HashSet<>(dataRequests);
        if (beginPending) result.add(beginRequest);
        if (finishRequest != null) result.add(finishRequest);
        return Set.copyOf(result);
    }

    private int fragmentCount() {
        return (payload.length - 1) / MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES + 1;
    }

    enum AckResult {
        IGNORED,
        BEGIN_ACCEPTED,
        DATA_ACCEPTED,
        COMPLETED,
        HASH_MISMATCH
    }

    record Outbound(
            UUID requestId,
            EditorRequestKind requestKind,
            EditorWireMessage.UploadFragment message
    ) {
        Outbound {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(requestKind, "requestKind");
            Objects.requireNonNull(message, "message");
        }
    }
}
