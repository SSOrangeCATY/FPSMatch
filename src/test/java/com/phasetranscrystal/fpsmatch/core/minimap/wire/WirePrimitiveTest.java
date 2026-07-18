package com.phasetranscrystal.fpsmatch.core.minimap.wire;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WirePrimitiveTest {
    @Test
    void exactWriterTransfersItsBackingArrayWithoutASecondAllocation()
            throws ReflectiveOperationException {
        WireWriter writer = new WireWriter(3, 3);
        writer.writeUnsignedByte(1);
        writer.writeUnsignedByte(2);
        writer.writeUnsignedByte(3);
        Field output = WireWriter.class.getDeclaredField("output");
        output.setAccessible(true);
        byte[] backing = (byte[]) output.get(writer);

        assertSame(backing, writer.takeExactByteArray());
    }

    @Test
    void unsignedFixedWidthIntegersUseCanonicalBigEndianBytes() {
        WireWriter writer = new WireWriter(6);
        writer.writeUnsignedByte(0);
        writer.writeUnsignedByte(255);
        writer.writeUnsignedShort(0);
        writer.writeUnsignedShort(65_535);

        assertArrayEquals(hex("00ff0000ffff"), writer.toByteArray());
        WireReader reader = new WireReader(writer.toByteArray());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(255, reader.readUnsignedByte());
        assertEquals(0, reader.readUnsignedShort());
        assertEquals(65_535, reader.readUnsignedShort());
        reader.requireFinished();
    }

    @Test
    void unsignedFixedWidthIntegersRejectOutOfRangeAndTruncation() {
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> new WireWriter(1).writeUnsignedByte(-1));
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> new WireWriter(1).writeUnsignedByte(256));
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> new WireWriter(2).writeUnsignedShort(-1));
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> new WireWriter(2).writeUnsignedShort(65_536));
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> new WireReader(new byte[0]).readUnsignedByte());
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> new WireReader(hex("01")).readUnsignedShort());
    }

    @Test
    void unsignedVarIntHasCanonicalExactBytes() {
        assertUnsignedVarInt(0, "00");
        assertUnsignedVarInt(127, "7f");
        assertUnsignedVarInt(128, "8001");
        assertUnsignedVarInt(Integer.MAX_VALUE, "ffffffff07");
    }

    @Test
    void unsignedVarIntWriterRejectsNegativeValues() {
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> new WireWriter(5).writeUnsignedVarInt(-1));
    }

    @Test
    void utf8UsesAByteLengthPrefixAndStrictRoundTrips() {
        WireWriter writer = new WireWriter(64);
        writer.writeUtf8("战术地图 A", MinimapHardLimits.MAX_WIRE_STRING_UTF8_BYTES);

        assertArrayEquals(hex("0ee68898e69cafe59cb0e59bbe2041"), writer.toByteArray());
        WireReader reader = new WireReader(writer.toByteArray());
        assertEquals("战术地图 A", reader.readUtf8(MinimapHardLimits.MAX_WIRE_STRING_UTF8_BYTES));
        reader.requireFinished();
    }

    @Test
    void utf8LimitIsMeasuredInBytesBeforePayloadAllocation() {
        WireReader reader = new WireReader(hex("03e7958c"));
        assertWireError(MinimapErrorCode.QUOTA_EXCEEDED, () -> reader.readUtf8(2));

        WireWriter writer = new WireWriter(16);
        assertWireError(MinimapErrorCode.QUOTA_EXCEEDED, () -> writer.writeUtf8("界", 2));
    }

    @Test
    void invalidAndOverlongUtf8AreRejectedWithoutReplacement() {
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> new WireReader(hex("02c328")).readUtf8(8));
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> new WireReader(hex("02c0af")).readUtf8(8));

        WireWriter writer = new WireWriter(16);
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> writer.writeUtf8("\ud800", 8));
    }

    @Test
    void utf8RejectsTruncatedPayloadAndNonCanonicalLength() {
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> new WireReader(hex("02c3")).readUtf8(8));
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> new WireReader(hex("8000")).readUtf8(8));
    }

    @Test
    void nonNegativeVarLongHasCanonicalExactBytes() {
        assertVarLong(0L, "00");
        assertVarLong(127L, "7f");
        assertVarLong(128L, "8001");
        assertVarLong(Long.MAX_VALUE, "ffffffffffffffff7f");
    }

    @Test
    void negativeOverflowingNonCanonicalAndTruncatedVarLongsAreRejected() {
        WireWriter writer = new WireWriter(16);
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> writer.writeNonNegativeVarLong(-1));

        for (String encoded : new String[]{
                "ffffffffffffffffff01",
                "80808080808080808000",
                "8000",
                "80"
        }) {
            assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                    () -> new WireReader(hex(encoded)).readNonNegativeVarLong());
        }
    }

    @Test
    void signedVarIntUsesCanonicalZigZagForTheFullIntDomain() {
        assertSignedVarInt(0, "00");
        assertSignedVarInt(-1, "01");
        assertSignedVarInt(1, "02");
        assertSignedVarInt(Integer.MAX_VALUE, "feffffff0f");
        assertSignedVarInt(Integer.MIN_VALUE, "ffffffff0f");
    }

    @Test
    void signedVarLongUsesCanonicalZigZagForTheFullLongDomain() {
        assertSignedVarLong(0L, "00");
        assertSignedVarLong(-1L, "01");
        assertSignedVarLong(1L, "02");
        assertSignedVarLong(Long.MAX_VALUE, "feffffffffffffffff01");
        assertSignedVarLong(Long.MIN_VALUE, "ffffffffffffffffff01");
    }

    @Test
    void signedVarIntsRejectNonCanonicalOverflowAndTruncation() {
        for (String encoded : new String[]{"8000", "ffffffff1f", "80"}) {
            assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                    () -> new WireReader(hex(encoded)).readSignedVarInt());
        }
        for (String encoded : new String[]{"8000", "ffffffffffffffffff02", "80"}) {
            assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                    () -> new WireReader(hex(encoded)).readSignedVarLong());
        }
    }

    @Test
    void unsignedVarIntRejectsNonCanonicalOverflowAndTruncation() {
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> new WireReader(hex("8000")).readUnsignedVarInt(Integer.MAX_VALUE));
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> new WireReader(hex("ffffffff0f")).readUnsignedVarInt(Integer.MAX_VALUE));
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> new WireReader(hex("80")).readUnsignedVarInt(Integer.MAX_VALUE));
    }

    @Test
    void booleanTagsAreStrictAndTruncationIsRejected() {
        WireWriter writer = new WireWriter(2);
        writer.writeBoolean(false);
        writer.writeBoolean(true);

        assertArrayEquals(hex("0001"), writer.toByteArray());
        WireReader reader = new WireReader(writer.toByteArray());
        assertFalse(reader.readBoolean());
        assertTrue(reader.readBoolean());
        reader.requireFinished();

        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> new WireReader(hex("02")).readBoolean());
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> new WireReader(new byte[0]).readBoolean());
    }

    @Test
    void optionalPresenceUsesOneStrictBooleanByteForTheWholeGroup() {
        WireWriter writer = new WireWriter(3);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        writer.writeUnsignedByte(0x2a);

        assertArrayEquals(hex("00012a"), writer.toByteArray());
        WireReader reader = new WireReader(writer.toByteArray());
        assertFalse(reader.readBoolean());
        assertTrue(reader.readBoolean());
        assertEquals(0x2a, reader.readUnsignedByte());
        reader.requireFinished();
    }

    @Test
    void uuidIsExactlySixteenBytesInNetworkOrder() {
        UUID value = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
        WireWriter writer = new WireWriter(16);
        writer.writeUuid(value);

        assertArrayEquals(hex("00112233445566778899aabbccddeeff"), writer.toByteArray());
        WireReader reader = new WireReader(writer.toByteArray());
        assertEquals(value, reader.readUuid());
        reader.requireFinished();

        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> new WireReader(new byte[15]).readUuid());
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> new WireWriter(16).writeUuid(null));
    }

    @Test
    void sha256IsExactlyThirtyTwoBytes() {
        String expectedHex = "000102030405060708090a0b0c0d0e0f"
                + "101112131415161718191a1b1c1d1e1f";
        Sha256 value = new Sha256(expectedHex);
        WireWriter writer = new WireWriter(32);
        writer.writeHash(value);

        assertArrayEquals(hex(expectedHex), writer.toByteArray());
        WireReader reader = new WireReader(writer.toByteArray());
        assertEquals(value, reader.readHash());
        reader.requireFinished();

        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> new WireReader(new byte[31]).readHash());
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> new WireWriter(32).writeHash(null));
    }

    @Test
    void finiteFloatingPointValuesUseCanonicalBigEndianBytes() {
        WireWriter writer = new WireWriter(12);
        writer.writeFloat(1.0f);
        writer.writeDouble(-2.5d);

        assertArrayEquals(hex("3f800000c004000000000000"), writer.toByteArray());
        WireReader reader = new WireReader(writer.toByteArray());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(-2.5d, reader.readDouble());
        reader.requireFinished();
    }

    @Test
    void floatingPointValuesRejectNonFiniteValuesAndTruncation() {
        for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                    () -> new WireWriter(4).writeFloat(value));
        }
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                    () -> new WireWriter(8).writeDouble(value));
        }
        for (String encoded : new String[]{"7fc00000", "7f800000", "ff800000"}) {
            assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                    () -> new WireReader(hex(encoded)).readFloat());
        }
        for (String encoded : new String[]{
                "7ff8000000000000", "7ff0000000000000", "fff0000000000000"
        }) {
            assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                    () -> new WireReader(hex(encoded)).readDouble());
        }
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> new WireReader(new byte[3]).readFloat());
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> new WireReader(new byte[7]).readDouble());
    }

    @Test
    void countsAreBoundedIndividuallyAndInCheckedTotals() {
        assertEquals(32, new WireReader(hex("20")).readCount(32));
        assertWireError(MinimapErrorCode.QUOTA_EXCEEDED,
                () -> new WireReader(hex("21")).readCount(32));

        assertEquals(32, WireReader.checkedCountTotal(20, 12, 32));
        assertWireError(MinimapErrorCode.QUOTA_EXCEEDED,
                () -> WireReader.checkedCountTotal(20, 13, 32));
        assertWireError(MinimapErrorCode.QUOTA_EXCEEDED,
                () -> WireReader.checkedCountTotal(Integer.MAX_VALUE, 1, Integer.MAX_VALUE));
    }

    @Test
    void fragmentLengthAbove262144IsRejectedBeforeAllocationOrTruncationChecks() {
        assertWireError(MinimapErrorCode.QUOTA_EXCEEDED,
                () -> new WireReader(hex("818010"))
                        .readByteArray(MinimapHardLimits.MAX_WIRE_BODY_BYTES));

        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> new WireReader(hex("808010"))
                        .readByteArray(MinimapHardLimits.MAX_WIRE_BODY_BYTES));
    }

    @Test
    void fragmentPayloadAt262144IsAllowedByThePrimitiveAndFrameOverheadIsSeparate() {
        int fragmentLength = MinimapHardLimits.MAX_WIRE_BODY_BYTES;
        byte[] encoded = new byte[3 + fragmentLength];
        encoded[0] = (byte) 0x80;
        encoded[1] = (byte) 0x80;
        encoded[2] = 0x10;
        ArraysSupport.fill(encoded, 3, encoded.length, (byte) 0x5a);

        WireReader reader = new WireReader(ByteBuffer.wrap(encoded));
        byte[] fragment = reader.readByteArray(fragmentLength);

        assertEquals(fragmentLength, fragment.length);
        assertEquals((byte) 0x5a, fragment[0]);
        assertEquals((byte) 0x5a, fragment[fragment.length - 1]);
        reader.requireFinished();
    }

    @Test
    void byteArrayWriterChecksDeclaredLimitAndWriterCapacityBeforeGrowth() {
        WireWriter fieldLimited = new WireWriter(32);
        assertWireError(MinimapErrorCode.QUOTA_EXCEEDED,
                () -> fieldLimited.writeByteArray(new byte[9], 8));

        WireWriter frameLimited = new WireWriter(4);
        assertWireError(MinimapErrorCode.QUOTA_EXCEEDED,
                () -> frameLimited.writeByteArray(new byte[4], 4));
    }

    @Test
    void lengthPrefixedBytesUseCanonicalUviAndRoundTrip() {
        WireWriter writer = new WireWriter(8);
        writer.writeByteArray(new byte[0], 4);
        writer.writeByteArray(hex("00ff"), 4);

        assertArrayEquals(hex("000200ff"), writer.toByteArray());
        WireReader reader = new WireReader(writer.toByteArray());
        assertArrayEquals(new byte[0], reader.readByteArray(4));
        assertArrayEquals(hex("00ff"), reader.readByteArray(4));
        reader.requireFinished();
    }

    @Test
    void lengthPrefixedBytesRejectTruncationNonCanonicalLengthAndLimitFirst() {
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> new WireReader(hex("030102")).readByteArray(3));
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> new WireReader(hex("8000")).readByteArray(8));
        assertWireError(MinimapErrorCode.QUOTA_EXCEEDED,
                () -> new WireReader(hex("09")).readByteArray(8));
    }

    @Test
    void remainingAndRequireFinishedRejectTrailingBytes() {
        WireReader reader = new WireReader(hex("0102"));
        assertEquals(2, reader.remaining());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(1, reader.remaining());
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, reader::requireFinished);
        assertEquals(2, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
        reader.requireFinished();
    }

    @Test
    void exactMaxFragmentFitsWriterWithFrameBudget() {
        byte[] fragment = new byte[MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES];
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeByteArray(fragment, MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES);

        int padding = MinimapHardLimits.MAX_WIRE_FRAME_BYTES - writer.toByteArray().length;
        for (int index = 0; index < padding; index++) {
            writer.writeUnsignedByte(0);
        }

        assertEquals(MinimapHardLimits.MAX_WIRE_FRAME_BYTES, writer.toByteArray().length);
        assertWireError(MinimapErrorCode.QUOTA_EXCEEDED,
                () -> writer.writeUnsignedByte(0));
    }

    @Test
    void writerConstructorCannotEscapeTheFrameHardLimit() {
        WireWriter writer = new WireWriter(Integer.MAX_VALUE);
        for (int index = 0; index < MinimapHardLimits.MAX_WIRE_FRAME_BYTES; index++) {
            writer.writeUnsignedByte(0);
        }

        assertEquals(MinimapHardLimits.MAX_WIRE_FRAME_BYTES, writer.toByteArray().length);
        assertWireError(MinimapErrorCode.QUOTA_EXCEEDED,
                () -> writer.writeUnsignedByte(0));
    }

    private static void assertUnsignedVarInt(int value, String expectedHex) {
        WireWriter writer = new WireWriter(5);
        writer.writeUnsignedVarInt(value);
        assertArrayEquals(hex(expectedHex), writer.toByteArray());

        WireReader reader = new WireReader(writer.toByteArray());
        assertEquals(value, reader.readUnsignedVarInt(Integer.MAX_VALUE));
        reader.requireFinished();
    }

    private static void assertVarLong(long value, String expectedHex) {
        WireWriter writer = new WireWriter(16);
        writer.writeNonNegativeVarLong(value);
        assertArrayEquals(hex(expectedHex), writer.toByteArray());

        WireReader reader = new WireReader(writer.toByteArray());
        assertEquals(value, reader.readNonNegativeVarLong());
        reader.requireFinished();
    }

    private static void assertSignedVarInt(int value, String expectedHex) {
        WireWriter writer = new WireWriter(5);
        writer.writeSignedVarInt(value);
        assertArrayEquals(hex(expectedHex), writer.toByteArray());

        WireReader reader = new WireReader(writer.toByteArray());
        assertEquals(value, reader.readSignedVarInt());
        reader.requireFinished();
    }

    private static void assertSignedVarLong(long value, String expectedHex) {
        WireWriter writer = new WireWriter(10);
        writer.writeSignedVarLong(value);
        assertArrayEquals(hex(expectedHex), writer.toByteArray());

        WireReader reader = new WireReader(writer.toByteArray());
        assertEquals(value, reader.readSignedVarLong());
        reader.requireFinished();
    }

    private static void assertWireError(MinimapErrorCode expected, Runnable action) {
        MinimapWireError error = assertThrows(MinimapWireError.class, action::run);
        assertEquals(expected, error.code());
    }

    private static byte[] hex(String value) {
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    /** Keeps the fixture setup compatible with Java 17 without exposing an implementation helper. */
    private static final class ArraysSupport {
        private static void fill(byte[] array, int from, int to, byte value) {
            for (int index = from; index < to; index++) {
                array[index] = value;
            }
        }
    }
}
