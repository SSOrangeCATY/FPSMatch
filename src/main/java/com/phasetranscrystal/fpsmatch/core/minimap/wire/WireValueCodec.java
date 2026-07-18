package com.phasetranscrystal.fpsmatch.core.minimap.wire;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;

import java.util.Optional;
import java.util.IdentityHashMap;
import java.util.UUID;

final class WireValueCodec {
    private static final int MIN_TRANSFER_METADATA_BYTES = 84;
    private static final int MAX_CACHED_RUNTIME_IDENTITIES = 128;
    private static final ThreadLocal<IdentityHashMap<
            WireIdentity.RuntimeIdentity, byte[]>> RUNTIME_IDENTITY_BYTES =
            ThreadLocal.withInitial(IdentityHashMap::new);

    private WireValueCodec() {
    }

    static void writeOptionalRequestId(WireWriter writer, Optional<UUID> requestId) {
        writer.writeBoolean(requestId.isPresent());
        requestId.ifPresent(writer::writeUuid);
    }

    static Optional<UUID> readOptionalRequestId(WireReader reader) {
        return reader.readBoolean() ? Optional.of(reader.readUuid()) : Optional.empty();
    }

    static void writeNamespacedId(WireWriter writer, NamespacedId id) {
        writer.writeUtf8(id.namespace(), MinimapHardLimits.MAX_NAMESPACE_UTF8_BYTES);
        writer.writeUtf8(id.path(), MinimapHardLimits.MAX_NAMESPACED_PATH_UTF8_BYTES);
    }

    static NamespacedId readNamespacedId(WireReader reader) {
        return new NamespacedId(
                reader.readUtf8(MinimapHardLimits.MAX_NAMESPACE_UTF8_BYTES),
                reader.readUtf8(MinimapHardLimits.MAX_NAMESPACED_PATH_UTF8_BYTES)
        );
    }

    static void writeTarget(WireWriter writer, WireIdentity.MapTarget target) {
        MapKey mapKey = target.mapKey();
        writer.writeUtf8(mapKey.gameType(), MinimapHardLimits.MAX_GAME_TYPE_UTF8_BYTES);
        writer.writeUtf8(mapKey.mapName(), MinimapHardLimits.MAX_MAP_NAME_UTF8_BYTES);
        writeNamespacedId(writer, target.dimension());
    }

    static WireIdentity.MapTarget readTarget(WireReader reader) {
        MapKey mapKey = new MapKey(
                reader.readUtf8(MinimapHardLimits.MAX_GAME_TYPE_UTF8_BYTES),
                reader.readUtf8(MinimapHardLimits.MAX_MAP_NAME_UTF8_BYTES)
        );
        return new WireIdentity.MapTarget(mapKey, readNamespacedId(reader));
    }

    static void writeDocumentBinding(
            WireWriter writer,
            WireIdentity.DocumentBinding binding
    ) {
        writeTarget(writer, binding.target());
        writeNamespacedId(writer, binding.documentId());
    }

    static WireIdentity.DocumentBinding readDocumentBinding(WireReader reader) {
        return new WireIdentity.DocumentBinding(
                readTarget(reader), readNamespacedId(reader)
        );
    }

    static void writeRuntimeIdentity(
            WireWriter writer,
            WireIdentity.RuntimeIdentity runtime
    ) {
        IdentityHashMap<WireIdentity.RuntimeIdentity, byte[]> cache =
                RUNTIME_IDENTITY_BYTES.get();
        byte[] bytes = cache.get(runtime);
        if (bytes == null) {
            bytes = encodeRuntimeIdentity(runtime);
            if (cache.size() >= MAX_CACHED_RUNTIME_IDENTITIES) {
                cache.clear();
            }
            cache.put(runtime, bytes);
        }
        writer.writeRawBytes(bytes);
    }

    private static byte[] encodeRuntimeIdentity(
            WireIdentity.RuntimeIdentity runtime
    ) {
        WireWriter writer = new WireWriter(
                MinimapHardLimits.MAX_WIRE_FRAME_BYTES,
                256
        );
        writeDocumentBinding(writer, runtime.binding());
        writer.writeNonNegativeVarLong(runtime.revision());
        writer.writeHash(runtime.runtimeHash());
        writer.writeBoolean(runtime.runtimeContainerHash().isPresent());
        runtime.runtimeContainerHash().ifPresent(writer::writeHash);
        return writer.toByteArray();
    }

    static WireIdentity.RuntimeIdentity readRuntimeIdentity(WireReader reader) {
        WireIdentity.DocumentBinding binding = readDocumentBinding(reader);
        long revision = reader.readNonNegativeVarLong();
        var runtimeHash = reader.readHash();
        Optional<com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256> containerHash =
                reader.readBoolean() ? Optional.of(reader.readHash()) : Optional.empty();
        return new WireIdentity.RuntimeIdentity(
                binding, revision, runtimeHash, containerHash
        );
    }

    static void writeLease(WireWriter writer, WireIdentity.ScopeLease lease) {
        writer.writeUnsignedByte(lease.scope().code());
        writer.writeNonNegativeVarLong(lease.scopeEpoch());
        writer.writeNonNegativeVarLong(lease.runtimeGeneration());
    }

    static WireIdentity.ScopeLease readLease(WireReader reader) {
        return new WireIdentity.ScopeLease(
                WireIdentity.Scope.fromCode(reader.readUnsignedByte()),
                reader.readNonNegativeVarLong(),
                reader.readNonNegativeVarLong()
        );
    }

    static void writeEditorContext(
            WireWriter writer,
            WireIdentity.EditorContext context
    ) {
        writeLease(writer, context.lease());
        writeDocumentBinding(writer, context.binding());
        writer.writeUuid(context.sessionId());
        writer.writeUuid(context.draftId());
        writer.writeNonNegativeVarLong(context.baseRevision());
        writer.writeHash(context.baseSourceHash());
        writer.writeHash(context.draftRootHash());
        writer.writeNonNegativeVarLong(context.ackCursor());
    }

    static WireIdentity.EditorContext readEditorContext(WireReader reader) {
        return new WireIdentity.EditorContext(
                readLease(reader),
                readDocumentBinding(reader),
                reader.readUuid(),
                reader.readUuid(),
                reader.readNonNegativeVarLong(),
                reader.readHash(),
                reader.readHash(),
                reader.readNonNegativeVarLong()
        );
    }

    static void writeTransfer(
            WireWriter writer,
            WireTransfer.TransferFragment transfer
    ) {
        int fragmentLength = transfer.fragmentBytes().length;
        long metadataBytes = (long) writer.writtenBytes()
                + 16L
                + unsignedVarIntSize(transfer.fragmentIndex())
                + unsignedVarIntSize(transfer.fragmentCount())
                + unsignedVarLongSize(transfer.totalLength())
                + 32L
                + 32L
                + unsignedVarIntSize(fragmentLength);
        requireFragmentMetadataBytes(metadataBytes);
        writer.writeUuid(transfer.transferId());
        writer.writeUnsignedVarInt(transfer.fragmentIndex());
        writer.writeUnsignedVarInt(transfer.fragmentCount());
        writer.writeNonNegativeVarLong(transfer.totalLength());
        writer.writeHash(transfer.objectHash());
        writer.writeHash(transfer.fragmentHash());
        writer.writeByteArray(
                transfer.fragmentBytes(), MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES
        );
    }

    static WireTransfer.TransferFragment readTransfer(
            WireReader reader,
            long maximumTotalLength
    ) {
        requireFragmentMetadataBytes(
                (long) reader.consumedBytes() + MIN_TRANSFER_METADATA_BYTES
        );
        UUID transferId = reader.readUuid();
        int fragmentIndex = reader.readUnsignedVarInt(
                MinimapHardLimits.MAX_WIRE_PAGE_COUNT
        );
        int fragmentCount = reader.readCount(MinimapHardLimits.MAX_WIRE_PAGE_COUNT);
        long totalLength = reader.readNonNegativeVarLong();
        if (totalLength > maximumTotalLength) {
            throw new MinimapWireError(
                    MinimapErrorCode.QUOTA_EXCEEDED,
                    "Transfer exceeds the opcode-specific byte limit"
            );
        }
        int expectedLength = WireTransfer.expectedFragmentLength(
                fragmentIndex, fragmentCount, totalLength
        );
        requireFragmentMetadataBytes(
                (long) reader.consumedBytes()
                        + 32L
                        + 32L
                        + unsignedVarIntSize(expectedLength)
        );
        var objectHash = reader.readHash();
        var fragmentHash = reader.readHash();
        byte[] fragmentData = reader.readByteArray(expectedLength);
        return new WireTransfer.TransferFragment(
                transferId,
                fragmentIndex,
                fragmentCount,
                totalLength,
                objectHash,
                fragmentHash,
                fragmentData
        );
    }

    private static int unsignedVarIntSize(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("unsigned VarInt value is negative");
        }
        int size = 1;
        while ((value >>>= 7) != 0) {
            size++;
        }
        return size;
    }

    private static int unsignedVarLongSize(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("unsigned VarLong value is negative");
        }
        int size = 1;
        while ((value >>>= 7) != 0) {
            size++;
        }
        return size;
    }

    private static void requireFragmentMetadataBytes(long metadataBytes) {
        if (metadataBytes > MinimapHardLimits.MAX_WIRE_FRAGMENT_METADATA_BYTES) {
            throw new MinimapWireError(
                    MinimapErrorCode.QUOTA_EXCEEDED,
                    "Transfer metadata exceeds its hard byte limit"
            );
        }
    }
}
