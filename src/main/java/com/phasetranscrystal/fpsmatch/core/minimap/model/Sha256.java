package com.phasetranscrystal.fpsmatch.core.minimap.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.regex.Pattern;

public record Sha256(String value) {
    private static final Pattern LOWER_HEX = Pattern.compile("[0-9a-f]{64}");

    public static Codec<Sha256> codec() {
        return CodecHolder.CODEC;
    }

    public Sha256 {
        if (value == null || value.length() != 64 || !LOWER_HEX.matcher(value).matches()) {
            throw new IllegalArgumentException("SHA-256 must be 64 lowercase hexadecimal characters");
        }
    }

    public static Sha256 parse(String value) {
        return new Sha256(value);
    }

    private static DataResult<Sha256> decode(String value) {
        try {
            return DataResult.success(parse(value));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static final class CodecHolder {
        private static final Codec<Sha256> CODEC = Codec.STRING.flatXmap(
                Sha256::decode,
                hash -> DataResult.success(hash.value)
        );
    }

    @Override
    public String toString() {
        return value;
    }
}