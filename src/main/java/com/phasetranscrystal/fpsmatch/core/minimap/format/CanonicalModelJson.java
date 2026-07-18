package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Strict authority JSON adapter for model Codecs.
 *
 * <p>DFU codecs intentionally ignore unknown map keys in many record
 * codecs. This adapter therefore requires a complete decode/encode round
 * trip and compares canonical core bytes, while keeping the reserved root
 * {@code extensions} object in an immutable sidecar.</p>
 */
public final class CanonicalModelJson {
    private CanonicalModelJson() {
    }

    public static <T> Document<T> read(byte[] utf8, Codec<T> codec) {
        return read(utf8, codec, null);
    }

    public static <T> Document<T> read(byte[] utf8, Codec<T> codec, Set<String> allowedCoreFields) {
        Objects.requireNonNull(utf8, "utf8");
        Objects.requireNonNull(codec, "codec");

        JsonElement parsed;
        try {
            parsed = StrictJsonParser.parse(utf8);
        } catch (CanonicalJsonException exception) {
            throw modelError("Invalid model JSON", exception);
        }
        if (!parsed.isJsonObject()) {
            throw modelError("Model JSON root must be an object");
        }

        byte[] canonicalWhole;
        try {
            canonicalWhole = JcsCanonicalizer.canonicalize(parsed);
        } catch (CanonicalJsonException exception) {
            throw modelError("Unable to canonicalize model JSON", exception);
        }
        if (!Arrays.equals(utf8, canonicalWhole)) {
            throw modelError("Model JSON is not JCS canonical");
        }

        JsonObject root = parsed.getAsJsonObject();
        PreservedExtensions extensions = PreservedExtensions.fromRoot(root);
        JsonObject core = root.deepCopy();
        core.remove("extensions");

        Set<String> allowed = copyAllowedFields(allowedCoreFields);
        if (allowed != null) {
            for (String key : core.keySet()) {
                if (!allowed.contains(key)) {
                    throw modelError("Unknown core field: " + key);
                }
            }
        }

        byte[] canonicalCore;
        try {
            canonicalCore = JcsCanonicalizer.canonicalize(core);
        } catch (CanonicalJsonException exception) {
            throw modelError("Unable to canonicalize model core", exception);
        }

        T value = decode(codec, core);
        JsonObject encoded = encodeObject(codec, value);
        byte[] encodedCore = canonicalizeEncodedCore(encoded);
        if (!Arrays.equals(canonicalCore, encodedCore)) {
            throw modelError("Codec does not losslessly round-trip model core fields");
        }
        return new Document<>(value, extensions);
    }

    public static <T> Document<T> decode(byte[] utf8, Codec<T> codec) {
        return read(utf8, codec);
    }

    public static <T> Document<T> decode(byte[] utf8, Codec<T> codec, Set<String> allowedCoreFields) {
        return read(utf8, codec, allowedCoreFields);
    }

    public static <T> Document<T> read(Codec<T> codec, byte[] utf8) {
        return read(utf8, codec);
    }

    public static <T> Document<T> decode(Codec<T> codec, byte[] utf8) {
        return read(utf8, codec);
    }

    public static <T> byte[] write(Document<T> document, Codec<T> codec) {
        Objects.requireNonNull(document, "document");
        return write(document.value(), codec, document.extensions());
    }

    public static <T> byte[] encode(Document<T> document, Codec<T> codec) {
        return write(document, codec);
    }

    public static <T> byte[] write(T value, Codec<T> codec, PreservedExtensions extensions) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(extensions, "extensions");

        JsonObject root = encodeObject(codec, value);
        if (root.has("extensions")) {
            throw modelError("Codec output must not contain reserved root extensions");
        }
        byte[] canonicalCore = canonicalizeEncodedCore(root);
        JsonElement detached = StrictJsonParser.parse(canonicalCore);
        if (!detached.isJsonObject()) {
            throw modelError("Model Codec must encode a JSON object");
        }
        root = detached.getAsJsonObject();
        if (extensions.isPresent()) {
            root.add("extensions", extensions.asJsonObject());
        }
        try {
            return JcsCanonicalizer.canonicalize(root);
        } catch (CanonicalJsonException exception) {
            throw modelError("Unable to canonicalize model output", exception);
        }
    }

    public static <T> byte[] encode(T value, Codec<T> codec, PreservedExtensions extensions) {
        return write(value, codec, extensions);
    }

    public static <T> byte[] write(T value, Codec<T> codec) {
        return write(value, codec, PreservedExtensions.missing());
    }

    public static <T> byte[] encode(T value, Codec<T> codec) {
        return write(value, codec);
    }

    private static <T> T decode(Codec<T> codec, JsonObject core) {
        DataResult<T> result;
        try {
            result = codec.parse(JsonOps.INSTANCE, core);
        } catch (RuntimeException exception) {
            throw modelError("Codec decode threw an exception", exception);
        }
        if (result.error().isPresent()) {
            throw modelError("Codec decode failed: " + result.error().orElseThrow().message());
        }
        return result.result().orElseThrow(() -> modelError("Codec decode produced no result"));
    }

    private static <T> JsonObject encodeObject(Codec<T> codec, T value) {
        DataResult<JsonElement> result;
        try {
            result = codec.encodeStart(JsonOps.INSTANCE, value);
        } catch (RuntimeException exception) {
            throw modelError("Codec encode threw an exception", exception);
        }
        if (result.error().isPresent()) {
            throw modelError("Codec encode failed: " + result.error().orElseThrow().message());
        }
        JsonElement encoded = result.result()
                .orElseThrow(() -> modelError("Codec encode produced no result"));
        if (!encoded.isJsonObject()) {
            throw modelError("Model Codec must encode a JSON object");
        }
        return encoded.getAsJsonObject();
    }

    private static byte[] canonicalizeEncodedCore(JsonObject encoded) {
        if (encoded.has("extensions")) {
            throw modelError("Codec output must not contain reserved root extensions");
        }
        try {
            return JcsCanonicalizer.canonicalize(encoded);
        } catch (CanonicalJsonException exception) {
            throw modelError("Unable to canonicalize Codec output", exception);
        }
    }

    private static Set<String> copyAllowedFields(Set<String> allowedCoreFields) {
        if (allowedCoreFields == null) {
            return null;
        }
        Set<String> copy = new LinkedHashSet<>();
        for (String field : allowedCoreFields) {
            if (field == null || field.equals("extensions")) {
                throw modelError("Allowed core fields may not contain null or reserved extensions");
            }
            copy.add(field);
        }
        return Set.copyOf(copy);
    }

    private static CanonicalModelJsonException modelError(String message) {
        return new CanonicalModelJsonException(message);
    }

    private static CanonicalModelJsonException modelError(String message, Throwable cause) {
        return new CanonicalModelJsonException(message, cause);
    }

    /** Immutable decoded model plus its root extension sidecar. */
    public static final class Document<T> {
        private final T value;
        private final PreservedExtensions extensions;

        public Document(T value, PreservedExtensions extensions) {
            this.value = Objects.requireNonNull(value, "value");
            this.extensions = Objects.requireNonNull(extensions, "extensions");
        }

        public T value() {
            return value;
        }

        public T model() {
            return value;
        }

        public PreservedExtensions extensions() {
            return extensions;
        }

        public PreservedExtensions preservedExtensions() {
            return extensions;
        }
    }
}
