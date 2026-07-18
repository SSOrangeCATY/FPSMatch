package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalModelJsonTest {
    private static final Codec<Sample> SAMPLE = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("known").forGetter(Sample::known),
            Nested.CODEC.fieldOf("nested").forGetter(Sample::nested)
    ).apply(instance, Sample::new));

    @Test
    void canonicalInputAndCodecRoundTripProduceIdenticalBytes() {
        byte[] input = utf8("{\"known\":1,\"nested\":{\"value\":\"x\"}}");

        CanonicalModelJson.Document<Sample> document = CanonicalModelJson.read(input, SAMPLE);

        assertEquals(new Sample(1, new Nested("x")), document.value());
        assertArrayEquals(input, CanonicalModelJson.write(document, SAMPLE));
    }

    @Test
    void rejectsNonCanonicalInputBeforeCodecDecode() {
        assertThrows(CanonicalModelJsonException.class, () -> CanonicalModelJson.read(
                utf8("{ \"nested\":{\"value\":\"x\"},\"known\":1 }"), SAMPLE
        ));
    }

    @Test
    void rejectsUnknownRootCoreFieldEvenWhenCodecIgnoresIt() {
        assertThrows(CanonicalModelJsonException.class, () -> CanonicalModelJson.read(
                utf8("{\"known\":1,\"nested\":{\"value\":\"x\"},\"unknown\":2}"), SAMPLE
        ));
    }

    @Test
    void rejectsUnknownNestedCoreFieldAfterCodecRoundTrip() {
        assertThrows(CanonicalModelJsonException.class, () -> CanonicalModelJson.read(
                utf8("{\"known\":1,\"nested\":{\"unknown\":2,\"value\":\"x\"}}"), SAMPLE
        ));
    }

    @Test
    void preservesNamespacedExtensionsAndDistinguishesMissingFromEmpty() {
        byte[] without = utf8("{\"known\":1,\"nested\":{\"value\":\"x\"}}");
        byte[] empty = utf8("{\"extensions\":{},\"known\":1,\"nested\":{\"value\":\"x\"}}");
        byte[] named = utf8("{\"extensions\":{\"example_mod\":{\"a\":1,\"z\":2}},\"known\":1,\"nested\":{\"value\":\"x\"}}");

        CanonicalModelJson.Document<Sample> missing = CanonicalModelJson.read(without, SAMPLE);
        CanonicalModelJson.Document<Sample> emptyDocument = CanonicalModelJson.read(empty, SAMPLE);
        CanonicalModelJson.Document<Sample> namedDocument = CanonicalModelJson.read(named, SAMPLE);

        assertFalse(missing.extensions().isPresent());
        assertTrue(emptyDocument.extensions().isPresent());
        assertTrue(emptyDocument.extensions().isEmpty());
        assertEquals(Set.of("example_mod"), namedDocument.extensions().namespaces());
        assertArrayEquals(without, CanonicalModelJson.write(missing, SAMPLE));
        assertArrayEquals(empty, CanonicalModelJson.write(emptyDocument, SAMPLE));
        assertArrayEquals(named, CanonicalModelJson.write(namedDocument, SAMPLE));
    }

    @Test
    void extensionAndByteAccessorsAreDefensivelyCopied() {
        byte[] input = utf8("{\"extensions\":{\"example_mod\":{\"value\":1}},\"known\":1,\"nested\":{\"value\":\"x\"}}");
        CanonicalModelJson.Document<Sample> document = CanonicalModelJson.read(input, SAMPLE);

        byte[] first = document.extensions().canonicalBytes();
        first[0] = (byte) (first[0] ^ 0x7f);
        assertArrayEquals(
                utf8("{\"example_mod\":{\"value\":1}}"),
                document.extensions().canonicalBytes()
        );

        JsonObject object = document.extensions().asJsonObject();
        object.addProperty("mutated", true);
        assertFalse(document.extensions().asJsonObject().has("mutated"));
        assertArrayEquals(input, CanonicalModelJson.write(document, SAMPLE));
        assertNotSame(first, document.extensions().canonicalBytes());
    }

    @Test
    void rejectsCodecDecodePartialResults() {
        Codec<Sample> partial = SAMPLE.flatXmap(
                value -> DataResult.error(() -> "partial decode", value),
                DataResult::success
        );

        assertThrows(CanonicalModelJsonException.class, () -> CanonicalModelJson.read(
                utf8("{\"known\":1,\"nested\":{\"value\":\"x\"}}"), partial
        ));
    }

    @Test
    void rejectsCodecEncodePartialResults() {
        Codec<Sample> partial = SAMPLE.flatXmap(
                DataResult::success,
                value -> DataResult.error(() -> "partial encode", value)
        );

        assertThrows(CanonicalModelJsonException.class, () -> CanonicalModelJson.write(
                new Sample(1, new Nested("x")), partial, PreservedExtensions.missing()
        ));
    }

    @Test
    void explicitAllowedRootFieldsRejectUnknownFieldsEarly() {
        assertThrows(CanonicalModelJsonException.class, () -> CanonicalModelJson.read(
                utf8("{\"known\":1,\"nested\":{\"value\":\"x\"}}"),
                MapCodec.unitCodec(new Sample(1, new Nested("x"))),
                Set.of("known")
        ));
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private record Sample(int known, Nested nested) {
    }

    private record Nested(String value) {
        private static final Codec<Nested> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("value").forGetter(Nested::value)
        ).apply(instance, Nested::new));
    }
}
