package com.ptcrys.fpsmatch.core.minimap.format;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import org.erdtman.jcs.NumberToJSON;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class JcsCanonicalizer {
    private static final int MAX_DEPTH = 256;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private JcsCanonicalizer() {
    }

    public static byte[] canonicalize(byte[] utf8) {
        return canonicalize(StrictJsonParser.parse(utf8), MinimapHardLimits.MAX_JSON_ENTRY_BYTES);
    }

    public static byte[] canonicalize(byte[] utf8, long maxBytes) {
        return canonicalize(StrictJsonParser.parse(utf8), maxBytes);
    }

    public static byte[] canonicalize(JsonElement value) {
        return canonicalize(value, MinimapHardLimits.MAX_JSON_ENTRY_BYTES);
    }

    public static byte[] canonicalize(JsonElement value, long maxBytes) {
        Objects.requireNonNull(value, "value");
        if (maxBytes < 0 || maxBytes > MinimapHardLimits.MAX_JSON_ENTRY_BYTES) {
            throw new IllegalArgumentException("Canonical JSON limit is outside the supported range");
        }
        CanonicalOutput output = new CanonicalOutput(maxBytes);
        append(value, output, 0, new IdentityHashMap<>());
        return output.toByteArray();
    }

    private static void append(
            JsonElement value,
            CanonicalOutput output,
            int depth,
            IdentityHashMap<JsonElement, Boolean> ancestors
    ) {
        if (value.isJsonNull()) {
            output.appendAscii("null");
        } else if (value.isJsonObject()) {
            enterContainer(value, depth, ancestors);
            try {
                appendObject(value.getAsJsonObject(), output, depth, ancestors);
            } finally {
                ancestors.remove(value);
            }
        } else if (value.isJsonArray()) {
            enterContainer(value, depth, ancestors);
            try {
                output.appendAscii('[');
                for (int index = 0; index < value.getAsJsonArray().size(); index++) {
                    if (index > 0) {
                        output.appendAscii(',');
                    }
                    append(value.getAsJsonArray().get(index), output, depth + 1, ancestors);
                }
                output.appendAscii(']');
            } finally {
                ancestors.remove(value);
            }
        } else {
            appendPrimitive(value.getAsJsonPrimitive(), output);
        }
    }

    private static void enterContainer(
            JsonElement value,
            int depth,
            IdentityHashMap<JsonElement, Boolean> ancestors
    ) {
        if (depth >= MAX_DEPTH) {
            throw new CanonicalJsonException("JSON nesting exceeds the hard depth limit");
        }
        if (ancestors.put(value, Boolean.TRUE) != null) {
            throw new CanonicalJsonException("JSON tree contains a reference cycle");
        }
    }

    private static void appendObject(
            JsonObject object,
            CanonicalOutput output,
            int depth,
            IdentityHashMap<JsonElement, Boolean> ancestors
    ) {
        List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(object.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        output.appendAscii('{');
        for (int index = 0; index < entries.size(); index++) {
            if (index > 0) {
                output.appendAscii(',');
            }
            Map.Entry<String, JsonElement> entry = entries.get(index);
            appendString(entry.getKey(), output);
            output.appendAscii(':');
            append(entry.getValue(), output, depth + 1, ancestors);
        }
        output.appendAscii('}');
    }

    private static void appendPrimitive(JsonPrimitive primitive, CanonicalOutput output) {
        if (primitive.isBoolean()) {
            output.appendAscii(Boolean.toString(primitive.getAsBoolean()));
        } else if (primitive.isString()) {
            appendString(primitive.getAsString(), output);
        } else if (primitive.isNumber()) {
            double value = primitive.getAsDouble();
            try {
                output.appendAscii(NumberToJSON.serializeNumber(value));
            } catch (IOException exception) {
                throw new CanonicalJsonException("JSON number is not finite", exception);
            }
        } else {
            throw new CanonicalJsonException("Unsupported JSON primitive");
        }
    }

    private static void appendString(String value, CanonicalOutput output) {
        StrictJsonParser.requireCanonicalString(value);
        output.appendAscii('"');
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> output.appendAscii("\\\"");
                case '\\' -> output.appendAscii("\\\\");
                case '\b' -> output.appendAscii("\\b");
                case '\t' -> output.appendAscii("\\t");
                case '\n' -> output.appendAscii("\\n");
                case '\f' -> output.appendAscii("\\f");
                case '\r' -> output.appendAscii("\\r");
                default -> {
                    if (current < 0x20) {
                        output.appendAscii("\\u00");
                        output.appendAscii(HEX[(current >>> 4) & 0x0f]);
                        output.appendAscii(HEX[current & 0x0f]);
                    } else if (Character.isHighSurrogate(current)) {
                        output.appendSurrogatePair(current, value.charAt(++index));
                    } else {
                        output.appendUtf8(current);
                    }
                }
            }
        }
        output.appendAscii('"');
    }

    private static final class CanonicalOutput {
        private final StringBuilder value = new StringBuilder();
        private final long maxBytes;
        private long utf8Bytes;

        private CanonicalOutput(long maxBytes) {
            this.maxBytes = maxBytes;
        }

        private void appendAscii(char character) {
            reserve(1);
            value.append(character);
        }

        private void appendAscii(String text) {
            reserve(text.length());
            value.append(text);
        }

        private void appendUtf8(char character) {
            reserve(character <= 0x7f ? 1 : character <= 0x7ff ? 2 : 3);
            value.append(character);
        }

        private void appendSurrogatePair(char high, char low) {
            reserve(4);
            value.append(high).append(low);
        }

        private void reserve(long bytes) {
            if (bytes > maxBytes - utf8Bytes) {
                throw new CanonicalJsonException("Canonical JSON exceeds the requested byte limit");
            }
            utf8Bytes += bytes;
        }

        private byte[] toByteArray() {
            byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length != utf8Bytes) {
                throw new IllegalStateException("Canonical JSON UTF-8 accounting mismatch");
            }
            return bytes;
        }
    }
}
