package com.phasetranscrystal.fpsmatch.core.minimap.wire;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapMessageDirection;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkerWireMessageTest {
    private static final HexFormat HEX = HexFormat.of();

    @Test
    void markerEncodingCountsBeforeAllocatingItsExactBackingArray() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/core/minimap/wire/MarkerWireCodec.java"
        ));

        assertTrue(source.contains("WireWriter counting = WireWriter.counting("));
        assertTrue(source.contains("MinimapHardLimits.MAX_WIRE_FRAME_BYTES"));
        assertTrue(source.contains("takeExactByteArray()"));
        assertTrue(source.contains("return exact.takeExactByteArray();"));
        assertFalse(source.contains("MinimapHardLimits.MAX_WIRE_FRAME_BYTES,\n                256"));
    }

    @Test
    void resetAndDeltaMatchIndependentGoldensAndRoundTrip() {
        MarkerWireMessage.Reset reset = resetFixture();

        byte[] resetFrame = MinimapWireCodec.encode(reset);

        assertEquals(250, resetFrame.length);
        assertArrayEquals(HEX.parseHex(
                "010044f501"
                        + "000001020167016d016101640161016f03"
                        + "11".repeat(32)
                        + "00"
                        + "00000000000000000000000000000001"
                        + "00"
                        + "00000000000000000000000000000002"
                        + "000101"
                        + "0161016d0161017401610173"
                        + "3ff0000000000000c0000000000000003fe0000000000000"
                        + "42b4000004010501016609"
                        + "016101610001"
                        + "016101620101"
                        + "016101630206"
                        + "01610164033ff8000000000000"
                        + "01610165040178"
                        + "016101660501620169"
                        + "016101670600000000000000000000000000000003"
                        + "0161016807" + "22".repeat(32)
                        + "0161016908017f"
        ), resetFrame);
        assertEquals(
                reset,
                MinimapWireCodec.decode(MinimapMessageDirection.S2C, resetFrame)
        );

        MarkerWireMessage.Delta delta = deltaFixture();

        byte[] deltaFrame = MinimapWireCodec.encode(delta);

        assertEquals(167, deltaFrame.length);
        assertArrayEquals(HEX.parseHex(
                "010045a201"
                        + "0106070167016d016101640161016f03"
                        + "11".repeat(32)
                        + "00"
                        + "00000000000000000000000000000001"
                        + "0803"
                        + "00"
                        + "016101610161017401610173"
                        + "00".repeat(24)
                        + "00000000"
                        + "00000000"
                        + "01"
                        + "016101610161017401610173"
                        + "3ff0000000000000" + "00".repeat(16)
                        + "00000000"
                        + "01000000"
                        + "02"
                        + "01610162"
        ), deltaFrame);
        assertEquals(
                delta,
                MinimapWireCodec.decode(MinimapMessageDirection.S2C, deltaFrame)
        );
    }

    @Test
    void streamingMarkerFramesMatchTheCanonicalArrayCodecExactly() {
        for (MarkerWireMessage message : List.of(resetFixture(), deltaFixture())) {
            byte[] expected = MinimapWireCodec.encode(message);
            ByteArrayOutputStream streamed = new ByteArrayOutputStream(
                    expected.length
            );

            assertEquals(
                    expected.length,
                    MinimapWireCodec.markerFrameLength(message)
            );
            MinimapWireCodec.writeMarkerFrame(
                    message,
                    new MinimapWireCodec.FrameSink() {
                        @Override
                        public void writeByte(int value) {
                            streamed.write(value);
                        }

                        @Override
                        public void writeBytes(byte[] value) {
                            streamed.writeBytes(value);
                        }
                    }
            );

            assertArrayEquals(expected, streamed.toByteArray());
        }
    }

    @Test
    void stateFieldsUseCanonicalWireOrderAndDefensiveBytes() {
        WireMarker.StateField z = field("a:z", new WireMarker.BoolValue(true));
        WireMarker.StateField aa = field("a:aa", new WireMarker.BoolValue(false));
        WireMarker.Marker sorted = markerWithState(List.of(aa, z));

        assertEquals(List.of(z, aa), sorted.stateFields());
        assertThrows(IllegalArgumentException.class, () -> markerWithState(List.of(z, z)));

        byte[] mutable = new byte[]{1, 2, 3};
        WireMarker.BytesValue bytesValue = new WireMarker.BytesValue(mutable);
        mutable[0] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, bytesValue.value());
        byte[] exposed = bytesValue.value();
        exposed[1] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, bytesValue.value());
    }

    @Test
    void stateCountAndEncodedByteBudgetsHaveExactBoundaries() {
        WireMarker.StateField maximumBytes = field(
                "a:a", new WireMarker.BytesValue(new byte[3_584])
        );
        WireMarker.StateField exactRemainder = field(
                "a:b", new WireMarker.StringValue("x".repeat(498))
        );

        assertEquals(
                2,
                markerWithState(List.of(maximumBytes, exactRemainder)).stateFields().size()
        );
        assertThrows(IllegalArgumentException.class, () -> markerWithState(List.of(
                maximumBytes,
                field("a:b", new WireMarker.StringValue("x".repeat(499)))
        )));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WireMarker.BytesValue(new byte[3_585])
        );

        List<WireMarker.StateField> fields = new ArrayList<>();
        for (int index = 0; index < MinimapHardLimits.MAX_MARKER_STATE_FIELDS; index++) {
            fields.add(field("a:k" + index, new WireMarker.BoolValue(true)));
        }
        assertEquals(64, markerWithState(fields).stateFields().size());
        fields.add(field("a:overflow", new WireMarker.BoolValue(true)));
        assertThrows(IllegalArgumentException.class, () -> markerWithState(fields));
    }

    @Test
    void markerPagesOperationsScopesAndSequencesAreBounded() {
        WireMarker.Marker marker = simpleMarker("a:m", 0.0, 0);
        WireIdentity.ScopeLease runtimeLease = new WireIdentity.ScopeLease(
                WireIdentity.Scope.MATCH_HUD, 0, 0
        );
        WireIdentity.ScopeLease editorLease = new WireIdentity.ScopeLease(
                WireIdentity.Scope.EDITOR, 0, 0
        );

        MarkerWireMessage.Reset maximumPage = new MarkerWireMessage.Reset(
                Optional.empty(), runtimeLease, runtimeIdentity(), uuid(1), 0, uuid(2),
                4_095, 4_096, Collections.nCopies(32, marker)
        );
        assertEquals(32, maximumPage.markers().size());
        assertThrows(IllegalArgumentException.class, () -> new MarkerWireMessage.Reset(
                Optional.empty(), runtimeLease, runtimeIdentity(), uuid(1), 1, uuid(2),
                0, 1, List.of(marker)
        ));
        assertThrows(IllegalArgumentException.class, () -> new MarkerWireMessage.Reset(
                Optional.empty(), runtimeLease, runtimeIdentity(), uuid(1), 0, uuid(2),
                0, 0, List.of(marker)
        ));
        assertThrows(IllegalArgumentException.class, () -> new MarkerWireMessage.Reset(
                Optional.empty(), runtimeLease, runtimeIdentity(), uuid(1), 0, uuid(2),
                1, 1, List.of(marker)
        ));
        assertThrows(IllegalArgumentException.class, () -> new MarkerWireMessage.Reset(
                Optional.empty(), runtimeLease, runtimeIdentity(), uuid(1), 0, uuid(2),
                0, 4_097, List.of(marker)
        ));
        assertThrows(IllegalArgumentException.class, () -> new MarkerWireMessage.Reset(
                Optional.empty(), runtimeLease, runtimeIdentity(), uuid(1), 0, uuid(2),
                0, 1, Collections.nCopies(33, marker)
        ));
        assertThrows(IllegalArgumentException.class, () -> new MarkerWireMessage.Reset(
                Optional.empty(), editorLease, runtimeIdentity(), uuid(1), 0, uuid(2),
                0, 1, List.of(marker)
        ));

        WireMarker.Add add = new WireMarker.Add(marker);
        assertEquals(32, new MarkerWireMessage.Delta(
                runtimeLease, runtimeIdentity(), uuid(1), 0,
                Collections.nCopies(32, add)
        ).operations().size());
        assertThrows(IllegalArgumentException.class, () -> new MarkerWireMessage.Delta(
                runtimeLease, runtimeIdentity(), uuid(1), 0,
                Collections.nCopies(33, add)
        ));
        assertThrows(IllegalArgumentException.class, () -> new MarkerWireMessage.Delta(
                runtimeLease, runtimeIdentity(), uuid(1), -1, List.of(add)
        ));
        assertThrows(IllegalArgumentException.class, () -> new MarkerWireMessage.Delta(
                editorLease, runtimeIdentity(), uuid(1), 1, List.of(add)
        ));
    }

    @Test
    void markerNumbersTicksStringsAndStateValuesAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> marker(
                Double.NaN, 0, 0, 0, 0, Optional.empty(), Optional.empty(), List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> marker(
                0, Double.POSITIVE_INFINITY, 0, 0, 0,
                Optional.empty(), Optional.empty(), List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> marker(
                0, 0, 0, Float.NEGATIVE_INFINITY, 0,
                Optional.empty(), Optional.empty(), List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> marker(
                0, 0, 0, 0, -1, Optional.empty(), Optional.empty(), List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> marker(
                0, 0, 0, 0, 0, Optional.of(-1L), Optional.empty(), List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> marker(
                0, 0, 0, 0, 0, Optional.empty(), Optional.of("x".repeat(65)), List.of()
        ));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WireMarker.UnsignedLongValue(-1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new WireMarker.DoubleValue(Double.NaN)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new WireMarker.StringValue("x".repeat(1_025))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new WireMarker.StringValue("\ud800")
        );
    }

    @Test
    void decodeRejectsUnknownTagsNonCanonicalStateAndTrailingBytes() {
        byte[] reset = MinimapWireCodec.encode(resetFixture());
        byte[] unknownStateTag = reset.clone();
        unknownStateTag[142] = 9;
        assertThrows(MinimapWireError.class, () -> MinimapWireCodec.decode(
                MinimapMessageDirection.S2C, unknownStateTag
        ));

        byte[] duplicateStateKey = reset.clone();
        duplicateStateKey[147] = 0x61;
        assertThrows(MinimapWireError.class, () -> MinimapWireCodec.decode(
                MinimapMessageDirection.S2C, duplicateStateKey
        ));

        byte[] unsortedState = reset.clone();
        unsortedState[141] = 0x7a;
        assertThrows(MinimapWireError.class, () -> MinimapWireCodec.decode(
                MinimapMessageDirection.S2C, unsortedState
        ));

        byte[] nonFiniteCoordinate = reset.clone();
        System.arraycopy(HEX.parseHex("7ff8000000000000"), 0, nonFiniteCoordinate, 103, 8);
        assertThrows(MinimapWireError.class, () -> MinimapWireCodec.decode(
                MinimapMessageDirection.S2C, nonFiniteCoordinate
        ));

        byte[] delta = MinimapWireCodec.encode(deltaFixture());
        byte[] unknownDeltaTag = delta.clone();
        unknownDeltaTag[72] = 3;
        assertThrows(MinimapWireError.class, () -> MinimapWireCodec.decode(
                MinimapMessageDirection.S2C, unknownDeltaTag
        ));

        assertThrows(MinimapWireError.class, () -> MinimapWireCodec.decode(
                MinimapMessageDirection.S2C,
                Arrays.copyOf(reset, reset.length + 1)
        ));
        assertThrows(MinimapWireError.class, () -> MinimapWireCodec.decode(
                MinimapMessageDirection.C2S, reset
        ));
    }

    @Test
    void decodeAcceptsExactly4096StateBytesAndRejects4097() {
        WireMarker.StateField maximumBytes = field(
                "a:a", new WireMarker.BytesValue(new byte[3_584])
        );
        WireMarker.StateField exactRemainder = field(
                "a:b", new WireMarker.StringValue("x".repeat(498))
        );
        MarkerWireMessage.Reset exact = new MarkerWireMessage.Reset(
                Optional.empty(),
                new WireIdentity.ScopeLease(WireIdentity.Scope.MATCH_HUD, 0, 0),
                runtimeIdentity(),
                uuid(1),
                0,
                uuid(2),
                0,
                1,
                List.of(markerWithState(List.of(maximumBytes, exactRemainder)))
        );
        byte[] exactFrame = MinimapWireCodec.encode(exact);

        assertEquals(
                exact,
                MinimapWireCodec.decode(MinimapMessageDirection.S2C, exactFrame)
        );

        int stringLengthPrefix = exactFrame.length - 498 - 2;
        assertEquals(0xf2, Byte.toUnsignedInt(exactFrame[stringLengthPrefix]));
        assertEquals(0x03, Byte.toUnsignedInt(exactFrame[stringLengthPrefix + 1]));
        byte[] overflow = Arrays.copyOf(exactFrame, exactFrame.length + 1);
        overflow[overflow.length - 1] = 'x';
        overflow[stringLengthPrefix] = (byte) 0xf3;
        int bodyLength = (Byte.toUnsignedInt(overflow[3]) & 0x7f)
                | (Byte.toUnsignedInt(overflow[4]) << 7);
        int largerBody = bodyLength + 1;
        overflow[3] = (byte) ((largerBody & 0x7f) | 0x80);
        overflow[4] = (byte) (largerBody >>> 7);

        MinimapWireError overflowError = assertThrows(
                MinimapWireError.class,
                () -> MinimapWireCodec.decode(MinimapMessageDirection.S2C, overflow)
        );
        assertEquals(MinimapErrorCode.QUOTA_EXCEEDED, overflowError.code());
    }

    private static MarkerWireMessage.Reset resetFixture() {
        return new MarkerWireMessage.Reset(
                Optional.empty(),
                new WireIdentity.ScopeLease(WireIdentity.Scope.MATCH_HUD, 1, 2),
                runtimeIdentity(),
                uuid(1),
                0,
                uuid(2),
                0,
                1,
                List.of(new WireMarker.Marker(
                        NamespacedId.parse("a:m"),
                        NamespacedId.parse("a:t"),
                        NamespacedId.parse("a:s"),
                        1.0,
                        -2.0,
                        0.5,
                        90.0f,
                        4,
                        Optional.of(5L),
                        Optional.of("f"),
                        List.of(
                                field("a:a", new WireMarker.BoolValue(true)),
                                field("a:b", new WireMarker.SignedLongValue(-1)),
                                field("a:c", new WireMarker.UnsignedLongValue(6)),
                                field("a:d", new WireMarker.DoubleValue(1.5)),
                                field("a:e", new WireMarker.StringValue("x")),
                                field(
                                        "a:f",
                                        new WireMarker.IdValue(NamespacedId.parse("b:i"))
                                ),
                                field("a:g", new WireMarker.UuidValue(uuid(3))),
                                field(
                                        "a:h",
                                        new WireMarker.HashValue(
                                                new Sha256("22".repeat(32))
                                        )
                                ),
                                field(
                                        "a:i",
                                        new WireMarker.BytesValue(new byte[]{0x7f})
                                )
                        )
                ))
        );
    }

    private static MarkerWireMessage.Delta deltaFixture() {
        return new MarkerWireMessage.Delta(
                new WireIdentity.ScopeLease(WireIdentity.Scope.TACTICAL_SCREEN, 6, 7),
                runtimeIdentity(),
                uuid(1),
                8,
                List.of(
                        new WireMarker.Add(simpleMarker("a:a", 0.0, 0)),
                        new WireMarker.Update(simpleMarker("a:a", 1.0, 1)),
                        new WireMarker.Remove(NamespacedId.parse("a:b"))
                )
        );
    }

    private static WireMarker.Marker markerWithState(
            List<WireMarker.StateField> stateFields
    ) {
        return marker(
                0, 0, 0, 0, 0,
                Optional.empty(), Optional.empty(), stateFields
        );
    }

    private static WireMarker.Marker marker(
            double x,
            double y,
            double z,
            float yaw,
            long updatedTick,
            Optional<Long> expiresTick,
            Optional<String> floorSlug,
            List<WireMarker.StateField> stateFields
    ) {
        return new WireMarker.Marker(
                NamespacedId.parse("a:m"),
                NamespacedId.parse("a:t"),
                NamespacedId.parse("a:s"),
                x,
                y,
                z,
                yaw,
                updatedTick,
                expiresTick,
                floorSlug,
                stateFields
        );
    }

    private static WireMarker.StateField field(
            String key,
            WireMarker.StateValue value
    ) {
        return new WireMarker.StateField(NamespacedId.parse(key), value);
    }

    private static WireMarker.Marker simpleMarker(String id, double x, long updatedTick) {
        return new WireMarker.Marker(
                NamespacedId.parse(id),
                NamespacedId.parse("a:t"),
                NamespacedId.parse("a:s"),
                x,
                0.0,
                0.0,
                0.0f,
                updatedTick,
                Optional.empty(),
                Optional.empty(),
                List.of()
        );
    }

    private static WireIdentity.RuntimeIdentity runtimeIdentity() {
        return new WireIdentity.RuntimeIdentity(
                new WireIdentity.DocumentBinding(
                        new WireIdentity.MapTarget(
                                new MapKey("g", "m"),
                                NamespacedId.parse("a:d")
                        ),
                        NamespacedId.parse("a:o")
                ),
                3,
                new Sha256("11".repeat(32)),
                Optional.empty()
        );
    }

    private static UUID uuid(long lowBits) {
        return new UUID(0, lowBits);
    }
}
