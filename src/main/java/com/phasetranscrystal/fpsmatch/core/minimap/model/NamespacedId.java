package com.phasetranscrystal.fpsmatch.core.minimap.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;

import java.util.regex.Pattern;

public record NamespacedId(String namespace, String path) {
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH_PATTERN = Pattern.compile("[a-z0-9/._-]+");

    public static Codec<NamespacedId> codec() {
        return CodecHolder.CODEC;
    }

    public NamespacedId {
        requireComponent(namespace, "namespace", MinimapHardLimits.MAX_NAMESPACE_UTF8_BYTES, NAMESPACE_PATTERN);
        requireComponent(path, "path", MinimapHardLimits.MAX_NAMESPACED_PATH_UTF8_BYTES, PATH_PATTERN);
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Invalid namespaced path segment");
            }
        }
    }

    public static NamespacedId parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Namespaced ID cannot be null");
        }
        int separator = value.indexOf(':');
        if (separator <= 0 || separator != value.lastIndexOf(':') || separator == value.length() - 1) {
            throw new IllegalArgumentException("Namespaced ID must contain exactly one namespace separator");
        }
        return new NamespacedId(value.substring(0, separator), value.substring(separator + 1));
    }

    private static DataResult<NamespacedId> decode(String value) {
        try {
            return DataResult.success(parse(value));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static void requireComponent(String value, String label, int maxBytes, Pattern pattern) {
        if (value == null || value.length() > maxBytes || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid namespaced ID " + label);
        }
    }

    private static final class CodecHolder {
        private static final Codec<NamespacedId> CODEC = Codec.STRING.flatXmap(
                NamespacedId::decode,
                id -> DataResult.success(id.toString())
        );
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}