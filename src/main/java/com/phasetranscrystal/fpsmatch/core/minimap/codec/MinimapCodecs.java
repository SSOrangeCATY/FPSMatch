package com.phasetranscrystal.fpsmatch.core.minimap.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.regex.Pattern;

public final class MinimapCodecs {
    private static final Pattern NON_NEGATIVE_LONG_PATTERN = Pattern.compile("0|[1-9][0-9]{0,18}");

    public static final Codec<Long> NON_NEGATIVE_LONG = Codec.STRING.flatXmap(
            MinimapCodecs::decodeNonNegativeLong,
            MinimapCodecs::encodeNonNegativeLong
    );

    private MinimapCodecs() {
    }

    private static DataResult<Long> decodeNonNegativeLong(String value) {
        if (!NON_NEGATIVE_LONG_PATTERN.matcher(value).matches()) {
            return DataResult.error(() -> "Expected a non-negative decimal long string");
        }
        try {
            return DataResult.success(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return DataResult.error(() -> "Non-negative decimal long exceeds Long.MAX_VALUE");
        }
    }

    private static DataResult<String> encodeNonNegativeLong(Long value) {
        if (value < 0) {
            return DataResult.error(() -> "Expected a non-negative long");
        }
        return DataResult.success(Long.toString(value));
    }
}
