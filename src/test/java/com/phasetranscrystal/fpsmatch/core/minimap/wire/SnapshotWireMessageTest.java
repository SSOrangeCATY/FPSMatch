package com.phasetranscrystal.fpsmatch.core.minimap.wire;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapMessageDirection;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import org.junit.jupiter.api.Test;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnapshotWireMessageTest {
    private static final HexFormat HEX = HexFormat.of();

    @Test
    void requestWorldSnapshotMatchesTypedGolden() {
        SnapshotWireMessage.RequestWorldSnapshot request =
                new SnapshotWireMessage.RequestWorldSnapshot(
                        uuid(16),
                        editorContext(),
                        uuid(14),
                        new Sha256("33".repeat(32)),
                        new WireSnapshot.SectionKey(-1, 0, 1),
                        new WireSnapshot.SectionKey(2, 3, 4),
                        true,
                        List.of(new WireSnapshot.RequestedChannel(
                                NamespacedId.parse("a:c"), 5
                        ))
                );

        byte[] frame = MinimapWireCodec.encode(request);

        assertEquals(195, frame.length);
        assertArrayEquals(HEX.parseHex(
                "010017be01"
                        + uuidHex(16)
                        + editorContextHex()
                        + uuidHex(14)
                        + "33".repeat(32)
                        + "010002040608"
                        + "0101"
                        + "0161016305"
        ), frame);
        assertEquals(
                request,
                MinimapWireCodec.decode(MinimapMessageDirection.C2S, frame)
        );
    }

    @Test
    void requestWorldSnapshotChecksSignedBoundsAndChannelLimit() {
        List<WireSnapshot.RequestedChannel> channels = IntStream.range(0, 32)
                .mapToObj(index -> new WireSnapshot.RequestedChannel(
                        NamespacedId.parse("a:c" + index), index
                ))
                .toList();
        SnapshotWireMessage.RequestWorldSnapshot extrema =
                new SnapshotWireMessage.RequestWorldSnapshot(
                        uuid(16),
                        editorContext(),
                        uuid(14),
                        new Sha256("33".repeat(32)),
                        new WireSnapshot.SectionKey(
                                Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE
                        ),
                        new WireSnapshot.SectionKey(
                                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE
                        ),
                        false,
                        channels
                );

        assertEquals(
                extrema,
                MinimapWireCodec.decode(
                        MinimapMessageDirection.C2S,
                        MinimapWireCodec.encode(extrema)
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                requestWithBounds(
                        new WireSnapshot.SectionKey(1, 0, 0),
                        new WireSnapshot.SectionKey(0, 0, 0),
                        List.of()
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                requestWithBounds(
                        new WireSnapshot.SectionKey(0, 1, 0),
                        new WireSnapshot.SectionKey(0, 0, 0),
                        List.of()
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                requestWithBounds(
                        new WireSnapshot.SectionKey(0, 0, 1),
                        new WireSnapshot.SectionKey(0, 0, 0),
                        List.of()
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                requestWithBounds(
                        new WireSnapshot.SectionKey(0, 0, 0),
                        new WireSnapshot.SectionKey(0, 0, 0),
                        IntStream.rangeClosed(0, 32)
                                .mapToObj(index -> new WireSnapshot.RequestedChannel(
                                        NamespacedId.parse("a:c" + index), index
                                ))
                                .toList()
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new WireSnapshot.RequestedChannel(NamespacedId.parse("a:c"), -1)
        );
    }

    @Test
    void requestDirtySectionsMatchesTypedGoldenAndBoundsCursorPageSize() {
        SnapshotWireMessage.RequestDirtySections request =
                new SnapshotWireMessage.RequestDirtySections(
                        uuid(17), editorContext(), 5, 6, 7
                );

        byte[] frame = MinimapWireCodec.encode(request);

        assertEquals(137, frame.length);
        assertArrayEquals(HEX.parseHex(
                "0100188401"
                        + uuidHex(17)
                        + editorContextHex()
                        + "050607"
        ), frame);
        assertEquals(
                request,
                MinimapWireCodec.decode(MinimapMessageDirection.C2S, frame)
        );
        SnapshotWireMessage.RequestDirtySections maximum =
                new SnapshotWireMessage.RequestDirtySections(
                        uuid(17), editorContext(), 0, 0, 4_096
                );
        assertEquals(
                maximum,
                MinimapWireCodec.decode(
                        MinimapMessageDirection.C2S,
                        MinimapWireCodec.encode(maximum)
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new SnapshotWireMessage.RequestDirtySections(
                        uuid(17), editorContext(), -1, 0, 1
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new SnapshotWireMessage.RequestDirtySections(
                        uuid(17), editorContext(), 0, -1, 1
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new SnapshotWireMessage.RequestDirtySections(
                        uuid(17), editorContext(), 0, 0, 0
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new SnapshotWireMessage.RequestDirtySections(
                        uuid(17), editorContext(), 0, 0, 4_097
                )
        );
    }

    @Test
    void worldSnapshotManifestMatchesTypedGolden() {
        WireSnapshot.ChannelDescriptor channel = new WireSnapshot.ChannelDescriptor(
                NamespacedId.parse("a:c"),
                5,
                3,
                new Sha256("44".repeat(32)),
                1
        );
        WireSnapshot.SectionDescriptor section = new WireSnapshot.SectionDescriptor(
                new WireSnapshot.SectionKey(-1, 0, 1),
                7,
                true,
                List.of(channel)
        );
        SnapshotWireMessage.WorldSnapshotManifest manifest =
                new SnapshotWireMessage.WorldSnapshotManifest(
                        uuid(18),
                        editorContext(),
                        5,
                        6,
                        new Sha256("33".repeat(32)),
                        0,
                        1,
                        List.of(section)
                );

        byte[] frame = MinimapWireCodec.encode(manifest);

        assertEquals(216, frame.length);
        assertArrayEquals(HEX.parseHex(
                "010055d301"
                        + uuidHex(18)
                        + editorContextHex()
                        + "0506"
                        + "33".repeat(32)
                        + "000101"
                        + "010002070101"
                        + "016101630503"
                        + "44".repeat(32)
                        + "01"
        ), frame);
        assertEquals(
                manifest,
                MinimapWireCodec.decode(MinimapMessageDirection.S2C, frame)
        );
    }

    @Test
    void worldSnapshotManifestChecksPageSectionAndChannelGeometry() {
        WireSnapshot.ChannelDescriptor channel = new WireSnapshot.ChannelDescriptor(
                NamespacedId.parse("a:c"),
                0,
                1,
                new Sha256("44".repeat(32)),
                1
        );
        List<WireSnapshot.ChannelDescriptor> channels = IntStream.range(0, 16)
                .mapToObj(index -> new WireSnapshot.ChannelDescriptor(
                        NamespacedId.parse("a:c" + index), 0, 1,
                        channel.objectHash(), 1
                ))
                .toList();
        List<WireSnapshot.SectionDescriptor> sections = IntStream.range(0, 32)
                .mapToObj(index -> new WireSnapshot.SectionDescriptor(
                        new WireSnapshot.SectionKey(index, 0, 0),
                        0, true, channels
                ))
                .toList();
        WireSnapshot.SectionDescriptor section = sections.get(0);
        SnapshotWireMessage.WorldSnapshotManifest maximum =
                new SnapshotWireMessage.WorldSnapshotManifest(
                        uuid(18), editorContext(), 0, 0,
                        new Sha256("33".repeat(32)),
                        4_095, 4_096,
                        sections
                );

        assertEquals(
                maximum,
                MinimapWireCodec.decode(
                        MinimapMessageDirection.S2C,
                        MinimapWireCodec.encode(maximum)
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new WireSnapshot.ChannelDescriptor(
                        channel.channelId(), -1, 1, channel.objectHash(), 1
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new WireSnapshot.ChannelDescriptor(
                        channel.channelId(), 0, 0, channel.objectHash(), 1
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new WireSnapshot.ChannelDescriptor(
                        channel.channelId(), 0,
                        MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES + 1L,
                        channel.objectHash(), 1
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new WireSnapshot.SectionDescriptor(
                        section.key(), -1, false, List.of()
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new WireSnapshot.SectionDescriptor(
                        section.key(), 0, false,
                        IntStream.range(0, 17)
                                .mapToObj(index -> new WireSnapshot.ChannelDescriptor(
                                        NamespacedId.parse("a:x" + index),
                                        0, 1, channel.objectHash(), 1
                                ))
                                .toList()
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new SnapshotWireMessage.WorldSnapshotManifest(
                        uuid(18), editorContext(), 0, 0,
                        new Sha256("33".repeat(32)),
                        0, 0, List.of()
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new SnapshotWireMessage.WorldSnapshotManifest(
                        uuid(18), editorContext(), 0, 0,
                        new Sha256("33".repeat(32)),
                        1, 1, List.of()
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new SnapshotWireMessage.WorldSnapshotManifest(
                        uuid(18), editorContext(), 0, 0,
                        new Sha256("33".repeat(32)),
                        0, 1,
                        java.util.Collections.nCopies(33, section)
                )
        );
    }

    @Test
    void worldSnapshotFragmentMatchesTypedGoldenAndBindsSectionChannelVersion() {
        Sha256 dataHash = dataHash();
        WireTransfer.TransferFragment transfer = new WireTransfer.TransferFragment(
                uuid(14),
                0,
                1,
                3,
                dataHash,
                dataHash,
                new byte[]{1, 2, 3}
        );
        SnapshotWireMessage.WorldSnapshotFragment fragment =
                new SnapshotWireMessage.WorldSnapshotFragment(
                        uuid(19),
                        editorContext(),
                        5,
                        new WireSnapshot.SectionKey(-1, 0, 1),
                        7,
                        NamespacedId.parse("a:c"),
                        5,
                        transfer
                );

        byte[] frame = MinimapWireCodec.encode(fragment);

        assertEquals(231, frame.length);
        assertArrayEquals(HEX.parseHex(
                "010056e201"
                        + uuidHex(19)
                        + editorContextHex()
                        + "05"
                        + "010002"
                        + "07"
                        + "0161016305"
                        + transferHex(dataHash)
        ), frame);
        assertEquals(
                fragment,
                MinimapWireCodec.decode(MinimapMessageDirection.S2C, frame)
        );
        assertThrows(IllegalArgumentException.class, () ->
                new SnapshotWireMessage.WorldSnapshotFragment(
                        fragment.requestId(), fragment.context(), -1,
                        fragment.section(), fragment.sectionRevision(),
                        fragment.channelId(), fragment.channelVersion(), fragment.transfer()
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new SnapshotWireMessage.WorldSnapshotFragment(
                        fragment.requestId(), fragment.context(), fragment.snapshotId(),
                        fragment.section(), -1,
                        fragment.channelId(), fragment.channelVersion(), fragment.transfer()
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new SnapshotWireMessage.WorldSnapshotFragment(
                        fragment.requestId(), fragment.context(), fragment.snapshotId(),
                        fragment.section(), fragment.sectionRevision(),
                        fragment.channelId(), -1, fragment.transfer()
                )
        );
    }

    @Test
    void dirtySectionsMatchesTypedGoldenAndBoundsPage() {
        WireSnapshot.DirtySection dirty = new WireSnapshot.DirtySection(
                new WireSnapshot.SectionKey(-1, 0, 1), 8
        );
        SnapshotWireMessage.DirtySections message =
                new SnapshotWireMessage.DirtySections(
                        uuid(20), editorContext(), 5, 6, 7, true, List.of(dirty)
                );

        byte[] frame = MinimapWireCodec.encode(message);

        assertEquals(143, frame.length);
        assertArrayEquals(HEX.parseHex(
                "0100578a01"
                        + uuidHex(20)
                        + editorContextHex()
                        + "0506070101"
                        + "01000208"
        ), frame);
        assertEquals(
                message,
                MinimapWireCodec.decode(MinimapMessageDirection.S2C, frame)
        );

        List<WireSnapshot.DirtySection> maximumSections = IntStream.range(0, 4_096)
                .mapToObj(index -> new WireSnapshot.DirtySection(
                        new WireSnapshot.SectionKey(
                                index == 0 ? Integer.MIN_VALUE : index,
                                Integer.MAX_VALUE,
                                Integer.MIN_VALUE
                        ),
                        Long.MAX_VALUE
                ))
                .toList();
        SnapshotWireMessage.DirtySections maximum =
                new SnapshotWireMessage.DirtySections(
                        uuid(20), editorContext(), 0, 0, Long.MAX_VALUE,
                        false,
                        maximumSections
                );
        assertEquals(
                maximum,
                MinimapWireCodec.decode(
                        MinimapMessageDirection.S2C,
                        MinimapWireCodec.encode(maximum)
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new WireSnapshot.DirtySection(dirty.section(), -1)
        );
        assertThrows(IllegalArgumentException.class, () ->
                new SnapshotWireMessage.DirtySections(
                        uuid(20), editorContext(), -1, 0, 0, false, List.of()
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new SnapshotWireMessage.DirtySections(
                        uuid(20), editorContext(), 0, -1, 0, false, List.of()
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new SnapshotWireMessage.DirtySections(
                        uuid(20), editorContext(), 0, 2, 1, false, List.of()
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new SnapshotWireMessage.DirtySections(
                        uuid(20), editorContext(), 0, 0, 0, false,
                        java.util.Collections.nCopies(4_097, dirty)
                )
        );
    }

    @Test
    void hostileSnapshotFramesRejectStructureBeforeCollectionAllocation() {
        SnapshotWireMessage.RequestWorldSnapshot request = requestWithBounds(
                new WireSnapshot.SectionKey(-1, 0, 1),
                new WireSnapshot.SectionKey(2, 3, 4),
                List.of(new WireSnapshot.RequestedChannel(
                        NamespacedId.parse("a:c"), 5
                ))
        );
        byte[] invalidBoundsAndOversizedChannels = MinimapWireCodec.encode(request);
        assertEquals(1, invalidBoundsAndOversizedChannels[182]);
        assertEquals(1, invalidBoundsAndOversizedChannels[189]);
        invalidBoundsAndOversizedChannels[182] = 6;
        invalidBoundsAndOversizedChannels[189] = 33;
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(
                        MinimapMessageDirection.C2S,
                        invalidBoundsAndOversizedChannels
                )
        );

        WireSnapshot.ChannelDescriptor channel = new WireSnapshot.ChannelDescriptor(
                NamespacedId.parse("a:c"), 5, 3,
                new Sha256("44".repeat(32)), 1
        );
        SnapshotWireMessage.WorldSnapshotManifest manifest =
                new SnapshotWireMessage.WorldSnapshotManifest(
                        uuid(18), editorContext(), 5, 6,
                        new Sha256("33".repeat(32)), 0, 1,
                        List.of(new WireSnapshot.SectionDescriptor(
                                new WireSnapshot.SectionKey(-1, 0, 1),
                                7, true, List.of(channel)
                        ))
                );
        byte[] invalidPageAndOversizedSections = MinimapWireCodec.encode(manifest);
        assertEquals(1, invalidPageAndOversizedSections[169]);
        assertEquals(1, invalidPageAndOversizedSections[170]);
        invalidPageAndOversizedSections[169] = 0;
        invalidPageAndOversizedSections[170] = 33;
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(
                        MinimapMessageDirection.S2C,
                        invalidPageAndOversizedSections
                )
        );

        byte[] oversizedTransferWithoutHashes = HEX.parseHex(
                "010056a101"
                        + uuidHex(19)
                        + editorContextHex()
                        + "05"
                        + "010002"
                        + "07"
                        + "0161016305"
                        + uuidHex(14)
                        + "0001"
                        + "81808040"
        );
        assertWireError(MinimapErrorCode.QUOTA_EXCEEDED, () ->
                MinimapWireCodec.decode(
                        MinimapMessageDirection.S2C,
                        oversizedTransferWithoutHashes
                )
        );
    }

    @Test
    void snapshotDirectionTrailingBytesAndOpaqueCarriersAreRejected() {
        SnapshotWireMessage.RequestDirtySections request =
                new SnapshotWireMessage.RequestDirtySections(
                        uuid(17), editorContext(), 0, 0, 1
                );
        byte[] frame = MinimapWireCodec.encode(request);
        assertWireError(MinimapErrorCode.WRONG_DIRECTION, () ->
                MinimapWireCodec.decode(MinimapMessageDirection.S2C, frame)
        );
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(
                        MinimapMessageDirection.C2S,
                        Arrays.copyOf(frame, frame.length + 1)
                )
        );

    }

    @Test
    void snapshotChannelAndManifestDeclaredBytesHaveIndependentCaps() {
        assertEquals(
                128L * 1024 * 1024,
                MinimapHardLimits.MAX_SNAPSHOT_CHANNEL_BYTES
        );
        assertEquals(
                512L * 1024 * 1024,
                MinimapHardLimits.MAX_SNAPSHOT_MANIFEST_DECLARED_BYTES
        );
        Sha256 objectHash = new Sha256("44".repeat(32));
        WireSnapshot.ChannelDescriptor maximumChannel =
                new WireSnapshot.ChannelDescriptor(
                        NamespacedId.parse("a:c"),
                        0,
                        MinimapHardLimits.MAX_SNAPSHOT_CHANNEL_BYTES,
                        objectHash,
                        fragmentCount(MinimapHardLimits.MAX_SNAPSHOT_CHANNEL_BYTES)
                );
        assertThrows(IllegalArgumentException.class, () ->
                new WireSnapshot.ChannelDescriptor(
                        NamespacedId.parse("a:c"),
                        0,
                        MinimapHardLimits.MAX_SNAPSHOT_CHANNEL_BYTES + 1,
                        objectHash,
                        fragmentCount(MinimapHardLimits.MAX_SNAPSHOT_CHANNEL_BYTES + 1)
                )
        );

        List<WireSnapshot.ChannelDescriptor> maximumChannels = IntStream.range(0, 4)
                .mapToObj(index -> new WireSnapshot.ChannelDescriptor(
                        NamespacedId.parse("a:c" + index),
                        0,
                        MinimapHardLimits.MAX_SNAPSHOT_CHANNEL_BYTES,
                        objectHash,
                        fragmentCount(MinimapHardLimits.MAX_SNAPSHOT_CHANNEL_BYTES)
                ))
                .toList();
        WireSnapshot.SectionDescriptor exactSection =
                new WireSnapshot.SectionDescriptor(
                        new WireSnapshot.SectionKey(0, 0, 0),
                        0,
                        false,
                        maximumChannels
                );
        SnapshotWireMessage.WorldSnapshotManifest exactPage =
                new SnapshotWireMessage.WorldSnapshotManifest(
                        uuid(18), editorContext(), 0, 0,
                        new Sha256("33".repeat(32)),
                        0, 1, List.of(exactSection)
                );
        assertEquals(
                exactPage,
                MinimapWireCodec.decode(
                        MinimapMessageDirection.S2C,
                        MinimapWireCodec.encode(exactPage)
                )
        );

        WireSnapshot.ChannelDescriptor oneByte = new WireSnapshot.ChannelDescriptor(
                NamespacedId.parse("a:d"), 0, 1, objectHash, 1
        );
        WireSnapshot.SectionDescriptor oversizedSection =
                new WireSnapshot.SectionDescriptor(
                        new WireSnapshot.SectionKey(0, 0, 0),
                        0,
                        false,
                        java.util.stream.Stream.concat(
                                maximumChannels.stream(),
                                java.util.stream.Stream.of(oneByte)
                        ).toList()
                );
        assertThrows(IllegalArgumentException.class, () ->
                new SnapshotWireMessage.WorldSnapshotManifest(
                        uuid(18), editorContext(), 0, 0,
                        new Sha256("33".repeat(32)),
                        0, 1, List.of(oversizedSection)
                )
        );

        WireTransfer.TransferFragment oversizedTransfer = firstFragment(
                MinimapHardLimits.MAX_SNAPSHOT_CHANNEL_BYTES + 1,
                objectHash
        );
        assertThrows(IllegalArgumentException.class, () ->
                new SnapshotWireMessage.WorldSnapshotFragment(
                        uuid(19), editorContext(), 0,
                        new WireSnapshot.SectionKey(0, 0, 0),
                        0, NamespacedId.parse("a:c"), 0, oversizedTransfer
                )
        );
    }

    @Test
    void dirtyPageWithMoreMustAdvanceCursorBeforeReadingItsCount() {
        assertThrows(IllegalArgumentException.class, () ->
                new SnapshotWireMessage.DirtySections(
                        uuid(20), editorContext(), 0, 6, 6,
                        true, List.of()
                )
        );

        byte[] stalledCursorAndOversizedCount = HEX.parseHex(
                "0100578701"
                        + uuidHex(20)
                        + editorContextHex()
                        + "05060601"
                        + "8120"
        );
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(
                        MinimapMessageDirection.S2C,
                        stalledCursorAndOversizedCount
                )
        );
    }

    @Test
    void snapshotListsUseBoundedIterationInsteadOfReportedSize() {
        List<WireSnapshot.RequestedChannel> requested = IntStream.range(0, 33)
                .mapToObj(index -> new WireSnapshot.RequestedChannel(
                        NamespacedId.parse("a:c" + index), 0
                ))
                .toList();
        assertThrows(IllegalArgumentException.class, () ->
                requestWithBounds(
                        new WireSnapshot.SectionKey(0, 0, 0),
                        new WireSnapshot.SectionKey(0, 0, 0),
                        misreportedList(requested)
                )
        );

        Sha256 hash = new Sha256("44".repeat(32));
        List<WireSnapshot.ChannelDescriptor> channels = IntStream.range(0, 17)
                .mapToObj(index -> new WireSnapshot.ChannelDescriptor(
                        NamespacedId.parse("a:c" + index), 0, 1, hash, 1
                ))
                .toList();
        assertThrows(IllegalArgumentException.class, () ->
                new WireSnapshot.SectionDescriptor(
                        new WireSnapshot.SectionKey(0, 0, 0),
                        0, false, misreportedList(channels)
                )
        );

        WireSnapshot.SectionDescriptor section = new WireSnapshot.SectionDescriptor(
                new WireSnapshot.SectionKey(0, 0, 0),
                0, false, List.of(channels.get(0))
        );
        List<WireSnapshot.SectionDescriptor> sections = IntStream.range(0, 33)
                .mapToObj(index -> new WireSnapshot.SectionDescriptor(
                        new WireSnapshot.SectionKey(index, 0, 0),
                        0, false, section.channels()
                ))
                .toList();
        assertThrows(IllegalArgumentException.class, () ->
                new SnapshotWireMessage.WorldSnapshotManifest(
                        uuid(18), editorContext(), 0, 0,
                        new Sha256("33".repeat(32)),
                        0, 1, misreportedList(sections)
                )
        );

        List<WireSnapshot.DirtySection> dirty = IntStream.range(0, 4_097)
                .mapToObj(index -> new WireSnapshot.DirtySection(
                        new WireSnapshot.SectionKey(index, 0, 0), 0
                ))
                .toList();
        assertThrows(IllegalArgumentException.class, () ->
                new SnapshotWireMessage.DirtySections(
                        uuid(20), editorContext(), 0, 0, 0,
                        false, misreportedList(dirty)
                )
        );
    }

    @Test
    void zeroSnapshotDescriptorLengthIsMalformedBeforeReadingHash() {
        byte[] zeroLengthWithoutHash = HEX.parseHex(
                "010055b201"
                        + uuidHex(18)
                        + editorContextHex()
                        + "0506"
                        + "33".repeat(32)
                        + "000101"
                        + "010002070101"
                        + "016101630500"
        );
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(
                        MinimapMessageDirection.S2C,
                        zeroLengthWithoutHash
                )
        );
    }

    @Test
    void sharedTransferCodecRejectsMetadataBeyond32KiBBeforeTransferWork() {
        WireTransfer.TransferFragment transfer = new WireTransfer.TransferFragment(
                uuid(14), 0, 1, 3, dataHash(), dataHash(), new byte[]{1, 2, 3}
        );
        WireWriter exact = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        exact.writeByteArray(
                new byte[MinimapHardLimits.MAX_WIRE_FRAGMENT_METADATA_BYTES - 87],
                MinimapHardLimits.MAX_WIRE_FRAGMENT_METADATA_BYTES
        );
        WireValueCodec.writeTransfer(exact, transfer);

        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeByteArray(
                new byte[MinimapHardLimits.MAX_WIRE_FRAGMENT_METADATA_BYTES - 86],
                MinimapHardLimits.MAX_WIRE_FRAGMENT_METADATA_BYTES
        );
        assertWireError(MinimapErrorCode.QUOTA_EXCEEDED, () ->
                WireValueCodec.writeTransfer(writer, transfer)
        );

        WireReader reader = new WireReader(writer.toByteArray());
        reader.readByteArray(MinimapHardLimits.MAX_WIRE_FRAGMENT_METADATA_BYTES);
        assertWireError(MinimapErrorCode.QUOTA_EXCEEDED, () ->
                WireValueCodec.readTransfer(
                        reader, MinimapHardLimits.MAX_SNAPSHOT_CHANNEL_BYTES
                )
        );
    }

    @Test
    void manifestAggregateLimitRejectsBeforeOverflowingChannelHash() {
        Sha256 hash = new Sha256("44".repeat(32));
        List<WireSnapshot.ChannelDescriptor> channels = IntStream.range(0, 4)
                .mapToObj(index -> new WireSnapshot.ChannelDescriptor(
                        NamespacedId.parse("a:c" + index),
                        0,
                        MinimapHardLimits.MAX_SNAPSHOT_CHANNEL_BYTES,
                        hash,
                        fragmentCount(MinimapHardLimits.MAX_SNAPSHOT_CHANNEL_BYTES)
                ))
                .toList();
        SnapshotWireMessage.WorldSnapshotManifest exact =
                new SnapshotWireMessage.WorldSnapshotManifest(
                        uuid(18), editorContext(), 0, 0,
                        new Sha256("33".repeat(32)),
                        0, 1,
                        List.of(new WireSnapshot.SectionDescriptor(
                                new WireSnapshot.SectionKey(0, 0, 0),
                                0, false, channels
                        ))
                );
        byte[] frame = MinimapWireCodec.encode(exact);
        assertEquals(4, frame[176]);
        byte[] hostile = Arrays.copyOf(frame, frame.length + 6);
        hostile[3] = (byte) 0xe2;
        hostile[4] = 0x02;
        hostile[176] = 5;
        byte[] overflowingPrefix = HEX.parseHex("016101780001");
        System.arraycopy(
                overflowingPrefix, 0, hostile, frame.length,
                overflowingPrefix.length
        );

        assertWireError(MinimapErrorCode.QUOTA_EXCEEDED, () ->
                MinimapWireCodec.decode(MinimapMessageDirection.S2C, hostile)
        );
    }

    @Test
    void snapshotIdentityListsRejectDuplicatesWithoutSorting() {
        WireSnapshot.RequestedChannel first = new WireSnapshot.RequestedChannel(
                NamespacedId.parse("a:z"), 0
        );
        WireSnapshot.RequestedChannel second = new WireSnapshot.RequestedChannel(
                NamespacedId.parse("a:a"), 0
        );
        SnapshotWireMessage.RequestWorldSnapshot ordered = requestWithBounds(
                new WireSnapshot.SectionKey(0, 0, 0),
                new WireSnapshot.SectionKey(0, 0, 0),
                List.of(first, second)
        );
        assertEquals(List.of(first, second), ordered.channels());
        assertEquals(
                ordered,
                MinimapWireCodec.decode(
                        MinimapMessageDirection.C2S,
                        MinimapWireCodec.encode(ordered)
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                requestWithBounds(
                        ordered.minimum(), ordered.maximum(),
                        List.of(first, new WireSnapshot.RequestedChannel(
                                first.channelId(), 1
                        ))
                )
        );

        Sha256 hash = new Sha256("44".repeat(32));
        WireSnapshot.ChannelDescriptor channel = new WireSnapshot.ChannelDescriptor(
                NamespacedId.parse("a:c"), 0, 1, hash, 1
        );
        assertThrows(IllegalArgumentException.class, () ->
                new WireSnapshot.SectionDescriptor(
                        new WireSnapshot.SectionKey(0, 0, 0),
                        0, false,
                        List.of(channel, new WireSnapshot.ChannelDescriptor(
                                channel.channelId(), 1, 1, hash, 1
                        ))
                )
        );

        WireSnapshot.SectionDescriptor section = new WireSnapshot.SectionDescriptor(
                new WireSnapshot.SectionKey(0, 0, 0),
                0, false, List.of(channel)
        );
        assertThrows(IllegalArgumentException.class, () ->
                new SnapshotWireMessage.WorldSnapshotManifest(
                        uuid(18), editorContext(), 0, 0,
                        new Sha256("33".repeat(32)),
                        0, 1,
                        List.of(section, new WireSnapshot.SectionDescriptor(
                                section.key(), 1, true, List.of(channel)
                        ))
                )
        );

        WireSnapshot.DirtySection dirty = new WireSnapshot.DirtySection(
                section.key(), 1
        );
        assertThrows(IllegalArgumentException.class, () ->
                new SnapshotWireMessage.DirtySections(
                        uuid(20), editorContext(), 0, 0, 0,
                        false,
                        List.of(dirty, new WireSnapshot.DirtySection(
                                dirty.section(), 2
                        ))
                )
        );
    }

    @Test
    void hostileSnapshotFramesRejectEveryDuplicateIdentity() {
        SnapshotWireMessage.RequestWorldSnapshot request = requestWithBounds(
                new WireSnapshot.SectionKey(0, 0, 0),
                new WireSnapshot.SectionKey(0, 0, 0),
                List.of(
                        new WireSnapshot.RequestedChannel(
                                NamespacedId.parse("a:z"), 0
                        ),
                        new WireSnapshot.RequestedChannel(
                                NamespacedId.parse("a:a"), 0
                        )
                )
        );
        byte[] duplicateRequestedChannel = MinimapWireCodec.encode(request);
        assertEquals('a', duplicateRequestedChannel[198]);
        duplicateRequestedChannel[198] = 'z';
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(
                        MinimapMessageDirection.C2S, duplicateRequestedChannel
                )
        );

        Sha256 hash = new Sha256("44".repeat(32));
        WireSnapshot.ChannelDescriptor channelZ = new WireSnapshot.ChannelDescriptor(
                NamespacedId.parse("a:z"), 0, 1, hash, 1
        );
        WireSnapshot.ChannelDescriptor channelA = new WireSnapshot.ChannelDescriptor(
                NamespacedId.parse("a:a"), 0, 1, hash, 1
        );
        SnapshotWireMessage.WorldSnapshotManifest channelManifest =
                new SnapshotWireMessage.WorldSnapshotManifest(
                        uuid(18), editorContext(), 0, 0,
                        new Sha256("33".repeat(32)),
                        0, 1,
                        List.of(new WireSnapshot.SectionDescriptor(
                                new WireSnapshot.SectionKey(0, 0, 0),
                                0, false, List.of(channelZ, channelA)
                        ))
                );
        byte[] duplicateDescriptorChannel = MinimapWireCodec.encode(channelManifest);
        assertEquals('a', duplicateDescriptorChannel[219]);
        duplicateDescriptorChannel[219] = 'z';
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(
                        MinimapMessageDirection.S2C, duplicateDescriptorChannel
                )
        );

        WireSnapshot.SectionDescriptor section0 = new WireSnapshot.SectionDescriptor(
                new WireSnapshot.SectionKey(0, 0, 0), 0, false, List.of()
        );
        WireSnapshot.SectionDescriptor section1 = new WireSnapshot.SectionDescriptor(
                new WireSnapshot.SectionKey(1, 0, 0), 0, false, List.of()
        );
        SnapshotWireMessage.WorldSnapshotManifest sectionManifest =
                new SnapshotWireMessage.WorldSnapshotManifest(
                        uuid(18), editorContext(), 0, 0,
                        new Sha256("33".repeat(32)),
                        0, 1, List.of(section0, section1)
                );
        byte[] duplicateSection = MinimapWireCodec.encode(sectionManifest);
        assertEquals(2, duplicateSection[177]);
        duplicateSection[177] = 0;
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(
                        MinimapMessageDirection.S2C, duplicateSection
                )
        );

        SnapshotWireMessage.DirtySections dirty = new SnapshotWireMessage.DirtySections(
                uuid(20), editorContext(), 0, 0, 0, false,
                List.of(
                        new WireSnapshot.DirtySection(
                                new WireSnapshot.SectionKey(0, 0, 0), 0
                        ),
                        new WireSnapshot.DirtySection(
                                new WireSnapshot.SectionKey(1, 0, 0), 0
                        )
                )
        );
        byte[] duplicateDirtySection = MinimapWireCodec.encode(dirty);
        assertEquals(2, duplicateDirtySection[143]);
        duplicateDirtySection[143] = 0;
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(
                        MinimapMessageDirection.S2C, duplicateDirtySection
                )
        );
    }

    private static SnapshotWireMessage.RequestWorldSnapshot requestWithBounds(
            WireSnapshot.SectionKey minimum,
            WireSnapshot.SectionKey maximum,
            List<WireSnapshot.RequestedChannel> channels
    ) {
        return new SnapshotWireMessage.RequestWorldSnapshot(
                uuid(16), editorContext(), uuid(14), new Sha256("33".repeat(32)),
                minimum, maximum, false, channels
        );
    }

    private static WireIdentity.EditorContext editorContext() {
        return new WireIdentity.EditorContext(
                new WireIdentity.ScopeLease(WireIdentity.Scope.EDITOR, 1, 2),
                new WireIdentity.DocumentBinding(
                        new WireIdentity.MapTarget(
                                new MapKey("g", "m"),
                                NamespacedId.parse("a:d")
                        ),
                        NamespacedId.parse("a:o")
                ),
                uuid(12),
                uuid(13),
                3,
                new Sha256("11".repeat(32)),
                new Sha256("22".repeat(32)),
                4
        );
    }

    private static Sha256 dataHash() {
        return new Sha256(
                "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81"
        );
    }

    private static String transferHex(Sha256 hash) {
        return uuidHex(14)
                + "000103"
                + hash.value()
                + hash.value()
                + "03010203";
    }

    private static WireTransfer.TransferFragment firstFragment(
            long totalLength,
            Sha256 objectHash
    ) {
        byte[] bytes = new byte[MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES];
        return new WireTransfer.TransferFragment(
                uuid(14),
                0,
                fragmentCount(totalLength),
                totalLength,
                objectHash,
                com.phasetranscrystal.fpsmatch.core.minimap.format
                        .Sha256Digest.of(bytes),
                bytes
        );
    }

    private static int fragmentCount(long totalLength) {
        return Math.toIntExact(
                (totalLength - 1L) / MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES + 1L
        );
    }

    private static <T> List<T> misreportedList(List<T> actual) {
        return new AbstractList<>() {
            @Override
            public T get(int index) {
                throw new IndexOutOfBoundsException(index);
            }

            @Override
            public int size() {
                return 0;
            }

            @Override
            public Iterator<T> iterator() {
                return actual.iterator();
            }
        };
    }

    private static void assertWireError(MinimapErrorCode expected, Runnable action) {
        MinimapWireError error = assertThrows(MinimapWireError.class, action::run);
        assertEquals(expected, error.code());
    }

    private static String editorContextHex() {
        return "020102"
                + "0167016d016101640161016f"
                + uuidHex(12)
                + uuidHex(13)
                + "03"
                + "11".repeat(32)
                + "22".repeat(32)
                + "04";
    }

    private static UUID uuid(long lowBits) {
        return new UUID(0, lowBits);
    }

    private static String uuidHex(long lowBits) {
        return "%032x".formatted(lowBits);
    }
}
