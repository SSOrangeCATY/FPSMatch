package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class StrictJsonParser {
    private static final int MAX_DEPTH = 256;
    private static final Pattern JSON_NUMBER = Pattern.compile(
            "-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?"
    );
    private static final Pattern EXTENSION_NAMESPACE = Pattern.compile("[a-z0-9_.-]+");

    private StrictJsonParser() {
    }

    public static JsonElement parse(byte[] utf8) {
        Objects.requireNonNull(utf8, "utf8");
        if (utf8.length > MinimapHardLimits.MAX_JSON_ENTRY_BYTES) {
            throw new CanonicalJsonException("JSON input exceeds the hard byte limit");
        }

        String text = decodeUtf8(utf8);
        if (!text.isEmpty() && text.charAt(0) == '\ufeff') {
            throw new CanonicalJsonException("JSON input must not contain a UTF-8 BOM");
        }
        validateLexemes(text);

        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            reader.setNestingLimit(MAX_DEPTH);
            JsonElement value = readValue(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new CanonicalJsonException("JSON input contains trailing data");
            }
            return value;
        } catch (CanonicalJsonException exception) {
            throw exception;
        } catch (IOException | IllegalStateException | NumberFormatException exception) {
            throw new CanonicalJsonException("Invalid JSON input", exception);
        }
    }

    public static JsonObject parseObject(byte[] utf8, Set<String> allowedCoreFields) {
        Objects.requireNonNull(allowedCoreFields, "allowedCoreFields");
        JsonElement value = parse(utf8);
        if (!value.isJsonObject()) {
            throw new CanonicalJsonException("Expected a JSON object");
        }

        JsonObject object = value.getAsJsonObject();
        for (String key : object.keySet()) {
            if (!key.equals("extensions") && !allowedCoreFields.contains(key)) {
                throw new CanonicalJsonException("Unknown core field: " + key);
            }
        }
        if (object.has("extensions")) {
            JsonElement extensions = object.get("extensions");
            if (!extensions.isJsonObject()) {
                throw new CanonicalJsonException("extensions must be an object");
            }
            for (String namespace : extensions.getAsJsonObject().keySet()) {
                if (namespace.length() > MinimapHardLimits.MAX_NAMESPACE_UTF8_BYTES
                        || !EXTENSION_NAMESPACE.matcher(namespace).matches()) {
                    throw new CanonicalJsonException("Invalid extension namespace");
                }
            }
        }
        return object;
    }

    static void requireCanonicalString(String value) {
        Objects.requireNonNull(value, "value");
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new CanonicalJsonException("JSON string contains an unmatched high surrogate");
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                throw new CanonicalJsonException("JSON string contains an unmatched low surrogate");
            }
        }
        if (!Normalizer.isNormalized(value, Normalizer.Form.NFC)) {
            throw new CanonicalJsonException("JSON string is not NFC-normalized");
        }
    }

    private static String decodeUtf8(byte[] utf8) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(utf8))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new CanonicalJsonException("JSON input is not valid UTF-8", exception);
        }
    }

    private static void validateLexemes(String text) {
        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (isJsonWhitespace(current) || isStructural(current)) {
                index++;
            } else if (current == '"') {
                index = scanString(text, index);
            } else if (current == 't') {
                index = scanLiteral(text, index, "true");
            } else if (current == 'f') {
                index = scanLiteral(text, index, "false");
            } else if (current == 'n') {
                index = scanLiteral(text, index, "null");
            } else if (current == '-' || current >= '0' && current <= '9') {
                int start = index++;
                while (index < text.length() && !isDelimiter(text.charAt(index))) {
                    index++;
                }
                if (!JSON_NUMBER.matcher(text.substring(start, index)).matches()) {
                    throw new CanonicalJsonException("Invalid JSON number");
                }
            } else {
                throw new CanonicalJsonException("Invalid JSON token");
            }
        }
    }

    private static int scanString(String text, int quoteIndex) {
        int index = quoteIndex + 1;
        while (index < text.length()) {
            char current = text.charAt(index++);
            if (current == '"') {
                return index;
            }
            if (current < 0x20) {
                throw new CanonicalJsonException("JSON string contains an unescaped control character");
            }
            if (current != '\\') {
                continue;
            }
            if (index >= text.length()) {
                throw new CanonicalJsonException("JSON string ends inside an escape sequence");
            }
            char escaped = text.charAt(index++);
            if (escaped == 'u') {
                if (index + 4 > text.length()) {
                    throw new CanonicalJsonException("JSON Unicode escape is truncated");
                }
                for (int offset = 0; offset < 4; offset++) {
                    if (!isHexDigit(text.charAt(index + offset))) {
                        throw new CanonicalJsonException("JSON Unicode escape contains a non-hex digit");
                    }
                }
                index += 4;
            } else if ("\"\\/bfnrt".indexOf(escaped) < 0) {
                throw new CanonicalJsonException("JSON string contains an invalid escape sequence");
            }
        }
        throw new CanonicalJsonException("JSON string is not terminated");
    }

    private static int scanLiteral(String text, int start, String literal) {
        int end = start + literal.length();
        if (end > text.length() || !text.regionMatches(start, literal, 0, literal.length())
                || end < text.length() && !isDelimiter(text.charAt(end))) {
            throw new CanonicalJsonException("Invalid JSON literal");
        }
        return end;
    }

    private static boolean isJsonWhitespace(char value) {
        return value == ' ' || value == '\t' || value == '\n' || value == '\r';
    }

    private static boolean isStructural(char value) {
        return value == '{' || value == '}' || value == '[' || value == ']'
                || value == ',' || value == ':';
    }

    private static boolean isDelimiter(char value) {
        return isJsonWhitespace(value) || isStructural(value);
    }

    private static boolean isHexDigit(char value) {
        return value >= '0' && value <= '9'
                || value >= 'a' && value <= 'f'
                || value >= 'A' && value <= 'F';
    }

    private static JsonElement readValue(JsonReader reader, int depth) throws IOException {
        JsonToken token = reader.peek();
        if (depth >= MAX_DEPTH && (token == JsonToken.BEGIN_OBJECT || token == JsonToken.BEGIN_ARRAY)) {
            throw new CanonicalJsonException("JSON nesting exceeds the hard depth limit");
        }
        return switch (token) {
            case BEGIN_OBJECT -> readObject(reader, depth + 1);
            case BEGIN_ARRAY -> readArray(reader, depth + 1);
            case STRING -> {
                String value = reader.nextString();
                requireCanonicalString(value);
                yield new JsonPrimitive(value);
            }
            case NUMBER -> readNumber(reader.nextString());
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw new CanonicalJsonException("Expected a JSON value");
        };
    }

    private static JsonObject readObject(JsonReader reader, int depth) throws IOException {
        reader.beginObject();
        JsonObject object = new JsonObject();
        Set<String> names = new HashSet<>();
        while (reader.hasNext()) {
            String name = reader.nextName();
            requireCanonicalString(name);
            if (!names.add(name)) {
                throw new CanonicalJsonException("Duplicate JSON object key: " + name);
            }
            object.add(name, readValue(reader, depth));
        }
        reader.endObject();
        return object;
    }

    private static JsonArray readArray(JsonReader reader, int depth) throws IOException {
        reader.beginArray();
        JsonArray array = new JsonArray();
        while (reader.hasNext()) {
            array.add(readValue(reader, depth));
        }
        reader.endArray();
        return array;
    }

    private static JsonPrimitive readNumber(String lexical) {
        if (!JSON_NUMBER.matcher(lexical).matches()) {
            throw new CanonicalJsonException("Invalid JSON number");
        }
        double value = Double.parseDouble(lexical);
        if (!Double.isFinite(value)) {
            throw new CanonicalJsonException("JSON number is outside the finite IEEE 754 range");
        }
        return new JsonPrimitive(value);
    }
}
