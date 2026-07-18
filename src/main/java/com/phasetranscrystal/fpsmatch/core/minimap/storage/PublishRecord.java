package com.phasetranscrystal.fpsmatch.core.minimap.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.phasetranscrystal.fpsmatch.core.minimap.format.JcsCanonicalizer;
import com.phasetranscrystal.fpsmatch.core.minimap.format.StrictJsonParser;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

public record PublishRecord(
        PublishTarget target,
        PublishDescriptor descriptor,
        PublishState state,
        String reason,
        PairValidation pairValidation
) {
    private static final Set<String> DESCRIPTOR_FIELDS = Set.of(
            "baseRevision",
            "expiresAtEpochMillis",
            "publishRevision",
            "publishToken",
            "runtimeContainerHash",
            "runtimeHash",
            "sourceHash"
    );

    public PublishRecord {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(state, "state");
        reason = reason == null ? "" : reason;
        Objects.requireNonNull(pairValidation, "pairValidation");
        if (pairValidation == PairValidation.METADATA_TRUSTED
                && state == PublishState.RESERVED) {
            throw new IllegalArgumentException(
                    "A RESERVED publish pair cannot be metadata-trusted"
            );
        }
    }

    public PublishRecord(
            PublishTarget target,
            PublishDescriptor descriptor,
            PublishState state,
            String reason
    ) {
        this(target, descriptor, state, reason, PairValidation.FULL);
    }

    public static PublishRecord reserved(
            PublishTarget target,
            PublishDescriptor descriptor
    ) {
        return new PublishRecord(target, descriptor, PublishState.RESERVED, "reserved");
    }

    public static PublishRecord trustedPrepared(
            PublishTarget target,
            PublishDescriptor descriptor,
            String reason
    ) {
        return new PublishRecord(
                target, descriptor,
                PublishState.PREPARED,
                reason,
                PairValidation.METADATA_TRUSTED
        );
    }

    public Sha256 descriptorChecksum() {
        return descriptor.descriptorChecksum();
    }

    public byte[] canonicalBytes() {
        JsonObject root = new JsonObject();
        root.add("descriptor", StrictJsonParser.parse(descriptor.canonicalBytes()));
        root.addProperty("descriptorChecksum", descriptorChecksum().value());
        root.addProperty("pairValidation", pairValidation.name());
        root.addProperty("reason", reason);
        root.addProperty("state", state.name());
        JsonObject persistedTarget = new JsonObject();
        persistedTarget.addProperty("dimension", target.dimension().toString());
        persistedTarget.addProperty("documentId", target.documentId().toString());
        JsonObject mapKey = new JsonObject();
        mapKey.addProperty("gameType", target.mapKey().gameType());
        mapKey.addProperty("mapName", target.mapKey().mapName());
        persistedTarget.add("mapKey", mapKey);
        root.add("target", persistedTarget);
        return JcsCanonicalizer.canonicalize(root);
    }

    public static PublishRecord read(byte[] bytes) {
        try {
            JsonElement parsed = StrictJsonParser.parse(bytes);
            if (!parsed.isJsonObject() || !Arrays.equals(bytes, JcsCanonicalizer.canonicalize(parsed))) {
                throw new ContainerStorageException("Publish record is not canonical JSON");
            }
            JsonObject root = parsed.getAsJsonObject();
            Set<String> fields = root.keySet();
            if (!fields.equals(Set.of(
                    "descriptor", "descriptorChecksum", "pairValidation", "reason", "state",
                    "target"
            )) && !fields.equals(Set.of(
                    "descriptor", "descriptorChecksum", "reason", "state", "target"
            ))) {
                throw new ContainerStorageException("Publish record fields are invalid");
            }
            JsonObject value = root.getAsJsonObject("descriptor");
            if (value == null || !value.keySet().equals(DESCRIPTOR_FIELDS)) {
                throw new ContainerStorageException("Publish record descriptor is missing");
            }
            long base = parseLong(value, "baseRevision");
            long expiresAt = parseLong(value, "expiresAtEpochMillis");
            long publish = parseLong(value, "publishRevision");
            String token = string(value, "publishToken");
            Sha256 source = Sha256.parse(string(value, "sourceHash"));
            Sha256 runtime = Sha256.parse(string(value, "runtimeHash"));
            Sha256 container = Sha256.parse(string(value, "runtimeContainerHash"));
            PublishDescriptor descriptor = new PublishDescriptor(
                    token, base, publish, expiresAt, source, runtime, container
            );
            if (!descriptor.descriptorChecksum().value()
                    .equals(string(root, "descriptorChecksum"))) {
                throw new ContainerStorageException("Publish descriptor checksum is invalid");
            }
            PairValidation pairValidation = root.has("pairValidation")
                    ? PairValidation.valueOf(string(root, "pairValidation"))
                    : PairValidation.FULL;
            JsonObject persistedTarget = root.getAsJsonObject("target");
            if (persistedTarget == null || !persistedTarget.keySet().equals(Set.of(
                    "dimension", "documentId", "mapKey"
            ))) {
                throw new ContainerStorageException("Publish target is invalid");
            }
            JsonObject mapKey = persistedTarget.getAsJsonObject("mapKey");
            if (mapKey == null || !mapKey.keySet().equals(Set.of("gameType", "mapName"))) {
                throw new ContainerStorageException("Publish target map key is invalid");
            }
            PublishTarget target = new PublishTarget(
                    new MapKey(string(mapKey, "gameType"), string(mapKey, "mapName")),
                    NamespacedId.parse(string(persistedTarget, "dimension")),
                    NamespacedId.parse(string(persistedTarget, "documentId"))
            );
            return new PublishRecord(
                    target, descriptor,
                    PublishState.valueOf(string(root, "state")),
                    string(root, "reason"),
                    pairValidation
            );
        } catch (ContainerStorageException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ContainerStorageException("Publish record is invalid", exception);
        }
    }

    private static long parseLong(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new ContainerStorageException("Publish record count is invalid: " + key);
        }
        try {
            String text = value.getAsString();
            if (!text.matches("0|[1-9][0-9]{0,18}")) {
                throw new NumberFormatException();
            }
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            throw new ContainerStorageException("Publish record count is invalid: " + key, exception);
        }
    }

    private static String string(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new ContainerStorageException("Publish record string is invalid: " + key);
        }
        return value.getAsString();
    }

    public PublishRecord transition(PublishState next, String nextReason) {
        Objects.requireNonNull(next, "next");
        boolean legal = switch (state) {
            case RESERVED -> next == PublishState.PREPARED || next == PublishState.ABORTED;
            case PREPARED -> next == PublishState.COMMITTED || next == PublishState.ABORTED;
            case COMMITTED, ABORTED -> false;
        };
        if (!legal) {
            throw new IllegalStateException("Illegal publish state transition: " + state + " -> " + next);
        }
        return new PublishRecord(target, descriptor, next, nextReason, pairValidation);
    }
}
