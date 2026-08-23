package com.ptcrys.fpsmatch.core.minimap.wire;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class EditorWireCodec {
    private EditorWireCodec() {
    }

    static byte[] encodeEditorOpen(EditorWireMessage.EditorOpen message) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeLease(writer, message.lease());
        WireValueCodec.writeTarget(writer, message.target());
        WireValueCodec.writeNamespacedId(writer, message.documentId());
        writer.writeUnsignedByte(message.openMode().code());
        writer.writeNonNegativeVarLong(message.expectedRevision());
        writer.writeBoolean(message.expectedRuntimeHash().isPresent());
        message.expectedRuntimeHash().ifPresent(writer::writeHash);
        return writer.toByteArray();
    }

    static EditorWireMessage.EditorOpen decodeEditorOpen(WireReader reader) {
        EditorWireMessage.EditorOpen result = new EditorWireMessage.EditorOpen(
                reader.readUuid(),
                WireValueCodec.readLease(reader),
                WireValueCodec.readTarget(reader),
                WireValueCodec.readNamespacedId(reader),
                WireEditor.OpenMode.fromCode(reader.readUnsignedByte()),
                reader.readNonNegativeVarLong(),
                reader.readBoolean() ? Optional.of(reader.readHash()) : Optional.empty()
        );
        reader.requireFinished();
        return result;
    }

    static byte[] encodeEditorResume(EditorWireMessage.EditorResume message) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeLease(writer, message.lease());
        WireValueCodec.writeDocumentBinding(writer, message.binding());
        writer.writeUuid(message.draftId());
        writer.writeHash(message.draftRootHash());
        writer.writeNonNegativeVarLong(message.ackCursor());
        return writer.toByteArray();
    }

    static EditorWireMessage.EditorResume decodeEditorResume(WireReader reader) {
        EditorWireMessage.EditorResume result = new EditorWireMessage.EditorResume(
                reader.readUuid(),
                WireValueCodec.readLease(reader),
                WireValueCodec.readDocumentBinding(reader),
                reader.readUuid(),
                reader.readHash(),
                reader.readNonNegativeVarLong()
        );
        reader.requireFinished();
        return result;
    }

    static byte[] encodeRequestSourceEntries(
            EditorWireMessage.RequestSourceEntries message
    ) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeEditorContext(writer, message.context());
        writer.writeHash(message.sourceHash());
        writer.writeUnsignedVarInt(message.entries().size());
        for (WireTransfer.EntryRequest entry : message.entries()) {
            writer.writeUtf8(
                    entry.path().value(), MinimapHardLimits.MAX_ENTRY_PATH_UTF8_BYTES
            );
            writer.writeHash(entry.expectedHash());
        }
        return writer.toByteArray();
    }

    static EditorWireMessage.RequestSourceEntries decodeRequestSourceEntries(
            WireReader reader
    ) {
        var requestId = reader.readUuid();
        WireIdentity.EditorContext context = WireValueCodec.readEditorContext(reader);
        var sourceHash = reader.readHash();
        int count = reader.readCount(MinimapHardLimits.MAX_ENTRY_REQUESTS);
        List<WireTransfer.EntryRequest> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new WireTransfer.EntryRequest(
                    ContainerPath.parse(reader.readUtf8(
                            MinimapHardLimits.MAX_ENTRY_PATH_UTF8_BYTES
                    )),
                    reader.readHash()
            ));
        }
        EditorWireMessage.RequestSourceEntries result =
                new EditorWireMessage.RequestSourceEntries(
                        requestId, context, sourceHash, entries
                );
        reader.requireFinished();
        return result;
    }

    static byte[] encodeEditorOperation(EditorWireMessage.EditorOperation message) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeEditorContext(writer, message.context());
        writer.writeNonNegativeVarLong(message.opSequence());
        writer.writeHash(message.expectedRootHash());
        writer.writeHash(message.payloadHash());
        writer.writeUnsignedVarInt(message.mutations().size());
        for (WireEditor.DraftMutation mutation : message.mutations()) {
            writeMutation(writer, mutation);
        }
        return writer.toByteArray();
    }

    static EditorWireMessage.EditorOperation decodeEditorOperation(WireReader reader) {
        var requestId = reader.readUuid();
        WireIdentity.EditorContext context = WireValueCodec.readEditorContext(reader);
        long opSequence = reader.readNonNegativeVarLong();
        var expectedRootHash = reader.readHash();
        var payloadHash = reader.readHash();
        int count = reader.readCount(MinimapHardLimits.MAX_EDITOR_MUTATIONS);
        if (count == 0) {
            throw new MinimapWireError(
                    MinimapErrorCode.MALFORMED_MESSAGE,
                    "editor operation must contain a mutation"
            );
        }
        List<WireEditor.DraftMutation> mutations = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            mutations.add(readMutation(reader));
        }
        EditorWireMessage.EditorOperation result =
                new EditorWireMessage.EditorOperation(
                        requestId,
                        context,
                        opSequence,
                        expectedRootHash,
                        payloadHash,
                        mutations
                );
        reader.requireFinished();
        return result;
    }

    private static void writeMutation(
            WireWriter writer,
            WireEditor.DraftMutation mutation
    ) {
        writer.writeUnsignedByte(mutation.tag());
        if (mutation instanceof WireEditor.Put put) {
            writer.writeUtf8(
                    put.path().value(), MinimapHardLimits.MAX_ENTRY_PATH_UTF8_BYTES
            );
            writer.writeUnsignedByte(put.mediaType().code());
            writer.writeBoolean(put.oldHash().isPresent());
            put.oldHash().ifPresent(writer::writeHash);
            writer.writeHash(put.newHash());
            writer.writeUuid(put.completedUploadId());
            return;
        }
        WireEditor.Delete delete = (WireEditor.Delete) mutation;
        writer.writeUtf8(
                delete.path().value(), MinimapHardLimits.MAX_ENTRY_PATH_UTF8_BYTES
        );
        writer.writeHash(delete.oldHash());
    }

    private static WireEditor.DraftMutation readMutation(WireReader reader) {
        int tag = reader.readUnsignedByte();
        return switch (tag) {
            case 0 -> new WireEditor.Put(
                    ContainerPath.parse(reader.readUtf8(
                            MinimapHardLimits.MAX_ENTRY_PATH_UTF8_BYTES
                    )),
                    WireEditor.MediaType.fromCode(reader.readUnsignedByte()),
                    reader.readBoolean() ? Optional.of(reader.readHash()) : Optional.empty(),
                    reader.readHash(),
                    reader.readUuid()
            );
            case 1 -> new WireEditor.Delete(
                    ContainerPath.parse(reader.readUtf8(
                            MinimapHardLimits.MAX_ENTRY_PATH_UTF8_BYTES
                    )),
                    reader.readHash()
            );
            default -> throw new MinimapWireError(
                    MinimapErrorCode.MALFORMED_MESSAGE,
                    "unknown editor mutation tag"
            );
        };
    }

    static byte[] encodeUploadFragment(EditorWireMessage.UploadFragment message) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeEditorContext(writer, message.context());
        WireEditor.UploadActionData data = message.data();
        writer.writeUnsignedByte(data.tag());
        if (data instanceof WireEditor.UploadBegin begin) {
            writer.writeUnsignedByte(begin.purpose().code());
            writer.writeBoolean(begin.path().isPresent());
            begin.path().ifPresent(path -> writer.writeUtf8(
                    path.value(), MinimapHardLimits.MAX_ENTRY_PATH_UTF8_BYTES
            ));
            writer.writeNonNegativeVarLong(begin.totalLength());
            writer.writeUnsignedVarInt(begin.fragmentCount());
            writer.writeHash(begin.expectedHash());
        } else if (data instanceof WireEditor.UploadData uploadData) {
            WireValueCodec.writeTransfer(writer, uploadData.transfer());
        } else if (data instanceof WireEditor.UploadFinish finish) {
            writer.writeUuid(finish.uploadId());
        } else {
            writer.writeUuid(((WireEditor.UploadAbort) data).uploadId());
        }
        return writer.toByteArray();
    }

    static EditorWireMessage.UploadFragment decodeUploadFragment(WireReader reader) {
        var requestId = reader.readUuid();
        WireIdentity.EditorContext context = WireValueCodec.readEditorContext(reader);
        int action = reader.readUnsignedByte();
        WireEditor.UploadActionData data = switch (action) {
            case 0 -> readUploadBegin(reader);
            case 1 -> new WireEditor.UploadData(WireValueCodec.readTransfer(
                    reader, MinimapHardLimits.MAX_WIRE_TRANSFER_BYTES
            ));
            case 2 -> new WireEditor.UploadFinish(reader.readUuid());
            case 3 -> new WireEditor.UploadAbort(reader.readUuid());
            default -> throw new MinimapWireError(
                    MinimapErrorCode.MALFORMED_MESSAGE,
                    "unknown editor upload action"
            );
        };
        EditorWireMessage.UploadFragment result =
                new EditorWireMessage.UploadFragment(requestId, context, data);
        reader.requireFinished();
        return result;
    }

    private static WireEditor.UploadBegin readUploadBegin(WireReader reader) {
        WireEditor.UploadPurpose purpose = WireEditor.UploadPurpose.fromCode(
                reader.readUnsignedByte()
        );
        Optional<ContainerPath> path = reader.readBoolean()
                ? Optional.of(ContainerPath.parse(reader.readUtf8(
                MinimapHardLimits.MAX_ENTRY_PATH_UTF8_BYTES
        )))
                : Optional.empty();
        long totalLength = reader.readNonNegativeVarLong();
        if (totalLength <= 0 || totalLength > purpose.maximumBytes()) {
            throw new MinimapWireError(
                    MinimapErrorCode.QUOTA_EXCEEDED,
                    "editor upload length exceeds its purpose limit"
            );
        }
        int fragmentCount = reader.readCount(MinimapHardLimits.MAX_WIRE_PAGE_COUNT);
        return new WireEditor.UploadBegin(
                purpose, path, totalLength, fragmentCount, reader.readHash()
        );
    }

    static byte[] encodeSaveDraft(EditorWireMessage.SaveDraft message) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeEditorContext(writer, message.context());
        writer.writeNonNegativeVarLong(message.expectedAckCursor());
        writer.writeHash(message.expectedRootHash());
        writer.writeBoolean(message.compact());
        return writer.toByteArray();
    }

    static EditorWireMessage.SaveDraft decodeSaveDraft(WireReader reader) {
        EditorWireMessage.SaveDraft result = new EditorWireMessage.SaveDraft(
                reader.readUuid(),
                WireValueCodec.readEditorContext(reader),
                reader.readNonNegativeVarLong(),
                reader.readHash(),
                reader.readBoolean()
        );
        reader.requireFinished();
        return result;
    }

    static byte[] encodeEditorClose(EditorWireMessage.EditorClose message) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeEditorContext(writer, message.context());
        writer.writeUnsignedByte(message.closeMode().code());
        return writer.toByteArray();
    }

    static EditorWireMessage.EditorClose decodeEditorClose(WireReader reader) {
        EditorWireMessage.EditorClose result = new EditorWireMessage.EditorClose(
                reader.readUuid(),
                WireValueCodec.readEditorContext(reader),
                WireEditor.CloseMode.fromCode(reader.readUnsignedByte())
        );
        reader.requireFinished();
        return result;
    }

    static byte[] encodeEditorSession(EditorWireMessage.EditorSession message) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeEditorContext(writer, message.context());
        writer.writeNonNegativeVarLong(message.expiresAtEpochMillis());
        writer.writeUnsignedByte(message.sourceAvailability().code());
        return writer.toByteArray();
    }

    static EditorWireMessage.EditorSession decodeEditorSession(WireReader reader) {
        EditorWireMessage.EditorSession result = new EditorWireMessage.EditorSession(
                reader.readUuid(),
                WireValueCodec.readEditorContext(reader),
                reader.readNonNegativeVarLong(),
                WireEditor.SourceAvailability.fromCode(reader.readUnsignedByte())
        );
        reader.requireFinished();
        return result;
    }

    static byte[] encodeSourceManifest(EditorWireMessage.SourceManifest message) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeEditorContext(writer, message.context());
        writer.writeHash(message.sourceHash());
        writer.writeHash(message.manifestHash());
        WireValueCodec.writeTransfer(writer, message.transfer());
        return writer.toByteArray();
    }

    static EditorWireMessage.SourceManifest decodeSourceManifest(WireReader reader) {
        EditorWireMessage.SourceManifest result = new EditorWireMessage.SourceManifest(
                reader.readUuid(),
                WireValueCodec.readEditorContext(reader),
                reader.readHash(),
                reader.readHash(),
                WireValueCodec.readTransfer(
                        reader, MinimapHardLimits.MAX_SOURCE_MANIFEST_BYTES
                )
        );
        reader.requireFinished();
        return result;
    }

    static byte[] encodeSourceFragment(EditorWireMessage.SourceFragment message) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeEditorContext(writer, message.context());
        writer.writeHash(message.sourceHash());
        writer.writeUtf8(
                message.path().value(), MinimapHardLimits.MAX_ENTRY_PATH_UTF8_BYTES
        );
        writer.writeUnsignedByte(message.mediaType().code());
        WireValueCodec.writeTransfer(writer, message.transfer());
        return writer.toByteArray();
    }

    static EditorWireMessage.SourceFragment decodeSourceFragment(WireReader reader) {
        var requestId = reader.readUuid();
        WireIdentity.EditorContext context = WireValueCodec.readEditorContext(reader);
        var sourceHash = reader.readHash();
        ContainerPath path = ContainerPath.parse(reader.readUtf8(
                MinimapHardLimits.MAX_ENTRY_PATH_UTF8_BYTES
        ));
        WireEditor.MediaType mediaType = WireEditor.MediaType.fromCode(
                reader.readUnsignedByte()
        );
        long maximum = mediaType == WireEditor.MediaType.JSON
                ? MinimapHardLimits.MAX_JSON_ENTRY_BYTES
                : MinimapHardLimits.MAX_ZIP_ENTRY_BYTES;
        EditorWireMessage.SourceFragment result = new EditorWireMessage.SourceFragment(
                requestId,
                context,
                sourceHash,
                path,
                mediaType,
                WireValueCodec.readTransfer(reader, maximum)
        );
        reader.requireFinished();
        return result;
    }

    static byte[] encodeEditorAck(EditorWireMessage.EditorAck message) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeEditorContext(writer, message.context());
        WireEditor.AckData data = message.data();
        writer.writeUnsignedByte(data.tag());
        if (data instanceof WireEditor.DraftSaved saved) {
            writer.writeBoolean(saved.compacted());
        } else if (data instanceof WireEditor.UploadAck upload) {
            writer.writeUuid(upload.uploadId());
            writer.writeUnsignedVarInt(upload.receivedFragments());
            writer.writeNonNegativeVarLong(upload.receivedBytes());
            writer.writeBoolean(upload.complete());
            writer.writeBoolean(upload.objectHash().isPresent());
            upload.objectHash().ifPresent(writer::writeHash);
        } else if (data instanceof WireEditor.Closed closed) {
            writer.writeUnsignedByte(closed.closeMode().code());
        }
        return writer.toByteArray();
    }

    static EditorWireMessage.EditorAck decodeEditorAck(WireReader reader) {
        var requestId = reader.readUuid();
        WireIdentity.EditorContext context = WireValueCodec.readEditorContext(reader);
        int tag = reader.readUnsignedByte();
        WireEditor.AckData data = switch (tag) {
            case 0 -> new WireEditor.OperationAck();
            case 1 -> new WireEditor.DraftSaved(reader.readBoolean());
            case 2 -> readUploadAck(reader);
            case 3 -> new WireEditor.Closed(WireEditor.CloseMode.fromCode(
                    reader.readUnsignedByte()
            ));
            default -> throw new MinimapWireError(
                    MinimapErrorCode.MALFORMED_MESSAGE,
                    "unknown editor ACK tag"
            );
        };
        EditorWireMessage.EditorAck result = new EditorWireMessage.EditorAck(
                requestId, context, data
        );
        reader.requireFinished();
        return result;
    }

    private static WireEditor.UploadAck readUploadAck(WireReader reader) {
        var uploadId = reader.readUuid();
        int receivedFragments = reader.readUnsignedVarInt(
                MinimapHardLimits.MAX_WIRE_PAGE_COUNT
        );
        long receivedBytes = reader.readNonNegativeVarLong();
        if (receivedBytes > MinimapHardLimits.MAX_WIRE_TRANSFER_BYTES) {
            throw new MinimapWireError(
                    MinimapErrorCode.QUOTA_EXCEEDED,
                    "Upload ACK byte count exceeds its limit"
            );
        }
        boolean complete = reader.readBoolean();
        Optional<Sha256> objectHash =
                reader.readBoolean() ? Optional.of(reader.readHash()) : Optional.empty();
        return new WireEditor.UploadAck(
                uploadId, receivedFragments, receivedBytes, complete, objectHash
        );
    }

}
