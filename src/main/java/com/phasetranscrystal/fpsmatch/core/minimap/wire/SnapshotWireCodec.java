package com.phasetranscrystal.fpsmatch.core.minimap.wire;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;

import java.util.ArrayList;
import java.util.List;

final class SnapshotWireCodec {
    private SnapshotWireCodec() {
    }

    static byte[] encodeRequestWorldSnapshot(
            SnapshotWireMessage.RequestWorldSnapshot message
    ) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeEditorContext(writer, message.context());
        writer.writeUuid(message.snapshotRequestId());
        writer.writeHash(message.registryDigest());
        writeSectionKey(writer, message.minimum());
        writeSectionKey(writer, message.maximum());
        writer.writeBoolean(message.temporaryLoad());
        writer.writeUnsignedVarInt(message.channels().size());
        for (WireSnapshot.RequestedChannel channel : message.channels()) {
            WireValueCodec.writeNamespacedId(writer, channel.channelId());
            writer.writeUnsignedVarInt(channel.channelVersion());
        }
        return writer.toByteArray();
    }

    static SnapshotWireMessage.RequestWorldSnapshot decodeRequestWorldSnapshot(
            WireReader reader
    ) {
        var requestId = reader.readUuid();
        WireIdentity.EditorContext context = WireValueCodec.readEditorContext(reader);
        var snapshotRequestId = reader.readUuid();
        var registryDigest = reader.readHash();
        WireSnapshot.SectionKey minimum = readSectionKey(reader);
        WireSnapshot.SectionKey maximum = readSectionKey(reader);
        requireOrderedBounds(minimum, maximum);
        boolean temporaryLoad = reader.readBoolean();
        int count = reader.readCount(MinimapHardLimits.MAX_SNAPSHOT_REQUEST_CHANNELS);
        List<WireSnapshot.RequestedChannel> channels = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            channels.add(new WireSnapshot.RequestedChannel(
                    WireValueCodec.readNamespacedId(reader),
                    reader.readUnsignedVarInt(Integer.MAX_VALUE)
            ));
        }
        SnapshotWireMessage.RequestWorldSnapshot result =
                new SnapshotWireMessage.RequestWorldSnapshot(
                        requestId,
                        context,
                        snapshotRequestId,
                        registryDigest,
                        minimum,
                        maximum,
                        temporaryLoad,
                        channels
                );
        reader.requireFinished();
        return result;
    }

    static byte[] encodeRequestDirtySections(
            SnapshotWireMessage.RequestDirtySections message
    ) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeEditorContext(writer, message.context());
        writer.writeNonNegativeVarLong(message.sinceWorldEpoch());
        writer.writeNonNegativeVarLong(message.cursor());
        writer.writeUnsignedVarInt(message.maxResults());
        return writer.toByteArray();
    }

    static SnapshotWireMessage.RequestDirtySections decodeRequestDirtySections(
            WireReader reader
    ) {
        SnapshotWireMessage.RequestDirtySections result =
                new SnapshotWireMessage.RequestDirtySections(
                        reader.readUuid(),
                        WireValueCodec.readEditorContext(reader),
                        reader.readNonNegativeVarLong(),
                        reader.readNonNegativeVarLong(),
                        reader.readUnsignedVarInt(
                                MinimapHardLimits.MAX_DIRTY_SECTION_RESULTS
                        )
                );
        reader.requireFinished();
        return result;
    }

    static byte[] encodeWorldSnapshotManifest(
            SnapshotWireMessage.WorldSnapshotManifest message
    ) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeEditorContext(writer, message.context());
        writer.writeNonNegativeVarLong(message.snapshotId());
        writer.writeNonNegativeVarLong(message.worldEpoch());
        writer.writeHash(message.registryDigest());
        writer.writeUnsignedVarInt(message.pageIndex());
        writer.writeUnsignedVarInt(message.pageCount());
        writer.writeUnsignedVarInt(message.sections().size());
        for (WireSnapshot.SectionDescriptor section : message.sections()) {
            writeSectionDescriptor(writer, section);
        }
        return writer.toByteArray();
    }

    static SnapshotWireMessage.WorldSnapshotManifest decodeWorldSnapshotManifest(
            WireReader reader
    ) {
        var requestId = reader.readUuid();
        WireIdentity.EditorContext context = WireValueCodec.readEditorContext(reader);
        long snapshotId = reader.readNonNegativeVarLong();
        long worldEpoch = reader.readNonNegativeVarLong();
        var registryDigest = reader.readHash();
        int pageIndex = reader.readUnsignedVarInt(MinimapHardLimits.MAX_WIRE_PAGE_COUNT);
        int pageCount = reader.readCount(MinimapHardLimits.MAX_WIRE_PAGE_COUNT);
        requirePage(pageIndex, pageCount);
        int sectionCount = reader.readCount(
                MinimapHardLimits.MAX_SNAPSHOT_SECTIONS_PER_PAGE
        );
        List<WireSnapshot.SectionDescriptor> sections = new ArrayList<>(sectionCount);
        DeclaredByteCounter declaredBytes = new DeclaredByteCounter();
        for (int index = 0; index < sectionCount; index++) {
            sections.add(readSectionDescriptor(reader, declaredBytes));
        }
        SnapshotWireMessage.WorldSnapshotManifest result =
                new SnapshotWireMessage.WorldSnapshotManifest(
                        requestId,
                        context,
                        snapshotId,
                        worldEpoch,
                        registryDigest,
                        pageIndex,
                        pageCount,
                        sections
                );
        reader.requireFinished();
        return result;
    }

    static byte[] encodeWorldSnapshotFragment(
            SnapshotWireMessage.WorldSnapshotFragment message
    ) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeEditorContext(writer, message.context());
        writer.writeNonNegativeVarLong(message.snapshotId());
        writeSectionKey(writer, message.section());
        writer.writeNonNegativeVarLong(message.sectionRevision());
        WireValueCodec.writeNamespacedId(writer, message.channelId());
        writer.writeUnsignedVarInt(message.channelVersion());
        WireValueCodec.writeTransfer(writer, message.transfer());
        return writer.toByteArray();
    }

    static SnapshotWireMessage.WorldSnapshotFragment decodeWorldSnapshotFragment(
            WireReader reader
    ) {
        SnapshotWireMessage.WorldSnapshotFragment result =
                new SnapshotWireMessage.WorldSnapshotFragment(
                        reader.readUuid(),
                        WireValueCodec.readEditorContext(reader),
                        reader.readNonNegativeVarLong(),
                        readSectionKey(reader),
                        reader.readNonNegativeVarLong(),
                        WireValueCodec.readNamespacedId(reader),
                        reader.readUnsignedVarInt(Integer.MAX_VALUE),
                        WireValueCodec.readTransfer(
                                reader, MinimapHardLimits.MAX_SNAPSHOT_CHANNEL_BYTES
                        )
                );
        reader.requireFinished();
        return result;
    }

    static byte[] encodeDirtySections(SnapshotWireMessage.DirtySections message) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(message.requestId());
        WireValueCodec.writeEditorContext(writer, message.context());
        writer.writeNonNegativeVarLong(message.worldEpoch());
        writer.writeNonNegativeVarLong(message.cursor());
        writer.writeNonNegativeVarLong(message.nextCursor());
        writer.writeBoolean(message.hasMore());
        writer.writeUnsignedVarInt(message.sections().size());
        for (WireSnapshot.DirtySection section : message.sections()) {
            writeSectionKey(writer, section.section());
            writer.writeNonNegativeVarLong(section.sectionRevision());
        }
        return writer.toByteArray();
    }

    static SnapshotWireMessage.DirtySections decodeDirtySections(WireReader reader) {
        var requestId = reader.readUuid();
        WireIdentity.EditorContext context = WireValueCodec.readEditorContext(reader);
        long worldEpoch = reader.readNonNegativeVarLong();
        long cursor = reader.readNonNegativeVarLong();
        long nextCursor = reader.readNonNegativeVarLong();
        boolean hasMore = reader.readBoolean();
        if (nextCursor < cursor || hasMore && nextCursor == cursor) {
            throw malformed("Dirty-section cursors are not ordered");
        }
        int count = reader.readCount(MinimapHardLimits.MAX_DIRTY_SECTION_RESULTS);
        List<WireSnapshot.DirtySection> sections = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            sections.add(new WireSnapshot.DirtySection(
                    readSectionKey(reader), reader.readNonNegativeVarLong()
            ));
        }
        SnapshotWireMessage.DirtySections result =
                new SnapshotWireMessage.DirtySections(
                        requestId,
                        context,
                        worldEpoch,
                        cursor,
                        nextCursor,
                        hasMore,
                        sections
                );
        reader.requireFinished();
        return result;
    }

    private static void writeSectionDescriptor(
            WireWriter writer,
            WireSnapshot.SectionDescriptor section
    ) {
        writeSectionKey(writer, section.key());
        writer.writeNonNegativeVarLong(section.sectionRevision());
        writer.writeBoolean(section.stale());
        writer.writeUnsignedVarInt(section.channels().size());
        for (WireSnapshot.ChannelDescriptor channel : section.channels()) {
            WireValueCodec.writeNamespacedId(writer, channel.channelId());
            writer.writeUnsignedVarInt(channel.channelVersion());
            writer.writeNonNegativeVarLong(channel.totalLength());
            writer.writeHash(channel.objectHash());
            writer.writeUnsignedVarInt(channel.fragmentCount());
        }
    }

    private static WireSnapshot.SectionDescriptor readSectionDescriptor(
            WireReader reader,
            DeclaredByteCounter declaredBytes
    ) {
        WireSnapshot.SectionKey key = readSectionKey(reader);
        long sectionRevision = reader.readNonNegativeVarLong();
        boolean stale = reader.readBoolean();
        int channelCount = reader.readCount(
                MinimapHardLimits.MAX_SNAPSHOT_CHANNELS_PER_SECTION
        );
        List<WireSnapshot.ChannelDescriptor> channels = new ArrayList<>(channelCount);
        for (int index = 0; index < channelCount; index++) {
            channels.add(readChannelDescriptor(reader, declaredBytes));
        }
        return new WireSnapshot.SectionDescriptor(
                key, sectionRevision, stale, channels
        );
    }

    private static WireSnapshot.ChannelDescriptor readChannelDescriptor(
            WireReader reader,
            DeclaredByteCounter declaredBytes
    ) {
        var channelId = WireValueCodec.readNamespacedId(reader);
        int channelVersion = reader.readUnsignedVarInt(Integer.MAX_VALUE);
        long totalLength = reader.readNonNegativeVarLong();
        if (totalLength <= 0) {
            throw malformed("Snapshot channel length must be positive");
        }
        if (totalLength > MinimapHardLimits.MAX_SNAPSHOT_CHANNEL_BYTES) {
            throw new MinimapWireError(
                    com.phasetranscrystal.fpsmatch.core.minimap.contract
                            .MinimapErrorCode.QUOTA_EXCEEDED,
                    "Snapshot channel length exceeds its hard limit"
            );
        }
        declaredBytes.add(totalLength);
        var objectHash = reader.readHash();
        int fragmentCount = reader.readCount(MinimapHardLimits.MAX_WIRE_PAGE_COUNT);
        return new WireSnapshot.ChannelDescriptor(
                channelId,
                channelVersion,
                totalLength,
                objectHash,
                fragmentCount
        );
    }

    static void writeSectionKey(WireWriter writer, WireSnapshot.SectionKey key) {
        writer.writeSignedVarInt(key.sectionX());
        writer.writeSignedVarInt(key.sectionY());
        writer.writeSignedVarInt(key.sectionZ());
    }

    static WireSnapshot.SectionKey readSectionKey(WireReader reader) {
        return new WireSnapshot.SectionKey(
                reader.readSignedVarInt(),
                reader.readSignedVarInt(),
                reader.readSignedVarInt()
        );
    }

    private static void requireOrderedBounds(
            WireSnapshot.SectionKey minimum,
            WireSnapshot.SectionKey maximum
    ) {
        if (minimum.sectionX() > maximum.sectionX()
                || minimum.sectionY() > maximum.sectionY()
                || minimum.sectionZ() > maximum.sectionZ()) {
            throw malformed("Snapshot section bounds are not ordered");
        }
    }

    private static void requirePage(int pageIndex, int pageCount) {
        if (pageCount <= 0 || pageIndex < 0 || pageIndex >= pageCount) {
            throw malformed("Snapshot manifest page coordinates are invalid");
        }
    }

    private static long checkedDeclaredBytes(long current, long additional) {
        long maximum = MinimapHardLimits.MAX_SNAPSHOT_MANIFEST_DECLARED_BYTES;
        if (additional < 0 || current > maximum - additional) {
            throw new MinimapWireError(
                    com.phasetranscrystal.fpsmatch.core.minimap.contract
                            .MinimapErrorCode.QUOTA_EXCEEDED,
                    "Snapshot manifest declared bytes exceed their limit"
            );
        }
        return current + additional;
    }

    private static final class DeclaredByteCounter {
        private long value;

        private void add(long additional) {
            value = checkedDeclaredBytes(value, additional);
        }
    }

    private static MinimapWireError malformed(String message) {
        return new MinimapWireError(
                com.phasetranscrystal.fpsmatch.core.minimap.contract
                        .MinimapErrorCode.MALFORMED_MESSAGE,
                message
        );
    }
}
