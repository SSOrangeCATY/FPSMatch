package com.ptcrys.fpsmatch.core.minimap.wire;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

final class WireText {
    private WireText() {
    }

    static String requireUtf8(String value, int maximumBytes, String label) {
        Objects.requireNonNull(value, label);
        if (maximumBytes < 0) {
            throw new IllegalArgumentException("UTF-8 limit is negative");
        }
        if (value.length() > maximumBytes
                || !StandardCharsets.UTF_8.newEncoder().canEncode(value)
                || value.getBytes(StandardCharsets.UTF_8).length > maximumBytes) {
            throw new IllegalArgumentException(label + " exceeds its UTF-8 limit");
        }
        return value;
    }
}
