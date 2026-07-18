package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JcsCanonicalizerTest {
    private static final String FIXTURE_ROOT =
            "/com/phasetranscrystal/fpsmatch/minimap/contract/v1/json/";

    @Test
    void canonicalizesTheRfc8785SampleToExactBytesAndHash() {
        byte[] input = resource("rfc8785-input.json");
        byte[] expected = HexFormat.of().parseHex(
                new String(resource("rfc8785-canonical.utf8.hex"), StandardCharsets.US_ASCII).trim()
        );

        byte[] canonical = JcsCanonicalizer.canonicalize(input);

        assertArrayEquals(expected, canonical);
        assertEquals("2d5e01a318d0f0879ab568c4be289c8b1f64ef8921a53c6277d5e069978baacb",
                sha256(canonical));
        assertArrayEquals(canonical, JcsCanonicalizer.canonicalize(canonical));
    }

    @Test
    void canonicalizesTheFpsmatchGoldenSampleToExactBytesAndHash() {
        byte[] expected = HexFormat.of().parseHex(new String(
                resource("fpsmatch-sample-canonical.utf8.hex"), StandardCharsets.US_ASCII
        ).trim());

        byte[] canonical = JcsCanonicalizer.canonicalize(resource("fpsmatch-sample-input.json"));

        assertArrayEquals(expected, canonical);
        assertEquals("c885b09c24bdff215883c26849cf1b34f9e5ee7611a7391fd59c265e26aa209c",
                sha256(canonical));
    }

    @Test
    void serializesEveryFiniteRfc8785AppendixBNumber() {
        List<NumberVector> vectors = List.of(
                vector("0000000000000000", "0"),
                vector("8000000000000000", "0"),
                vector("0000000000000001", "5e-324"),
                vector("8000000000000001", "-5e-324"),
                vector("7fefffffffffffff", "1.7976931348623157e+308"),
                vector("ffefffffffffffff", "-1.7976931348623157e+308"),
                vector("4340000000000000", "9007199254740992"),
                vector("c340000000000000", "-9007199254740992"),
                vector("4430000000000000", "295147905179352830000"),
                vector("44b52d02c7e14af5", "9.999999999999997e+22"),
                vector("44b52d02c7e14af6", "1e+23"),
                vector("44b52d02c7e14af7", "1.0000000000000001e+23"),
                vector("444b1ae4d6e2ef4e", "999999999999999700000"),
                vector("444b1ae4d6e2ef4f", "999999999999999900000"),
                vector("444b1ae4d6e2ef50", "1e+21"),
                vector("3eb0c6f7a0b5ed8c", "9.999999999999997e-7"),
                vector("3eb0c6f7a0b5ed8d", "0.000001"),
                vector("41b3de4355555553", "333333333.3333332"),
                vector("41b3de4355555554", "333333333.33333325"),
                vector("41b3de4355555555", "333333333.3333333"),
                vector("41b3de4355555556", "333333333.3333334"),
                vector("41b3de4355555557", "333333333.33333343"),
                vector("becbf647612f3696", "-0.0000033333333333333333"),
                vector("43143ff3c1cb0959", "1424953923781206.2")
        );

        for (NumberVector vector : vectors) {
            double value = Double.longBitsToDouble(Long.parseUnsignedLong(vector.bits(), 16));
            assertEquals(vector.expected(), canonicalString(new JsonPrimitive(value)), vector.bits());
        }
    }

    @Test
    void rejectsNonFiniteNumbers() {
        assertThrows(CanonicalJsonException.class,
                () -> JcsCanonicalizer.canonicalize(new JsonPrimitive(Double.NaN)));
        assertThrows(CanonicalJsonException.class,
                () -> JcsCanonicalizer.canonicalize(new JsonPrimitive(Double.POSITIVE_INFINITY)));
        assertThrows(CanonicalJsonException.class,
                () -> JcsCanonicalizer.canonicalize(new JsonPrimitive(Double.NEGATIVE_INFINITY)));
        assertThrows(CanonicalJsonException.class,
                () -> StrictJsonParser.parse(ascii("1e400")));
    }

    @Test
    void sortsObjectPropertiesByRawUtf16CodeUnitsRecursively() {
        JsonObject object = new JsonObject();
        object.addProperty("\u20ac", "Euro Sign");
        object.addProperty("\r", "Carriage Return");
        object.addProperty("\ufffd", "Replacement Character");
        object.addProperty("1", "One");
        object.addProperty("\ud83d\ude00", "Emoji: Grinning Face");
        object.addProperty("\u0080", "Control");
        object.addProperty("\u00f6", "Latin Small Letter O With Diaeresis");
        JsonObject nested = new JsonObject();
        nested.addProperty("z", 1);
        nested.addProperty("a", 2);
        JsonArray array = new JsonArray();
        array.add(nested);
        object.add("array", array);

        assertEquals(
                "{\"\\r\":\"Carriage Return\",\"1\":\"One\",\"array\":[{\"a\":2,\"z\":1}],"
                        + "\"\u0080\":\"Control\",\"\u00f6\":\"Latin Small Letter O With Diaeresis\","
                        + "\"\u20ac\":\"Euro Sign\",\"\ud83d\ude00\":\"Emoji: Grinning Face\","
                        + "\"\ufffd\":\"Replacement Character\"}",
                canonicalString(object)
        );
    }

    @Test
    void emitsOnlyTheRequiredLowercaseStringEscapes() {
        String value = "\b\t\n\f\r\u0000\u000f\"\\/";
        assertEquals("\"\\b\\t\\n\\f\\r\\u0000\\u000f\\\"\\\\/\"",
                canonicalString(new JsonPrimitive(value)));
    }

    @Test
    void strictParserRejectsDuplicateKeysIncludingEscapedAndNullFirstValues() {
        for (String json : List.of(
                "{\"a\":1,\"a\":2}",
                "{\"outer\":{\"a\":1,\"a\":2}}",
                "{\"a\":null,\"\\u0061\":2}"
        )) {
            assertThrows(CanonicalJsonException.class,
                    () -> StrictJsonParser.parse(ascii(json)), json);
        }
    }

    @Test
    void strictParserRejectsInvalidUtf8BomAndNonJsonSyntax() {
        assertThrows(CanonicalJsonException.class,
                () -> StrictJsonParser.parse(new byte[]{(byte) 0xc3, 0x28}));
        assertThrows(CanonicalJsonException.class,
                () -> StrictJsonParser.parse(new byte[]{(byte) 0xef, (byte) 0xbb, (byte) 0xbf, '{', '}'}));
        for (String json : List.of(
                "/* comment */ {}",
                "{'a':1}",
                "{\"a\":01}",
                "{\"a\":1,}",
                "{}{}",
                "TRUE",
                "False",
                "NULL",
                "\"\\'\"",
                "\"\\\n\""
        )) {
            assertThrows(CanonicalJsonException.class,
                    () -> StrictJsonParser.parse(ascii(json)), json);
        }
    }

    @Test
    void strictParserRejectsUnescapedControlCharactersInsideStrings() {
        for (int control : List.of(0x00, 0x09, 0x0a, 0x0d, 0x1f)) {
            String json = "{\"value\":\"" + (char) control + "\"}";
            assertThrows(CanonicalJsonException.class,
                    () -> StrictJsonParser.parse(json.getBytes(StandardCharsets.UTF_8)),
                    "U+" + String.format("%04X", control));
        }
    }

    @Test
    void strictParserAppliesTheNestingLimitToEmptyContainers() {
        assertDoesNotThrow(() -> nestedArrays(256));
        assertThrows(CanonicalJsonException.class,
                () -> StrictJsonParser.parse(ascii("[".repeat(257) + "]".repeat(257))));
    }

    @Test
    void canonicalizerRejectsBeforeExceedingTheRequestedOutputLimit() {
        JsonPrimitive value = new JsonPrimitive("abc");
        assertEquals("\"abc\"", new String(JcsCanonicalizer.canonicalize(value, 5), StandardCharsets.UTF_8));
        assertThrows(CanonicalJsonException.class, () -> JcsCanonicalizer.canonicalize(value, 4));
    }

    @Test
    void canonicalizerRejectsOverdeepAndCyclicInMemoryTrees() {
        JsonArray overdeep = new JsonArray();
        for (int depth = 0; depth < 257; depth++) {
            JsonArray parent = new JsonArray();
            parent.add(overdeep);
            overdeep = parent;
        }
        JsonArray cycle = new JsonArray();
        cycle.add(cycle);

        JsonArray finalOverdeep = overdeep;
        assertThrows(CanonicalJsonException.class, () -> JcsCanonicalizer.canonicalize(finalOverdeep));
        assertThrows(CanonicalJsonException.class, () -> JcsCanonicalizer.canonicalize(cycle));
    }

    @Test
    void rejectsUnmatchedSurrogatesAndNonNfcStringsWithoutNormalizing() {
        assertThrows(CanonicalJsonException.class,
                () -> StrictJsonParser.parse(ascii("\"\\ud800\"")));
        assertThrows(CanonicalJsonException.class,
                () -> StrictJsonParser.parse(ascii("{\"value\":\"e\\u0301\"}")));
        assertThrows(CanonicalJsonException.class,
                () -> JcsCanonicalizer.canonicalize(new JsonPrimitive("\udc00")));
        assertThrows(CanonicalJsonException.class,
                () -> JcsCanonicalizer.canonicalize(new JsonPrimitive("e\u0301")));

        assertEquals("{\"value\":\"\u00e9\"}",
                canonicalString(StrictJsonParser.parse(ascii("{\"value\":\"\\u00e9\"}"))));
    }

    @Test
    void rejectsUnknownCoreFieldsAndPreservesNamespacedExtensions() {
        assertThrows(CanonicalJsonException.class, () -> StrictJsonParser.parseObject(
                ascii("{\"known\":1,\"typo\":2}"), Set.of("known")
        ));
        assertThrows(CanonicalJsonException.class, () -> StrictJsonParser.parseObject(
                ascii("{\"known\":1,\"extensions\":{\"Bad Namespace\":{}}}"), Set.of("known")
        ));

        JsonObject parsed = StrictJsonParser.parseObject(
                ascii("{\"known\":1,\"extensions\":{\"example_mod\":{\"z\":2,\"a\":1}}}"),
                Set.of("known")
        );
        assertEquals(
                "{\"extensions\":{\"example_mod\":{\"a\":1,\"z\":2}},\"known\":1}",
                canonicalString(parsed)
        );
    }

    private static String canonicalString(com.google.gson.JsonElement value) {
        return new String(JcsCanonicalizer.canonicalize(value), StandardCharsets.UTF_8);
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static com.google.gson.JsonElement nestedArrays(int depth) {
        return StrictJsonParser.parse(ascii("[".repeat(depth) + "]".repeat(depth)));
    }

    private static byte[] resource(String name) {
        try (InputStream stream = JcsCanonicalizerTest.class.getResourceAsStream(FIXTURE_ROOT + name)) {
            if (stream == null) {
                throw new AssertionError("Missing test fixture: " + name);
            }
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new AssertionError("Failed to read test fixture: " + name, exception);
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static NumberVector vector(String bits, String expected) {
        return new NumberVector(bits, expected);
    }

    private record NumberVector(String bits, String expected) {
    }
}
