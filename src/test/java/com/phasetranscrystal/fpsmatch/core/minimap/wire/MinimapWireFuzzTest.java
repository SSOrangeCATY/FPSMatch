package com.phasetranscrystal.fpsmatch.core.minimap.wire;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapMessageDirection;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MinimapWireFuzzTest {
    private static final Set<MinimapErrorCode> FRAME_ERRORS = EnumSet.of(
            MinimapErrorCode.MALFORMED_MESSAGE,
            MinimapErrorCode.UNSUPPORTED_WIRE_VERSION,
            MinimapErrorCode.UNKNOWN_OPCODE,
            MinimapErrorCode.WRONG_DIRECTION,
            MinimapErrorCode.QUOTA_EXCEEDED
    );
    @Test
    void everyProperPrefixOfEveryGoldenShapeFailsAsAStableWireError() {
        for (MinimapWireMessage message : MinimapWireGoldenFixtures.messages().values()) {
            byte[] frame = MinimapWireCodec.encode(message);
            for (int length = 0; length < frame.length; length++) {
                byte[] prefix = java.util.Arrays.copyOf(frame, length);
                try {
                    MinimapWireCodec.decode(message.opcode().direction(), prefix);
                    fail(message.opcode() + " prefix length " + length + " decoded");
                } catch (MinimapWireError error) {
                    assertTrue(FRAME_ERRORS.contains(error.code()), error::toString);
                }
            }
        }
    }

    @Test
    void everyGoldenRejectsTrailingBytesAndOverlongBodyLengths() {
        for (MinimapWireMessage message : MinimapWireGoldenFixtures.messages().values()) {
            byte[] frame = MinimapWireCodec.encode(message);
            assertWireError(
                    MinimapErrorCode.MALFORMED_MESSAGE,
                    message.opcode().direction(),
                    Arrays.copyOf(frame, frame.length + 1)
            );
            assertWireError(
                    MinimapErrorCode.MALFORMED_MESSAGE,
                    message.opcode().direction(),
                    overlongBodyLength(frame)
            );
        }
    }

    @Test
    void everySingleByteGoldenMutationIsTypedOrAStableWireError() {
        for (MinimapWireMessage message : MinimapWireGoldenFixtures.messages().values()) {
            byte[] frame = MinimapWireCodec.encode(message);
            for (int index = 0; index < frame.length; index++) {
                byte[] mutated = frame.clone();
                mutated[index] ^= (byte) 0x80;
                assertTypedOrWireError(message.opcode().direction(), mutated);
            }
        }
    }

    @Test
    void deterministicMalformedInputNeverLeaksImplementationExceptions() {
        Random random = new Random(0x4650534dL);
        for (int sample = 0; sample < 10_000; sample++) {
            int bound = sample % 10 == 0
                    ? MinimapHardLimits.MAX_WIRE_FRAME_BYTES + 1
                    : 257;
            byte[] input = new byte[random.nextInt(bound)];
            random.nextBytes(input);
            assertTypedOrWireError(
                    random.nextBoolean()
                            ? MinimapMessageDirection.C2S
                            : MinimapMessageDirection.S2C,
                    input
            );
        }
    }

    private static void assertTypedOrWireError(
            MinimapMessageDirection direction,
            byte[] input
    ) {
        ByteBuffer caller = ByteBuffer.wrap(input);
        int position = caller.position();
        int limit = caller.limit();
        try {
            MinimapWireMessage decoded = MinimapWireCodec.decode(direction, caller);
            assertTrue(MinimapWireCodec.registeredOpcodes().contains(decoded.opcode()));
        } catch (MinimapWireError error) {
            assertTrue(error.code() != null, error::toString);
        } catch (RuntimeException leaked) {
            fail("wire decoder leaked " + leaked.getClass().getName(), leaked);
        }
        assertTrue(caller.position() == position, "caller position changed");
        assertTrue(caller.limit() == limit, "caller limit changed");
    }

    private static void assertWireError(
            MinimapErrorCode expected,
            MinimapMessageDirection direction,
            byte[] input
    ) {
        try {
            MinimapWireCodec.decode(direction, input);
            fail("wire input decoded instead of failing with " + expected);
        } catch (MinimapWireError error) {
            assertTrue(error.code() == expected, error::toString);
        }
    }

    private static byte[] overlongBodyLength(byte[] frame) {
        int terminal = 3;
        while ((frame[terminal] & 0x80) != 0) {
            terminal++;
        }
        byte[] overlong = new byte[frame.length + 1];
        System.arraycopy(frame, 0, overlong, 0, terminal + 1);
        overlong[terminal] |= (byte) 0x80;
        overlong[terminal + 1] = 0;
        System.arraycopy(
                frame,
                terminal + 1,
                overlong,
                terminal + 2,
                frame.length - terminal - 1
        );
        return overlong;
    }
}
