package com.phasetranscrystal.fpsmatch.core.minimap.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.regex.Pattern;

public record MapKey(String gameType, String mapName) {
    private static final Pattern RESOURCE_NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern RESOURCE_PATH = Pattern.compile("[a-z0-9/._-]+");

    /**
     * Codec is constructed lazily so pure MapKey construction does not require Mojang DFU on the classpath.
     * Prefer {@link #codec()} over a static field for that reason.
     */
    public static Codec<MapKey> codec() {
        return CodecHolder.CODEC;
    }

    public MapKey {
        requireGameType(gameType);
        requireValid(mapName, "mapName", MinimapHardLimits.MAX_MAP_NAME_UTF8_BYTES);
    }

    private static final class CodecHolder {
        private static final Codec<String> GAME_TYPE_CODEC = Codec.STRING.flatXmap(
                MapKey::validateGameType,
                MapKey::validateGameType
        );
        private static final Codec<String> MAP_NAME_CODEC = validatedString(
                "mapName", MinimapHardLimits.MAX_MAP_NAME_UTF8_BYTES
        );
        private static final Codec<MapKey> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                GAME_TYPE_CODEC.fieldOf("gameType").forGetter(MapKey::gameType),
                MAP_NAME_CODEC.fieldOf("mapName").forGetter(MapKey::mapName)
        ).apply(instance, MapKey::new));
    }

    private static DataResult<String> validateGameType(String value) {
        try {
            requireGameType(value);
            return DataResult.success(value);
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static void requireGameType(String value) {
        if (value == null || value.isEmpty() || value.length() > MinimapHardLimits.MAX_GAME_TYPE_UTF8_BYTES) {
            throw new IllegalArgumentException("gameType is empty or too long");
        }
        int separator = value.indexOf(':');
        if (separator < 0) {
            if (!RESOURCE_PATH.matcher(value).matches()) {
                throw new IllegalArgumentException("gameType must be a lowercase resource ID");
            }
            return;
        }
        if (separator == 0 || separator != value.lastIndexOf(':') || separator == value.length() - 1
                || !RESOURCE_NAMESPACE.matcher(value.substring(0, separator)).matches()
                || !RESOURCE_PATH.matcher(value.substring(separator + 1)).matches()) {
            throw new IllegalArgumentException("gameType must be a lowercase resource ID");
        }
    }

    private static Codec<String> validatedString(String label, int maxUtf8Bytes) {
        return Codec.STRING.flatXmap(
                value -> validate(value, label, maxUtf8Bytes),
                value -> validate(value, label, maxUtf8Bytes)
        );
    }

    private static DataResult<String> validate(String value, String label, int maxUtf8Bytes) {
        try {
            requireValid(value, label, maxUtf8Bytes);
            return DataResult.success(value);
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static void requireValid(String value, String label, int maxUtf8Bytes) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be empty");
        }
        if (value.length() > maxUtf8Bytes) {
            throw new IllegalArgumentException(label + " exceeds its UTF-8 byte limit");
        }
        if (!Normalizer.isNormalized(value, Normalizer.Form.NFC)) {
            throw new IllegalArgumentException(label + " must already be NFC normalized");
        }
        validateUnicodeScalarsAndControls(value, label);
        if (value.getBytes(StandardCharsets.UTF_8).length > maxUtf8Bytes) {
            throw new IllegalArgumentException(label + " exceeds its UTF-8 byte limit");
        }
    }

    private static void validateUnicodeScalarsAndControls(String value, String label) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            int codePoint;
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new IllegalArgumentException(label + " contains an unpaired surrogate");
                }
                codePoint = Character.toCodePoint(current, value.charAt(++index));
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException(label + " contains an unpaired surrogate");
            } else {
                codePoint = current;
            }
            if (Character.isISOControl(codePoint)) {
                throw new IllegalArgumentException(label + " contains an ISO control character");
            }
        }
    }
}