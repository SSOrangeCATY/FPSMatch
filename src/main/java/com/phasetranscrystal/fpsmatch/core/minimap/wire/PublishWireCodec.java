package com.phasetranscrystal.fpsmatch.core.minimap.wire;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class PublishWireCodec {
    private PublishWireCodec() {
    }

    static byte[] encodeEditorRebase(PublishWireMessage.EditorRebase message) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeEditorContext(writer, message.context());
        WireEditor.RebaseData data = message.data();
        writer.writeUnsignedByte(data.tag());
        if (data instanceof WireEditor.RebaseStart start) {
            writer.writeNonNegativeVarLong(start.theirsRevision());
            writer.writeHash(start.theirsHash());
        } else {
            WireEditor.RebaseResolve resolve = (WireEditor.RebaseResolve) data;
            writer.writeUuid(resolve.rebaseId());
            writer.writeUnsignedVarInt(resolve.resolutions().size());
            for (WireEditor.Resolution resolution : resolve.resolutions()) {
                writer.writeHash(resolution.conflictHash());
                writer.writeUnsignedByte(resolution.choice().code());
            }
        }
        return writer.toByteArray();
    }

    static PublishWireMessage.EditorRebase decodeEditorRebase(WireReader reader) {
        var requestId = reader.readUuid();
        WireIdentity.EditorContext context = WireValueCodec.readEditorContext(reader);
        int tag = reader.readUnsignedByte();
        WireEditor.RebaseData data = switch (tag) {
            case 0 -> new WireEditor.RebaseStart(
                    reader.readNonNegativeVarLong(), reader.readHash()
            );
            case 1 -> readRebaseResolve(reader);
            default -> throw new MinimapWireError(
                    MinimapErrorCode.MALFORMED_MESSAGE,
                    "unknown editor rebase phase"
            );
        };
        PublishWireMessage.EditorRebase result =
                new PublishWireMessage.EditorRebase(requestId, context, data);
        reader.requireFinished();
        return result;
    }

    static byte[] encodeReservePublish(PublishWireMessage.ReservePublish message) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeEditorContext(writer, message.context());
        return writer.toByteArray();
    }

    static PublishWireMessage.ReservePublish decodeReservePublish(WireReader reader) {
        PublishWireMessage.ReservePublish result =
                new PublishWireMessage.ReservePublish(
                        reader.readUuid(),
                        WireValueCodec.readEditorContext(reader)
                );
        reader.requireFinished();
        return result;
    }

    static byte[] encodeCommitPublish(PublishWireMessage.CommitPublish message) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeEditorContext(writer, message.context());
        writer.writeUtf8(
                message.publishToken(),
                MinimapHardLimits.MAX_PUBLISH_TOKEN_UTF8_BYTES
        );
        writer.writeNonNegativeVarLong(message.publishRevision());
        writer.writeUuid(message.sourceUploadId());
        writer.writeUuid(message.runtimeUploadId());
        writer.writeHash(message.sourceHash());
        writer.writeHash(message.runtimeHash());
        writer.writeHash(message.runtimeContainerHash());
        return writer.toByteArray();
    }

    static PublishWireMessage.CommitPublish decodeCommitPublish(WireReader reader) {
        PublishWireMessage.CommitPublish result =
                new PublishWireMessage.CommitPublish(
                        reader.readUuid(),
                        WireValueCodec.readEditorContext(reader),
                        reader.readUtf8(MinimapHardLimits.MAX_PUBLISH_TOKEN_UTF8_BYTES),
                        reader.readNonNegativeVarLong(),
                        reader.readUuid(),
                        reader.readUuid(),
                        reader.readHash(),
                        reader.readHash(),
                        reader.readHash()
                );
        reader.requireFinished();
        return result;
    }

    static byte[] encodeQueryPublishStatus(
            PublishWireMessage.QueryPublishStatus message
    ) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeLease(writer, message.lease());
        WireValueCodec.writeDocumentBinding(writer, message.binding());
        writer.writeUtf8(
                message.publishToken(),
                MinimapHardLimits.MAX_PUBLISH_TOKEN_UTF8_BYTES
        );
        writer.writeNonNegativeVarLong(message.publishRevision());
        return writer.toByteArray();
    }

    static PublishWireMessage.QueryPublishStatus decodeQueryPublishStatus(
            WireReader reader
    ) {
        PublishWireMessage.QueryPublishStatus result =
                new PublishWireMessage.QueryPublishStatus(
                        reader.readUuid(),
                        readEditorLease(reader),
                        WireValueCodec.readDocumentBinding(reader),
                        reader.readUtf8(MinimapHardLimits.MAX_PUBLISH_TOKEN_UTF8_BYTES),
                        reader.readNonNegativeVarLong()
                );
        reader.requireFinished();
        return result;
    }

    static byte[] encodePublishReservation(
            PublishWireMessage.PublishReservation message
    ) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeEditorContext(writer, message.context());
        writer.writeUtf8(
                message.publishToken(),
                MinimapHardLimits.MAX_PUBLISH_TOKEN_UTF8_BYTES
        );
        writer.writeNonNegativeVarLong(message.baseRevision());
        writer.writeNonNegativeVarLong(message.publishRevision());
        writer.writeNonNegativeVarLong(message.expiresAtEpochMillis());
        return writer.toByteArray();
    }

    static PublishWireMessage.PublishReservation decodePublishReservation(
            WireReader reader
    ) {
        PublishWireMessage.PublishReservation result =
                new PublishWireMessage.PublishReservation(
                        reader.readUuid(),
                        WireValueCodec.readEditorContext(reader),
                        reader.readUtf8(MinimapHardLimits.MAX_PUBLISH_TOKEN_UTF8_BYTES),
                        reader.readNonNegativeVarLong(),
                        reader.readNonNegativeVarLong(),
                        reader.readNonNegativeVarLong()
                );
        reader.requireFinished();
        return result;
    }

    static byte[] encodeEditorRebaseResult(
            PublishWireMessage.EditorRebaseResult message
    ) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeLease(writer, message.lease());
        WireValueCodec.writeDocumentBinding(writer, message.binding());
        writer.writeUuid(message.rebaseId());
        writer.writeUnsignedByte(message.status().code());
        writer.writeBoolean(message.newContext().isPresent());
        message.newContext().ifPresent(context ->
                WireValueCodec.writeEditorContext(writer, context)
        );
        writer.writeNonNegativeVarLong(message.theirsRevision());
        writer.writeHash(message.theirsHash());
        writer.writeUnsignedVarInt(message.pageIndex());
        writer.writeUnsignedVarInt(message.pageCount());
        writer.writeUnsignedVarInt(message.conflicts().size());
        for (WireEditor.Conflict conflict : message.conflicts()) {
            writeConflict(writer, conflict);
        }
        return writer.toByteArray();
    }

    static PublishWireMessage.EditorRebaseResult decodeEditorRebaseResult(
            WireReader reader
    ) {
        var requestId = reader.readUuid();
        WireIdentity.ScopeLease lease = readEditorLease(reader);
        WireIdentity.DocumentBinding binding = WireValueCodec.readDocumentBinding(reader);
        var rebaseId = reader.readUuid();
        WireStatus.RebaseResultStatus status = WireStatus.RebaseResultStatus.fromCode(
                reader.readUnsignedByte()
        );
        Optional<WireIdentity.EditorContext> newContext = reader.readBoolean()
                ? Optional.of(WireValueCodec.readEditorContext(reader))
                : Optional.empty();
        long theirsRevision = reader.readNonNegativeVarLong();
        var theirsHash = reader.readHash();
        int pageIndex = reader.readUnsignedVarInt(MinimapHardLimits.MAX_WIRE_PAGE_COUNT);
        int pageCount = reader.readCount(MinimapHardLimits.MAX_WIRE_PAGE_COUNT);
        requireRebaseResultHeader(status, newContext, pageIndex, pageCount);
        int conflictCount = reader.readCount(MinimapHardLimits.MAX_REBASE_ITEMS);
        requireRebaseConflictCount(status, conflictCount);
        List<WireEditor.Conflict> conflicts = new ArrayList<>(conflictCount);
        for (int index = 0; index < conflictCount; index++) {
            conflicts.add(readConflict(reader));
        }
        PublishWireMessage.EditorRebaseResult result =
                new PublishWireMessage.EditorRebaseResult(
                        requestId,
                        lease,
                        binding,
                        rebaseId,
                        status,
                        newContext,
                        theirsRevision,
                        theirsHash,
                        pageIndex,
                        pageCount,
                        conflicts
                );
        reader.requireFinished();
        return result;
    }

    static byte[] encodePublishResult(PublishWireMessage.PublishResult message) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeLease(writer, message.lease());
        WireValueCodec.writeDocumentBinding(writer, message.binding());
        writer.writeUtf8(
                message.publishToken(),
                MinimapHardLimits.MAX_PUBLISH_TOKEN_UTF8_BYTES
        );
        writer.writeNonNegativeVarLong(message.publishRevision());
        writer.writeUnsignedByte(message.outcome().code());
        writer.writeBoolean(message.hashes().isPresent());
        message.hashes().ifPresent(hashes -> writeHashTriple(writer, hashes));
        writer.writeBoolean(message.error().isPresent());
        message.error().ifPresent(error -> writeErrorInfo(writer, error));
        return writer.toByteArray();
    }

    static PublishWireMessage.PublishResult decodePublishResult(WireReader reader) {
        var requestId = reader.readUuid();
        WireIdentity.ScopeLease lease = readEditorLease(reader);
        WireIdentity.DocumentBinding binding = WireValueCodec.readDocumentBinding(reader);
        String publishToken = reader.readUtf8(
                MinimapHardLimits.MAX_PUBLISH_TOKEN_UTF8_BYTES
        );
        long publishRevision = reader.readNonNegativeVarLong();
        WireStatus.PublishOutcome outcome = WireStatus.PublishOutcome.fromCode(
                reader.readUnsignedByte()
        );
        boolean hashesPresent = reader.readBoolean();
        if (hashesPresent != (outcome == WireStatus.PublishOutcome.COMMITTED)) {
            throw malformed("publish outcome has invalid hash presence");
        }
        Optional<WireStatus.HashTriple> hashes = hashesPresent
                ? Optional.of(readHashTriple(reader))
                : Optional.empty();
        boolean errorPresent = reader.readBoolean();
        if (errorPresent == (outcome == WireStatus.PublishOutcome.COMMITTED)) {
            throw malformed("publish outcome has invalid error presence");
        }
        Optional<WireStatus.ErrorInfo> error = errorPresent
                ? Optional.of(readErrorInfo(reader))
                : Optional.empty();
        PublishWireMessage.PublishResult result =
                new PublishWireMessage.PublishResult(
                        requestId,
                        lease,
                        binding,
                        publishToken,
                        publishRevision,
                        outcome,
                        hashes,
                        error
                );
        reader.requireFinished();
        return result;
    }

    static byte[] encodePublishStatus(PublishWireMessage.PublishStatus message) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeLease(writer, message.lease());
        WireValueCodec.writeDocumentBinding(writer, message.binding());
        writer.writeUtf8(
                message.publishToken(),
                MinimapHardLimits.MAX_PUBLISH_TOKEN_UTF8_BYTES
        );
        writer.writeNonNegativeVarLong(message.publishRevision());
        writer.writeUnsignedByte(message.state().code());
        writer.writeBoolean(message.currentRevision().isPresent());
        message.currentRevision().ifPresent(writer::writeNonNegativeVarLong);
        writer.writeBoolean(message.hashes().isPresent());
        message.hashes().ifPresent(hashes -> writeHashTriple(writer, hashes));
        writer.writeBoolean(message.error().isPresent());
        message.error().ifPresent(error -> writeErrorInfo(writer, error));
        return writer.toByteArray();
    }

    static PublishWireMessage.PublishStatus decodePublishStatus(WireReader reader) {
        var requestId = reader.readUuid();
        WireIdentity.ScopeLease lease = readEditorLease(reader);
        WireIdentity.DocumentBinding binding = WireValueCodec.readDocumentBinding(reader);
        String publishToken = reader.readUtf8(
                MinimapHardLimits.MAX_PUBLISH_TOKEN_UTF8_BYTES
        );
        long publishRevision = reader.readNonNegativeVarLong();
        WireStatus.PublishState state = WireStatus.PublishState.fromCode(
                reader.readUnsignedByte()
        );
        boolean currentPresent = reader.readBoolean();
        if (currentPresent != (state != WireStatus.PublishState.STATUS_UNKNOWN)) {
            throw malformed("publish state has invalid current-revision presence");
        }
        Optional<Long> currentRevision = currentPresent
                ? Optional.of(reader.readNonNegativeVarLong())
                : Optional.empty();
        boolean hashesPresent = reader.readBoolean();
        if (hashesPresent != (state == WireStatus.PublishState.COMMITTED)) {
            throw malformed("publish state has invalid hash presence");
        }
        Optional<WireStatus.HashTriple> hashes = hashesPresent
                ? Optional.of(readHashTriple(reader))
                : Optional.empty();
        boolean errorPresent = reader.readBoolean();
        boolean expectsError = state == WireStatus.PublishState.ABORTED
                || state == WireStatus.PublishState.STATUS_UNKNOWN;
        if (errorPresent != expectsError) {
            throw malformed("publish state has invalid error presence");
        }
        Optional<WireStatus.ErrorInfo> error = errorPresent
                ? Optional.of(readErrorInfo(reader))
                : Optional.empty();
        PublishWireMessage.PublishStatus result =
                new PublishWireMessage.PublishStatus(
                        requestId,
                        lease,
                        binding,
                        publishToken,
                        publishRevision,
                        state,
                        currentRevision,
                        hashes,
                        error
                );
        reader.requireFinished();
        return result;
    }

    static byte[] encodeErrorMessage(PublishWireMessage.ErrorMessage message) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        WireValueCodec.writeOptionalRequestId(writer, message.requestId());
        writer.writeBoolean(message.lease().isPresent());
        message.lease().ifPresent(lease -> WireValueCodec.writeLease(writer, lease));
        writer.writeBoolean(message.binding().isPresent());
        message.binding().ifPresent(binding ->
                WireValueCodec.writeDocumentBinding(writer, binding)
        );
        writer.writeBoolean(message.failedOpcode().isPresent());
        message.failedOpcode().ifPresent(writer::writeUnsignedByte);
        writeErrorInfo(writer, message.error());
        return writer.toByteArray();
    }

    static PublishWireMessage.ErrorMessage decodeErrorMessage(WireReader reader) {
        Optional<java.util.UUID> requestId = WireValueCodec.readOptionalRequestId(reader);
        Optional<WireIdentity.ScopeLease> lease = reader.readBoolean()
                ? Optional.of(WireValueCodec.readLease(reader))
                : Optional.empty();
        Optional<WireIdentity.DocumentBinding> binding = reader.readBoolean()
                ? Optional.of(WireValueCodec.readDocumentBinding(reader))
                : Optional.empty();
        Optional<Integer> failedOpcode = reader.readBoolean()
                ? Optional.of(reader.readUnsignedByte())
                : Optional.empty();
        PublishWireMessage.ErrorMessage result =
                new PublishWireMessage.ErrorMessage(
                        requestId,
                        lease,
                        binding,
                        failedOpcode,
                        readErrorInfo(reader)
                );
        reader.requireFinished();
        return result;
    }

    private static void writeConflict(
            WireWriter writer,
            WireEditor.Conflict conflict
    ) {
        writer.writeHash(conflict.conflictHash());
        WireEditor.ConflictSubject subject = conflict.subject();
        writer.writeUnsignedByte(subject.tag());
        if (subject instanceof WireEditor.PathSubject path) {
            writer.writeUtf8(
                    path.path().value(), MinimapHardLimits.MAX_ENTRY_PATH_UTF8_BYTES
            );
        } else {
            WireValueCodec.writeNamespacedId(
                    writer, ((WireEditor.IdSubject) subject).id()
            );
        }
        writer.writeBoolean(conflict.oursHash().isPresent());
        conflict.oursHash().ifPresent(writer::writeHash);
        writer.writeBoolean(conflict.theirsHash().isPresent());
        conflict.theirsHash().ifPresent(writer::writeHash);
    }

    private static void writeHashTriple(
            WireWriter writer,
            WireStatus.HashTriple hashes
    ) {
        writer.writeHash(hashes.sourceHash());
        writer.writeHash(hashes.runtimeHash());
        writer.writeHash(hashes.runtimeContainerHash());
    }

    private static WireStatus.HashTriple readHashTriple(WireReader reader) {
        return new WireStatus.HashTriple(
                reader.readHash(), reader.readHash(), reader.readHash()
        );
    }

    private static void writeErrorInfo(
            WireWriter writer,
            WireStatus.ErrorInfo error
    ) {
        writer.writeUnsignedShort(error.errorCode());
        writer.writeUnsignedByte(error.retryDisposition().code());
        writer.writeUtf8(
                error.detail(), MinimapHardLimits.MAX_ERROR_DETAIL_UTF8_BYTES
        );
    }

    private static WireStatus.ErrorInfo readErrorInfo(WireReader reader) {
        int errorCode = reader.readUnsignedShort();
        WireStatus.requireKnownErrorCode(errorCode);
        WireStatus.RetryDisposition retry = WireStatus.RetryDisposition.fromCode(
                reader.readUnsignedByte()
        );
        return new WireStatus.ErrorInfo(
                errorCode,
                retry,
                reader.readUtf8(MinimapHardLimits.MAX_ERROR_DETAIL_UTF8_BYTES)
        );
    }

    private static WireEditor.Conflict readConflict(WireReader reader) {
        var conflictHash = reader.readHash();
        int subjectTag = reader.readUnsignedByte();
        WireEditor.ConflictSubject subject = switch (subjectTag) {
            case 0 -> new WireEditor.PathSubject(
                    com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath.parse(
                            reader.readUtf8(MinimapHardLimits.MAX_ENTRY_PATH_UTF8_BYTES)
                    )
            );
            case 1 -> new WireEditor.IdSubject(
                    WireValueCodec.readNamespacedId(reader)
            );
            default -> throw new MinimapWireError(
                    MinimapErrorCode.MALFORMED_MESSAGE,
                    "unknown rebase conflict subject"
            );
        };
        Optional<com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256> oursHash =
                reader.readBoolean() ? Optional.of(reader.readHash()) : Optional.empty();
        Optional<com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256> theirsHash =
                reader.readBoolean() ? Optional.of(reader.readHash()) : Optional.empty();
        return new WireEditor.Conflict(
                conflictHash, subject, oursHash, theirsHash
        );
    }

    private static void requireRebaseResultHeader(
            WireStatus.RebaseResultStatus status,
            Optional<WireIdentity.EditorContext> newContext,
            int pageIndex,
            int pageCount
    ) {
        boolean valid = status == WireStatus.RebaseResultStatus.MERGED
                ? newContext.isPresent()
                && pageIndex == 0
                && pageCount == 1
                : newContext.isEmpty()
                && pageCount > 0
                && pageIndex < pageCount;
        if (!valid) {
            throw new MinimapWireError(
                    MinimapErrorCode.MALFORMED_MESSAGE,
                    "invalid rebase-result context or page"
            );
        }
    }

    private static void requireRebaseConflictCount(
            WireStatus.RebaseResultStatus status,
            int conflictCount
    ) {
        boolean valid = status == WireStatus.RebaseResultStatus.MERGED
                ? conflictCount == 0
                : conflictCount > 0;
        if (!valid) {
            throw malformed("invalid rebase-result conflict count");
        }
    }

    private static MinimapWireError malformed(String message) {
        return new MinimapWireError(MinimapErrorCode.MALFORMED_MESSAGE, message);
    }

    private static WireIdentity.ScopeLease readEditorLease(WireReader reader) {
        WireIdentity.ScopeLease lease = WireValueCodec.readLease(reader);
        WireEditor.requireEditorLease(lease);
        return lease;
    }

    private static WireEditor.RebaseResolve readRebaseResolve(WireReader reader) {
        var rebaseId = reader.readUuid();
        int count = reader.readCount(MinimapHardLimits.MAX_REBASE_ITEMS);
        List<WireEditor.Resolution> resolutions = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            resolutions.add(new WireEditor.Resolution(
                    reader.readHash(),
                    WireEditor.ResolutionChoice.fromCode(reader.readUnsignedByte())
            ));
        }
        return new WireEditor.RebaseResolve(rebaseId, resolutions);
    }
}
