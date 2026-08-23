package com.ptcrys.fpsmatch.core.minimap.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;

import java.util.regex.Pattern;

public record ContainerPath(String value) {
    private static final Pattern ALLOWED = Pattern.compile("[a-z0-9._/-]+");

    public static final Codec<ContainerPath> CODEC = Codec.STRING.flatXmap(
            ContainerPath::decode,
            path -> DataResult.success(path.value)
    );

    public ContainerPath {
        if (value == null || value.isEmpty()
                || value.length() > MinimapHardLimits.MAX_ENTRY_PATH_UTF8_BYTES
                || value.startsWith("/") || value.endsWith("/")
                || !ALLOWED.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid canonical container path");
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Invalid canonical container path segment");
            }
        }
    }

    public static ContainerPath parse(String value) {
        return new ContainerPath(value);
    }

    private static DataResult<ContainerPath> decode(String value) {
        try {
            return DataResult.success(parse(value));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
