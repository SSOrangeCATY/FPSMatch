package com.phasetranscrystal.fpsmatch.core.minimap.wire;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapMessageDirection;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapOpcode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapWireCodecTest {
    private static final String GOLDEN_RESOURCE =
            "/com/phasetranscrystal/fpsmatch/minimap/contract/v1/wire/minimap-bodies.hex";

    @Test
    void registryCoversEveryStableOpcode() {
        assertEquals(EnumSet.allOf(MinimapOpcode.class), MinimapWireCodec.registeredOpcodes());
    }

    @Test
    void everyStableOpcodeHasAConcreteTypedGoldenAndRoundTripsExactly() throws IOException {
        Map<MinimapOpcode, MinimapWireMessage> messages =
                MinimapWireGoldenFixtures.messages();
        Map<String, byte[]> golden = loadGoldenFrames();

        assertEquals(EnumSet.allOf(MinimapOpcode.class), messages.keySet());
        assertEquals(
                EnumSet.allOf(MinimapOpcode.class),
                golden.keySet().stream().map(MinimapOpcode::valueOf)
                        .collect(java.util.stream.Collectors.toSet())
        );
        assertTrue(Stream.of(MinimapWireMessage.class.getPermittedSubclasses())
                .map(Class::getSimpleName)
                .noneMatch("Opaque"::equals));

        for (MinimapOpcode opcode : MinimapOpcode.values()) {
            MinimapWireMessage message = messages.get(opcode);
            byte[] encoded = MinimapWireCodec.encode(message);
            assertArrayEquals(golden.get(opcode.name()), encoded, opcode.name());
            assertEquals(message, MinimapWireCodec.decode(opcode.direction(), encoded));
            assertEquals(opcode, message.opcode());
        }
    }

    @Test
    void everyConcreteRecordObeysTheExactScopeMatrix() {
        for (MinimapWireMessage message : MinimapWireGoldenFixtures.messages().values()) {
            Set<WireIdentity.Scope> allowed = allowedScopes(message.opcode());
            for (WireIdentity.Scope scope : WireIdentity.Scope.values()) {
                if (allowed.contains(scope)) {
                    MinimapWireMessage variant = replaceScope(message, scope);
                    byte[] frame = MinimapWireCodec.encode(variant);
                    assertEquals(
                            variant,
                            MinimapWireCodec.decode(message.opcode().direction(), frame),
                            message.opcode() + " " + scope
                    );
                } else {
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> replaceScope(message, scope),
                            message.opcode() + " " + scope
                    );
                }
            }
        }
    }

    @Test
    void unsupportedMajorAndMinorVersionsHaveStableErrors() {
        assertWireError(MinimapErrorCode.UNSUPPORTED_WIRE_VERSION,
                () -> MinimapWireCodec.decode(MinimapMessageDirection.C2S, hex("02000100")));
        assertWireError(MinimapErrorCode.UNSUPPORTED_WIRE_VERSION,
                () -> MinimapWireCodec.decode(MinimapMessageDirection.C2S, hex("01010100")));
    }

    @Test
    void unknownOpcodeHasAStableError() {
        assertWireError(MinimapErrorCode.UNKNOWN_OPCODE,
                () -> MinimapWireCodec.decode(MinimapMessageDirection.C2S, hex("01000500")));
    }

    @Test
    void overflowingUnsignedBodyLengthHasAStableMalformedError() {
        assertWireError(
                MinimapErrorCode.MALFORMED_MESSAGE,
                () -> MinimapWireCodec.decode(
                        MinimapMessageDirection.C2S,
                        hex("0100018080808008")
                )
        );
    }

    @Test
    void knownOpcodeFromWrongDirectionIsRejectedBeforeReturningAMessage() {
        assertWireError(MinimapErrorCode.WRONG_DIRECTION,
                () -> MinimapWireCodec.decode(MinimapMessageDirection.C2S, hex("01004100")));
    }

    @Test
    void truncatedFramesHaveAStableMalformedError() {
        for (String encoded : new String[]{"", "01", "0100", "010001", "01000180", "01000103aa"}) {
            assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                    () -> MinimapWireCodec.decode(MinimapMessageDirection.C2S, hex(encoded)));
        }
    }

    @Test
    void trailingBytesAfterTheDeclaredPayloadAreRejected() {
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> MinimapWireCodec.decode(MinimapMessageDirection.C2S, hex("01000100ff")));
    }

    @Test
    void oversizedFrameAndPayloadHaveAStableQuotaError() {
        assertWireError(MinimapErrorCode.QUOTA_EXCEEDED,
                () -> MinimapWireCodec.decode(MinimapMessageDirection.C2S,
                        new byte[MinimapHardLimits.MAX_WIRE_FRAME_BYTES + 1]));
    }

    @Test
    void byteBufferDecodeConsumesOnlyItsBoundedSlice() {
        MinimapWireMessage message = reservePublish();
        byte[] frame = MinimapWireCodec.encode(message);
        ByteBuffer container = ByteBuffer.allocate(frame.length + 2);
        container.put((byte) 0xaa);
        container.put(frame);
        container.put((byte) 0xbb);
        container.position(1);
        container.limit(frame.length + 1);

        MinimapWireMessage decoded =
                MinimapWireCodec.decode(MinimapMessageDirection.C2S, container.slice());

        assertEquals(message, decoded);
        assertEquals(1, container.position());
        assertEquals(frame.length + 1, container.limit());
    }

    private static void assertWireError(MinimapErrorCode expected, Runnable action) {
        MinimapWireError error = assertThrows(MinimapWireError.class, action::run);
        assertEquals(expected, error.code());
    }

    private static Set<WireIdentity.Scope> allowedScopes(MinimapOpcode opcode) {
        return switch (opcode) {
            case C2S_SUBSCRIBE, C2S_UNSUBSCRIBE, C2S_REQUEST_ENTRIES,
                    C2S_REQUEST_MARKER_RESET, S2C_SCOPE_ACK, S2C_MANIFEST,
                    S2C_ENTRY_FRAGMENT, S2C_MARKER_RESET, S2C_MARKER_DELTA ->
                    EnumSet.of(
                            WireIdentity.Scope.MATCH_HUD,
                            WireIdentity.Scope.TACTICAL_SCREEN
                    );
            case S2C_ERROR -> EnumSet.allOf(WireIdentity.Scope.class);
            default -> EnumSet.of(WireIdentity.Scope.EDITOR);
        };
    }

    private static MinimapWireMessage replaceScope(
            MinimapWireMessage message,
            WireIdentity.Scope scope
    ) {
        return (MinimapWireMessage) replaceScopeValue(message, scope);
    }

    private static Object replaceScopeValue(Object value, WireIdentity.Scope scope) {
        if (value instanceof WireIdentity.ScopeLease lease) {
            return new WireIdentity.ScopeLease(
                    scope,
                    lease.scopeEpoch(),
                    lease.runtimeGeneration()
            );
        }
        if (value instanceof Optional<?> optional) {
            return optional.map(element -> replaceScopeValue(element, scope));
        }
        if (value instanceof List<?> list) {
            ArrayList<Object> replaced = new ArrayList<>(list.size());
            for (Object element : list) {
                replaced.add(replaceScopeValue(element, scope));
            }
            return List.copyOf(replaced);
        }

        Class<?> type = value.getClass();
        if (!type.isRecord()
                || !type.getPackageName().equals(MinimapWireMessage.class.getPackageName())) {
            return value;
        }

        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] parameterTypes = new Class<?>[components.length];
        Object[] arguments = new Object[components.length];
        try {
            for (int index = 0; index < components.length; index++) {
                parameterTypes[index] = components[index].getType();
                arguments[index] = replaceScopeValue(
                        components[index].getAccessor().invoke(value),
                        scope
                );
            }
            Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(arguments);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new AssertionError("Cannot rebuild " + type.getName(), exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot rebuild " + type.getName(), exception);
        }
    }

    private static PublishWireMessage.ReservePublish reservePublish() {
        WireIdentity.DocumentBinding binding = new WireIdentity.DocumentBinding(
                new WireIdentity.MapTarget(
                        new com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey("g", "m"),
                        com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId.parse("a:d")
                ),
                com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId.parse("a:o")
        );
        return new PublishWireMessage.ReservePublish(
                new java.util.UUID(0, 1),
                new WireIdentity.EditorContext(
                        new WireIdentity.ScopeLease(WireIdentity.Scope.EDITOR, 1, 2),
                        binding,
                        new java.util.UUID(0, 2),
                        new java.util.UUID(0, 3),
                        0,
                        new com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256(
                                "11".repeat(32)
                        ),
                        new com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256(
                                "22".repeat(32)
                        ),
                        0
                )
        );
    }

    private static Map<String, byte[]> loadGoldenFrames() throws IOException {
        try (InputStream input = MinimapWireCodecTest.class.getResourceAsStream(GOLDEN_RESOURCE)) {
            if (input == null) {
                throw new IOException("missing golden resource " + GOLDEN_RESOURCE);
            }
            Map<String, byte[]> values = new LinkedHashMap<>();
            String text = new String(input.readAllBytes(), StandardCharsets.US_ASCII);
            Arrays.stream(text.split("\\R"))
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(line -> {
                        int separator = line.indexOf('=');
                        if (separator <= 0 || separator == line.length() - 1) {
                            throw new IllegalArgumentException("invalid golden line: " + line);
                        }
                        String name = line.substring(0, separator);
                        if (values.put(name, hex(line.substring(separator + 1))) != null) {
                            throw new IllegalArgumentException("duplicate golden opcode: " + name);
                        }
                    });
            return values;
        }
    }

    private static byte[] hex(String value) {
        if ((value.length() & 1) != 0) {
            throw new IllegalArgumentException("odd hex length");
        }
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
