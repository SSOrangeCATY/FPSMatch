package com.phasetranscrystal.fpsmatch.common.packet.minimap;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapOpcode;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireCodec;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireError;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireGoldenFixtures;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.EditorWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.PublishWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireEditor;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireTransfer;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapEnvelopeCodecTest {

    @Test
    void c2sEnvelopeWritesTheExactStableWireBody() {
        MinimapWireMessage message = reservePublish();
        byte[] expected = MinimapWireCodec.encode(message);
        List<MinimapC2SPacket> packets = MinimapC2SPacket.fromMessage(
                new UUID(0, 10), message
        );
        ByteArrayOutputStream frame = new ByteArrayOutputStream(expected.length);
        packets.forEach(packet -> frame.writeBytes(packet.segment().segmentData()));

        assertArrayEquals(expected, frame.toByteArray());
    }

    @Test
    void s2cEnvelopeWritesTheExactStableWireBody() {
        MinimapWireMessage message = publishReservation();
        byte[] expected = MinimapWireCodec.encode(message);
        MinimapS2CPacket packet = MinimapS2CPacket.fromMessage(
                new UUID(0, 11), message
        );

        assertArrayEquals(expected, packet.segment().segmentData());
    }

    @Test
    void markerS2cEnvelopeDefersItsCanonicalFrameUntilForgeEncoding()
            throws ReflectiveOperationException {
        MinimapWireMessage message = MinimapWireGoldenFixtures.message(
                MinimapOpcode.S2C_MARKER_DELTA
        );
        byte[] expected = MinimapWireCodec.encode(message);
        MinimapS2CPacket packet = MinimapS2CPacket.fromMessage(
                new UUID(0, 111), message
        );
        Field segment = MinimapS2CPacket.class.getDeclaredField("segment");
        segment.setAccessible(true);

        assertNull(segment.get(packet));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        MinimapS2CPacket.encode(packet, buffer);
        MinimapFrameSegment decoded = MinimapS2CPacket.decode(buffer).segment();

        assertArrayEquals(expected, decoded.segmentData());
        assertEquals(Sha256Digest.of(expected), decoded.frameHash());
        buffer.release();
    }

    @Test
    void forgeEnvelopeCodecRoundTripsOneImmutableSegment() {
        MinimapC2SPacket c2s = MinimapC2SPacket.fromMessage(
                new UUID(0, 12), reservePublish()
        ).get(0);
        FriendlyByteBuf c2sBuffer = new FriendlyByteBuf(Unpooled.buffer());
        MinimapC2SPacket.encode(c2s, c2sBuffer);
        assertEquals(c2s.segment(), MinimapC2SPacket.decode(c2sBuffer).segment());
        c2sBuffer.release();

        MinimapS2CPacket s2c = MinimapS2CPacket.fromMessage(
                new UUID(0, 13), publishReservation()
        );
        FriendlyByteBuf s2cBuffer = new FriendlyByteBuf(Unpooled.buffer());
        MinimapS2CPacket.encode(s2c, s2cBuffer);
        assertEquals(s2c.segment(), MinimapS2CPacket.decode(s2cBuffer).segment());
        s2cBuffer.release();
    }

    @Test
    void maximumBusinessFragmentProducesForgeSafeC2SPackets() {
        byte[] data = new byte[MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES];
        Sha256 dataHash = Sha256Digest.of(data);
        EditorWireMessage.UploadFragment message = new EditorWireMessage.UploadFragment(
                new UUID(0, 14),
                editorContext(),
                new WireEditor.UploadData(new WireTransfer.TransferFragment(
                        new UUID(0, 15), 0, 1, data.length,
                        dataHash, dataHash, data
                ))
        );
        List<MinimapC2SPacket> packets = MinimapC2SPacket.fromMessage(
                new UUID(0, 16), message
        );

        assertTrue(packets.size() > 1);
        for (MinimapC2SPacket packet : packets) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            MinimapC2SPacket.encode(packet, buffer);
            assertTrue(buffer.readableBytes() + 5 < 32_767);
            buffer.release();
        }
    }

    @Test
    void decoderRejectsLengthsAndNonCanonicalIntegersBeforeSegmentDataRead() {
        FriendlyByteBuf oversizedFrame = new FriendlyByteBuf(Unpooled.buffer());
        oversizedFrame.writeLong(0).writeLong(1);
        writeUnsignedVarInt(
                oversizedFrame,
                MinimapHardLimits.MAX_WIRE_FRAME_BYTES + 1
        );
        MinimapWireError frameError = assertThrows(
                MinimapWireError.class,
                () -> MinimapC2SPacket.decode(oversizedFrame)
        );
        assertEquals(MinimapErrorCode.QUOTA_EXCEEDED, frameError.code());
        assertEquals(0, oversizedFrame.readableBytes());
        oversizedFrame.release();

        FriendlyByteBuf nonCanonical = new FriendlyByteBuf(Unpooled.buffer());
        nonCanonical.writeLong(0).writeLong(2);
        nonCanonical.writeByte(0x81).writeByte(0x00);
        MinimapWireError canonicalError = assertThrows(
                MinimapWireError.class,
                () -> MinimapC2SPacket.decode(nonCanonical)
        );
        assertEquals(MinimapErrorCode.MALFORMED_MESSAGE, canonicalError.code());
        nonCanonical.release();

        FriendlyByteBuf oversizedSegment = new FriendlyByteBuf(Unpooled.buffer());
        oversizedSegment.writeLong(0).writeLong(3);
        writeUnsignedVarInt(oversizedSegment, 40_000);
        oversizedSegment.writeBytes(new byte[32]);
        writeUnsignedVarInt(oversizedSegment, 0);
        writeUnsignedVarInt(oversizedSegment, 2);
        writeUnsignedVarInt(
                oversizedSegment,
                MinimapHardLimits.MAX_FORGE_C2S_SEGMENT_BYTES + 1
        );
        int payloadStart = oversizedSegment.writerIndex();
        MinimapWireError segmentError = assertThrows(
                MinimapWireError.class,
                () -> MinimapC2SPacket.decode(oversizedSegment)
        );
        assertEquals(MinimapErrorCode.MALFORMED_MESSAGE, segmentError.code());
        assertEquals(payloadStart, oversizedSegment.readerIndex());
        oversizedSegment.release();
    }

    @Test
    void typedFactoriesRejectAnInnerMessageForTheOtherDirection() {
        MinimapWireError c2sError = assertThrows(
                MinimapWireError.class,
                () -> MinimapC2SPacket.fromMessage(
                        new UUID(0, 17), publishReservation()
                )
        );
        MinimapWireError s2cError = assertThrows(
                MinimapWireError.class,
                () -> MinimapS2CPacket.fromMessage(
                        new UUID(0, 18), reservePublish()
                )
        );

        assertEquals(MinimapErrorCode.WRONG_DIRECTION, c2sError.code());
        assertEquals(MinimapErrorCode.WRONG_DIRECTION, s2cError.code());
    }

    private static PublishWireMessage.ReservePublish reservePublish() {
        return new PublishWireMessage.ReservePublish(new UUID(0, 1), editorContext());
    }

    private static void writeUnsignedVarInt(FriendlyByteBuf buffer, int value) {
        int remaining = value;
        while ((remaining & ~0x7f) != 0) {
            buffer.writeByte((remaining & 0x7f) | 0x80);
            remaining >>>= 7;
        }
        buffer.writeByte(remaining);
    }

    private static PublishWireMessage.PublishReservation publishReservation() {
        return new PublishWireMessage.PublishReservation(
                new UUID(0, 1), editorContext(), "t", 0, 1, 2
        );
    }

    private static WireIdentity.EditorContext editorContext() {
        return new WireIdentity.EditorContext(
                new WireIdentity.ScopeLease(WireIdentity.Scope.EDITOR, 1, 2),
                new WireIdentity.DocumentBinding(
                        new WireIdentity.MapTarget(
                                new MapKey("g", "m"), NamespacedId.parse("a:d")
                        ),
                        NamespacedId.parse("a:o")
                ),
                new UUID(0, 2),
                new UUID(0, 3),
                0,
                new Sha256("11".repeat(32)),
                new Sha256("22".repeat(32)),
                0
        );
    }
}
