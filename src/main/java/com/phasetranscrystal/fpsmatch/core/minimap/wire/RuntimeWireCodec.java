package com.phasetranscrystal.fpsmatch.core.minimap.wire;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class RuntimeWireCodec {
    private RuntimeWireCodec() {
    }

    static byte[] encodeSubscribe(RuntimeWireMessage.Subscribe message) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeLease(writer, message.lease());
        WireValueCodec.writeTarget(writer, message.target());
        writer.writeBoolean(message.runtimeHint().isPresent());
        message.runtimeHint().ifPresent(hint -> {
            WireValueCodec.writeNamespacedId(writer, hint.documentId());
            writer.writeNonNegativeVarLong(hint.revision());
            writer.writeHash(hint.runtimeHash());
        });
        return writer.toByteArray();
    }

    static byte[] encodeUnsubscribe(RuntimeWireMessage.Unsubscribe message) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeLease(writer, message.lease());
        WireValueCodec.writeTarget(writer, message.target());
        return writer.toByteArray();
    }

    static byte[] encodeRequestEntries(RuntimeWireMessage.RequestEntries message) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeLease(writer, message.lease());
        WireValueCodec.writeRuntimeIdentity(writer, message.runtime());
        writer.writeUnsignedVarInt(message.entries().size());
        for (WireTransfer.EntryRequest entry : message.entries()) {
            writer.writeUtf8(entry.path().value(), MinimapHardLimits.MAX_ENTRY_PATH_UTF8_BYTES);
            writer.writeHash(entry.expectedHash());
        }
        return writer.toByteArray();
    }

    static byte[] encodeRequestMarkerReset(RuntimeWireMessage.RequestMarkerReset message) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeLease(writer, message.lease());
        WireValueCodec.writeRuntimeIdentity(writer, message.runtime());
        writer.writeBoolean(message.cursor().isPresent());
        message.cursor().ifPresent(cursor -> {
            writer.writeUuid(cursor.streamEpoch());
            writer.writeNonNegativeVarLong(cursor.lastSequence());
        });
        return writer.toByteArray();
    }

    static byte[] encodeScopeAck(RuntimeWireMessage.ScopeAck message) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeLease(writer, message.lease());
        WireValueCodec.writeRuntimeIdentity(writer, message.runtime());
        return writer.toByteArray();
    }

    static byte[] encodeManifest(RuntimeWireMessage.Manifest message) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        WireValueCodec.writeOptionalRequestId(writer, message.requestId());
        WireValueCodec.writeLease(writer, message.lease());
        WireValueCodec.writeRuntimeIdentity(writer, message.runtime());
        WireValueCodec.writeTransfer(writer, message.transfer());
        return writer.toByteArray();
    }

    static byte[] encodeEntryFragment(RuntimeWireMessage.EntryFragment message) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeLease(writer, message.lease());
        WireValueCodec.writeRuntimeIdentity(writer, message.runtime());
        writer.writeUtf8(message.path().value(), MinimapHardLimits.MAX_ENTRY_PATH_UTF8_BYTES);
        WireValueCodec.writeTransfer(writer, message.transfer());
        return writer.toByteArray();
    }

    static RuntimeWireMessage.Subscribe decodeSubscribe(WireReader reader) {
        RuntimeWireMessage.Subscribe result = new RuntimeWireMessage.Subscribe(
                reader.readUuid(),
                WireValueCodec.readLease(reader),
                WireValueCodec.readTarget(reader),
                reader.readBoolean()
                        ? Optional.of(new WireIdentity.RuntimeHint(
                        WireValueCodec.readNamespacedId(reader),
                        reader.readNonNegativeVarLong(),
                        reader.readHash()
                ))
                        : Optional.empty()
        );
        reader.requireFinished();
        return result;
    }

    static RuntimeWireMessage.Unsubscribe decodeUnsubscribe(WireReader reader) {
        RuntimeWireMessage.Unsubscribe result = new RuntimeWireMessage.Unsubscribe(
                reader.readUuid(),
                WireValueCodec.readLease(reader),
                WireValueCodec.readTarget(reader)
        );
        reader.requireFinished();
        return result;
    }

    static RuntimeWireMessage.RequestEntries decodeRequestEntries(WireReader reader) {
        UUID requestId = reader.readUuid();
        WireIdentity.ScopeLease lease = WireValueCodec.readLease(reader);
        WireIdentity.RuntimeIdentity runtime = WireValueCodec.readRuntimeIdentity(reader);
        int count = reader.readCount(256);
        List<WireTransfer.EntryRequest> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new WireTransfer.EntryRequest(
                    ContainerPath.parse(reader.readUtf8(
                            MinimapHardLimits.MAX_ENTRY_PATH_UTF8_BYTES
                    )),
                    reader.readHash()
            ));
        }
        RuntimeWireMessage.RequestEntries result = new RuntimeWireMessage.RequestEntries(
                requestId, lease, runtime, entries
        );
        reader.requireFinished();
        return result;
    }

    static RuntimeWireMessage.RequestMarkerReset decodeRequestMarkerReset(WireReader reader) {
        UUID requestId = reader.readUuid();
        WireIdentity.ScopeLease lease = WireValueCodec.readLease(reader);
        WireIdentity.RuntimeIdentity runtime = WireValueCodec.readRuntimeIdentity(reader);
        Optional<WireIdentity.MarkerStreamCursor> cursor = reader.readBoolean()
                ? Optional.of(new WireIdentity.MarkerStreamCursor(
                reader.readUuid(), reader.readNonNegativeVarLong()
        ))
                : Optional.empty();
        RuntimeWireMessage.RequestMarkerReset result =
                new RuntimeWireMessage.RequestMarkerReset(
                        requestId, lease, runtime, cursor
                );
        reader.requireFinished();
        return result;
    }

    static RuntimeWireMessage.ScopeAck decodeScopeAck(WireReader reader) {
        RuntimeWireMessage.ScopeAck result = new RuntimeWireMessage.ScopeAck(
                reader.readUuid(),
                WireValueCodec.readLease(reader),
                WireValueCodec.readRuntimeIdentity(reader)
        );
        reader.requireFinished();
        return result;
    }

    static RuntimeWireMessage.Manifest decodeManifest(WireReader reader) {
        RuntimeWireMessage.Manifest result = new RuntimeWireMessage.Manifest(
                WireValueCodec.readOptionalRequestId(reader),
                WireValueCodec.readLease(reader),
                WireValueCodec.readRuntimeIdentity(reader),
                WireValueCodec.readTransfer(
                        reader, MinimapHardLimits.MAX_RUNTIME_MANIFEST_BYTES
                )
        );
        reader.requireFinished();
        return result;
    }

    static RuntimeWireMessage.EntryFragment decodeEntryFragment(WireReader reader) {
        RuntimeWireMessage.EntryFragment result = new RuntimeWireMessage.EntryFragment(
                reader.readUuid(),
                WireValueCodec.readLease(reader),
                WireValueCodec.readRuntimeIdentity(reader),
                ContainerPath.parse(reader.readUtf8(MinimapHardLimits.MAX_ENTRY_PATH_UTF8_BYTES)),
                WireValueCodec.readTransfer(reader, MinimapHardLimits.MAX_ZIP_ENTRY_BYTES)
        );
        reader.requireFinished();
        return result;
    }
}
