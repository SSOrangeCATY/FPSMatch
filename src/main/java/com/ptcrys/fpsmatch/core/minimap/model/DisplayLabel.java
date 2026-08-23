package com.ptcrys.fpsmatch.core.minimap.model;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Objects;
import java.util.regex.Pattern;

public record DisplayLabel(Type type, String value) {
    private static final int MAX_UTF8_BYTES = 512;
    private static final Pattern TRANSLATION_KEY = Pattern.compile("[a-z0-9_.-]+");

    public DisplayLabel {
        Objects.requireNonNull(type, "type");
        requireText(value);
        if (type == Type.TRANSLATION && !TRANSLATION_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid translation key");
        }
    }

    public static DisplayLabel literal(String value) {
        return new DisplayLabel(Type.LITERAL, value);
    }

    public static DisplayLabel translation(String value) {
        return new DisplayLabel(Type.TRANSLATION, value);
    }

    private static void requireText(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_UTF8_BYTES) {
            throw new IllegalArgumentException("Display label is empty or too long");
        }
        if (!Normalizer.isNormalized(value, Normalizer.Form.NFC)) {
            throw new IllegalArgumentException("Display label must already be NFC normalized");
        }
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            int codePoint;
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new IllegalArgumentException("Display label contains an unpaired surrogate");
                }
                codePoint = Character.toCodePoint(current, value.charAt(++index));
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException("Display label contains an unpaired surrogate");
            } else {
                codePoint = current;
            }
            if (Character.isISOControl(codePoint)) {
                throw new IllegalArgumentException("Display label contains an ISO control character");
            }
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
            throw new IllegalArgumentException("Display label exceeds its UTF-8 byte limit");
        }
    }

    public enum Type {
        LITERAL,
        TRANSLATION
    }
}
