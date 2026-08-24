package com.ptcrys.fpsmatch.core.minimap.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ptcrys.fpsmatch.core.minimap.codec.MinimapCodecs;
import com.ptcrys.fpsmatch.core.minimap.format.JcsCanonicalizer;
import com.ptcrys.fpsmatch.core.minimap.format.StrictJsonParser;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.mojang.serialization.JsonOps;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

public record CurrentPointer(
        long expectedBaseRevision,
        long revision,
        Sha256 descriptorChecksum
) {
    static final CurrentPointer RESET_TOMBSTONE = new CurrentPointer(
            0, 0, Sha256.parse("0".repeat(64))
    );
    private static final Set<String> FIELDS = Set.of(
            "descriptorChecksum", "expectedBaseRevision", "revision"
    );

    public CurrentPointer {
        if (expectedBaseRevision < 0) {
            throw new IllegalArgumentException("Expected base revision must be non-negative");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("Current revision must be non-negative");
        }
        if (revision < expectedBaseRevision) {
            throw new IllegalArgumentException("Current revision cannot precede its expected base");
        }
        if (descriptorChecksum == null) {
            throw new NullPointerException("descriptorChecksum");
        }
    }

    static void requireResetTombstone(CurrentPointer pointer) {
        if (!RESET_TOMBSTONE.equals(pointer)) {
            throw new ContainerStorageException("CURRENT reset tombstone is invalid");
        }
    }

    public byte[] canonicalBytes() {
        JsonObject root = new JsonObject();
        root.addProperty("descriptorChecksum", descriptorChecksum.value());
        root.addProperty("expectedBaseRevision", Long.toString(expectedBaseRevision));
        root.addProperty("revision", Long.toString(revision));
        return JcsCanonicalizer.canonicalize(root);
    }

    public static CurrentPointer read(byte[] bytes) {
        if (bytes == null) {
            throw new ContainerStorageException("CURRENT pointer bytes are null");
        }
        try {
            JsonElement parsed = StrictJsonParser.parse(bytes);
            if (!parsed.isJsonObject()
                    || !Arrays.equals(bytes, JcsCanonicalizer.canonicalize(parsed))) {
                throw new ContainerStorageException("CURRENT pointer is not canonical JSON");
            }
            JsonObject root = parsed.getAsJsonObject();
            if (!root.keySet().equals(FIELDS)) {
                throw new ContainerStorageException("CURRENT pointer fields are invalid");
            }
            JsonElement expectedBaseElement = root.get("expectedBaseRevision");
            long expectedBaseRevision = MinimapCodecs.NON_NEGATIVE_LONG
                    .parse(JsonOps.INSTANCE, expectedBaseElement)
                    .result()
                    .orElseThrow(() -> new ContainerStorageException(
                            "CURRENT pointer expected base revision is invalid"
                    ));
            JsonElement revisionElement = root.get("revision");
            long revision = MinimapCodecs.NON_NEGATIVE_LONG
                    .parse(JsonOps.INSTANCE, revisionElement)
                    .result()
                    .orElseThrow(() -> new ContainerStorageException(
                            "CURRENT pointer revision is invalid"
                    ));
            JsonElement checksumElement = root.get("descriptorChecksum");
            if (checksumElement == null || !checksumElement.isJsonPrimitive()
                    || !checksumElement.getAsJsonPrimitive().isString()) {
                throw new ContainerStorageException("CURRENT pointer checksum is invalid");
            }
            return new CurrentPointer(expectedBaseRevision, revision,
                    Sha256.parse(checksumElement.getAsString()));
        } catch (ContainerStorageException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ContainerStorageException("CURRENT pointer is invalid", exception);
        }
    }

    public static CurrentPointer parse(byte[] bytes) {
        return read(bytes);
    }

    @Override
    public String toString() {
        return new String(canonicalBytes(), StandardCharsets.UTF_8);
    }
}
