package com.ptcrys.fpsmatch.core.minimap.wire;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class MarkerWireCodec {
    private static final int MAX_STATE_STRING_BYTES = 1_024;
    private static final int MAX_CACHED_MARKERS = 8_192;
    private static final ThreadLocal<IdentityHashMap<WireMarker.Marker, byte[]>>
            MARKER_BYTES = ThreadLocal.withInitial(IdentityHashMap::new);

    private MarkerWireCodec() {
    }

    static byte[] encodeReset(MarkerWireMessage.Reset message) {
        WireWriter writer = new WireWriter(
                MinimapHardLimits.MAX_WIRE_FRAME_BYTES,
                estimatedMarkerBytes(message.markers().size())
        );
        writeReset(writer, message);
        return writer.toByteArray();
    }

    static void writeReset(
            WireWriter writer,
            MarkerWireMessage.Reset message
    ) {
        WireValueCodec.writeOptionalRequestId(writer, message.requestId());
        WireValueCodec.writeLease(writer, message.lease());
        WireValueCodec.writeRuntimeIdentity(writer, message.runtime());
        writer.writeUuid(message.streamEpoch());
        writer.writeNonNegativeVarLong(message.sequence());
        writer.writeUuid(message.resetId());
        writer.writeUnsignedVarInt(message.pageIndex());
        writer.writeUnsignedVarInt(message.pageCount());
        writer.writeUnsignedVarInt(message.markers().size());
        for (WireMarker.Marker marker : message.markers()) {
            writeMarker(writer, marker);
        }
    }

    static byte[] encodeDelta(MarkerWireMessage.Delta message) {
        WireWriter writer = new WireWriter(
                MinimapHardLimits.MAX_WIRE_FRAME_BYTES,
                estimatedMarkerBytes(message.operations().size())
        );
        writeDelta(writer, message);
        return writer.toByteArray();
    }

    static void writeDelta(
            WireWriter writer,
            MarkerWireMessage.Delta message
    ) {
        WireValueCodec.writeLease(writer, message.lease());
        WireValueCodec.writeRuntimeIdentity(writer, message.runtime());
        writer.writeUuid(message.streamEpoch());
        writer.writeNonNegativeVarLong(message.sequence());
        writer.writeUnsignedVarInt(message.operations().size());
        for (WireMarker.DeltaOperation operation : message.operations()) {
            writer.writeUnsignedByte(operation.tag());
            if (operation instanceof WireMarker.Add add) {
                writeMarker(writer, add.marker());
            } else if (operation instanceof WireMarker.Update update) {
                writeMarker(writer, update.marker());
            } else {
                WireValueCodec.writeNamespacedId(
                        writer, ((WireMarker.Remove) operation).markerId()
                );
            }
        }
    }

    private static int estimatedMarkerBytes(int itemCount) {
        long estimated = 192L + itemCount * 160L;
        return (int) Math.min(
                MinimapHardLimits.MAX_WIRE_FRAME_BYTES,
                estimated
        );
    }

    static MarkerWireMessage.Reset decodeReset(WireReader reader) {
        Optional<UUID> requestId = WireValueCodec.readOptionalRequestId(reader);
        WireIdentity.ScopeLease lease = WireValueCodec.readLease(reader);
        WireIdentity.RuntimeIdentity runtime = WireValueCodec.readRuntimeIdentity(reader);
        UUID streamEpoch = reader.readUuid();
        long sequence = reader.readNonNegativeVarLong();
        UUID resetId = reader.readUuid();
        int pageIndex = reader.readUnsignedVarInt(MinimapHardLimits.MAX_WIRE_PAGE_COUNT);
        int pageCount = reader.readCount(MinimapHardLimits.MAX_WIRE_PAGE_COUNT);
        int markerCount = reader.readCount(MarkerWireMessage.MAX_PAGE_ITEMS);
        List<WireMarker.Marker> markers = new ArrayList<>(markerCount);
        for (int index = 0; index < markerCount; index++) {
            markers.add(readMarker(reader));
        }
        MarkerWireMessage.Reset result = new MarkerWireMessage.Reset(
                requestId,
                lease,
                runtime,
                streamEpoch,
                sequence,
                resetId,
                pageIndex,
                pageCount,
                markers
        );
        reader.requireFinished();
        return result;
    }

    static MarkerWireMessage.Delta decodeDelta(WireReader reader) {
        WireIdentity.ScopeLease lease = WireValueCodec.readLease(reader);
        WireIdentity.RuntimeIdentity runtime = WireValueCodec.readRuntimeIdentity(reader);
        UUID streamEpoch = reader.readUuid();
        long sequence = reader.readNonNegativeVarLong();
        int operationCount = reader.readCount(MarkerWireMessage.MAX_PAGE_ITEMS);
        List<WireMarker.DeltaOperation> operations = new ArrayList<>(operationCount);
        for (int index = 0; index < operationCount; index++) {
            int tag = reader.readUnsignedByte();
            operations.add(switch (tag) {
                case 0 -> new WireMarker.Add(readMarker(reader));
                case 1 -> new WireMarker.Update(readMarker(reader));
                case 2 -> new WireMarker.Remove(WireValueCodec.readNamespacedId(reader));
                default -> throw malformed("unknown marker delta tag");
            });
        }
        MarkerWireMessage.Delta result = new MarkerWireMessage.Delta(
                lease, runtime, streamEpoch, sequence, operations
        );
        reader.requireFinished();
        return result;
    }

    private static void writeMarker(WireWriter writer, WireMarker.Marker marker) {
        IdentityHashMap<WireMarker.Marker, byte[]> cache = MARKER_BYTES.get();
        byte[] bytes = cache.get(marker);
        if (bytes == null) {
            bytes = encodeMarker(marker);
            if (cache.size() >= MAX_CACHED_MARKERS) {
                cache.clear();
            }
            cache.put(marker, bytes);
        }
        writer.writeRawBytes(bytes);
    }

    private static byte[] encodeMarker(WireMarker.Marker marker) {
        WireWriter counting = WireWriter.counting(
                MinimapHardLimits.MAX_WIRE_FRAME_BYTES
        );
        writeMarkerFields(counting, marker);

        WireWriter exact = new WireWriter(
                MinimapHardLimits.MAX_WIRE_FRAME_BYTES,
                counting.writtenBytes()
        );
        writeMarkerFields(exact, marker);
        return exact.takeExactByteArray();
    }

    private static void writeMarkerFields(
            WireWriter writer,
            WireMarker.Marker marker
    ) {
        WireValueCodec.writeNamespacedId(writer, marker.markerId());
        WireValueCodec.writeNamespacedId(writer, marker.typeId());
        WireValueCodec.writeNamespacedId(writer, marker.styleId());
        writer.writeDouble(marker.x());
        writer.writeDouble(marker.y());
        writer.writeDouble(marker.z());
        writer.writeFloat(marker.yaw());
        writer.writeNonNegativeVarLong(marker.updatedTick());
        writer.writeBoolean(marker.expiresTick().isPresent());
        marker.expiresTick().ifPresent(writer::writeNonNegativeVarLong);
        writer.writeBoolean(marker.floorSlug().isPresent());
        marker.floorSlug().ifPresent(value -> writer.writeUtf8(
                value, MinimapHardLimits.MAX_INTERNAL_SLUG_UTF8_BYTES
        ));
        writer.writeUnsignedVarInt(marker.stateFields().size());
        for (WireMarker.StateField field : marker.stateFields()) {
            writeStateField(writer, field);
        }
    }

    private static WireMarker.Marker readMarker(WireReader reader) {
        NamespacedId markerId = WireValueCodec.readNamespacedId(reader);
        NamespacedId typeId = WireValueCodec.readNamespacedId(reader);
        NamespacedId styleId = WireValueCodec.readNamespacedId(reader);
        double x = reader.readDouble();
        double y = reader.readDouble();
        double z = reader.readDouble();
        float yaw = reader.readFloat();
        long updatedTick = reader.readNonNegativeVarLong();
        Optional<Long> expiresTick = reader.readBoolean()
                ? Optional.of(reader.readNonNegativeVarLong())
                : Optional.empty();
        Optional<String> floorSlug = reader.readBoolean()
                ? Optional.of(reader.readUtf8(MinimapHardLimits.MAX_INTERNAL_SLUG_UTF8_BYTES))
                : Optional.empty();
        int stateCount = reader.readCount(MinimapHardLimits.MAX_MARKER_STATE_FIELDS);
        List<WireMarker.StateField> fields = new ArrayList<>(stateCount);
        byte[] previousKey = null;
        int aggregateBytes = 0;
        for (int index = 0; index < stateCount; index++) {
            int before = reader.remaining();
            WireMarker.StateField field = readStateField(reader);
            byte[] canonicalKey = WireMarker.canonicalIdBytes(field.key());
            if (previousKey != null
                    && Arrays.compareUnsigned(previousKey, canonicalKey) >= 0) {
                throw malformed("marker state keys are not strictly ordered");
            }
            aggregateBytes = WireReader.checkedCountTotal(
                    aggregateBytes,
                    before - reader.remaining(),
                    MinimapHardLimits.MAX_MARKER_STATE_BYTES
            );
            fields.add(field);
            previousKey = canonicalKey;
        }
        return new WireMarker.Marker(
                markerId,
                typeId,
                styleId,
                x,
                y,
                z,
                yaw,
                updatedTick,
                expiresTick,
                floorSlug,
                fields
        );
    }

    private static void writeStateField(
            WireWriter writer,
            WireMarker.StateField field
    ) {
        WireValueCodec.writeNamespacedId(writer, field.key());
        WireMarker.StateValue value = field.value();
        writer.writeUnsignedByte(value.tag());
        if (value instanceof WireMarker.BoolValue bool) {
            writer.writeBoolean(bool.value());
        } else if (value instanceof WireMarker.SignedLongValue signed) {
            writer.writeSignedVarLong(signed.value());
        } else if (value instanceof WireMarker.UnsignedLongValue unsigned) {
            writer.writeNonNegativeVarLong(unsigned.value());
        } else if (value instanceof WireMarker.DoubleValue number) {
            writer.writeDouble(number.value());
        } else if (value instanceof WireMarker.StringValue string) {
            writer.writeUtf8(string.value(), MAX_STATE_STRING_BYTES);
        } else if (value instanceof WireMarker.IdValue id) {
            WireValueCodec.writeNamespacedId(writer, id.value());
        } else if (value instanceof WireMarker.UuidValue uuid) {
            writer.writeUuid(uuid.value());
        } else if (value instanceof WireMarker.HashValue hash) {
            writer.writeHash(hash.value());
        } else {
            writer.writeByteArray(
                    ((WireMarker.BytesValue) value).valueBytes(),
                    MinimapHardLimits.MAX_MARKER_BYTES_VALUE
            );
        }
    }

    private static WireMarker.StateField readStateField(WireReader reader) {
        NamespacedId key = WireValueCodec.readNamespacedId(reader);
        int tag = reader.readUnsignedByte();
        WireMarker.StateValue value = switch (tag) {
            case 0 -> new WireMarker.BoolValue(reader.readBoolean());
            case 1 -> new WireMarker.SignedLongValue(reader.readSignedVarLong());
            case 2 -> new WireMarker.UnsignedLongValue(reader.readNonNegativeVarLong());
            case 3 -> new WireMarker.DoubleValue(reader.readDouble());
            case 4 -> new WireMarker.StringValue(reader.readUtf8(MAX_STATE_STRING_BYTES));
            case 5 -> new WireMarker.IdValue(WireValueCodec.readNamespacedId(reader));
            case 6 -> new WireMarker.UuidValue(reader.readUuid());
            case 7 -> new WireMarker.HashValue(reader.readHash());
            case 8 -> new WireMarker.BytesValue(reader.readByteArray(
                    MinimapHardLimits.MAX_MARKER_BYTES_VALUE
            ));
            default -> throw malformed("unknown marker state tag");
        };
        return new WireMarker.StateField(key, value);
    }

    private static MinimapWireError malformed(String message) {
        return new MinimapWireError(MinimapErrorCode.MALFORMED_MESSAGE, message);
    }
}
