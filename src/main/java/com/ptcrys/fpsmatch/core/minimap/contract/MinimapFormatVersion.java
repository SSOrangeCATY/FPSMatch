package com.ptcrys.fpsmatch.core.minimap.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record MinimapFormatVersion(int major, int minor) {
    private static final Pattern VERSION_PATTERN = Pattern.compile("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)");

    /**
     * Codec is constructed lazily so pure version construction does not require Mojang DFU on the classpath.
     */
    public static Codec<MinimapFormatVersion> codec() {
        return CodecHolder.CODEC;
    }

    public MinimapFormatVersion {
        if (major < 0 || minor < 0) {
            throw new IllegalArgumentException("Format version components must be non-negative");
        }
    }

    public static MinimapFormatVersion parse(String value) {
        if (value == null || value.length() > MinimapHardLimits.MAX_FORMAT_VERSION_UTF8_BYTES) {
            throw new IllegalArgumentException("Invalid format version length");
        }
        Matcher matcher = VERSION_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Format version must be major.minor decimal without padding");
        }
        try {
            return new MinimapFormatVersion(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2))
            );
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Format version component is too large", exception);
        }
    }

    private static DataResult<MinimapFormatVersion> decode(String value) {
        try {
            return DataResult.success(parse(value));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static final class CodecHolder {
        private static final Codec<MinimapFormatVersion> CODEC = Codec.STRING.flatXmap(
                MinimapFormatVersion::decode,
                version -> DataResult.success(version.toString())
        );
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }
}