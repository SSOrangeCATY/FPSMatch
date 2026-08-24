package com.ptcrys.fpsmatch.common.minimap.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.format.BoundedPngReader;
import com.ptcrys.fpsmatch.core.minimap.format.JcsCanonicalizer;
import com.ptcrys.fpsmatch.core.minimap.format.MinimapContainerLayout;
import com.ptcrys.fpsmatch.core.minimap.format.PngValidationException;
import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import com.ptcrys.fpsmatch.core.minimap.format.StrictJsonParser;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Validated Task 3 forward-operation descriptor and its submitted CAS content. */
final class DraftOperationDescriptor {
    private static final Set<String> ROOT_FIELDS = Set.of("operations");
    private static final Set<String> OPACITY_FIELDS = Set.of("kind", "opacity", "path");
    private static final Set<String> VISIBILITY_FIELDS = Set.of("kind", "path", "visible");
    private static final Set<String> LOCKED_FIELDS = Set.of("kind", "locked", "path");
    private static final Set<String> PUT_FIELDS = Set.of("kind", "newHash", "path");
    private static final Set<String> PUT_WITH_OLD_FIELDS =
            Set.of("kind", "newHash", "oldHash", "path");
    private static final Set<String> DELETE_FIELDS = Set.of("kind", "oldHash", "path");

    private final byte[] bytes;
    private final List<Sha256> referencedContentHashes;
    private final Map<Sha256, byte[]> submittedContent;

    private DraftOperationDescriptor(
            byte[] bytes,
            List<Sha256> referencedContentHashes,
            Map<Sha256, byte[]> submittedContent
    ) {
        this.bytes = bytes;
        this.referencedContentHashes = referencedContentHashes;
        this.submittedContent = submittedContent;
    }

    static DraftOperationDescriptor validate(
            Sha256 expectedHash,
            byte[] descriptorBytes,
            Map<Sha256, byte[]> referencedContent,
            long maximumContentBytes
    ) {
        Objects.requireNonNull(expectedHash, "expectedHash");
        Objects.requireNonNull(descriptorBytes, "descriptorBytes");
        Objects.requireNonNull(referencedContent, "referencedContent");
        if (descriptorBytes.length == 0
                || descriptorBytes.length > MinimapHardLimits.MAX_WIRE_BODY_BYTES) {
            throw error(MinimapErrorCode.QUOTA_EXCEEDED, "Draft descriptor exceeds its limit");
        }
        byte[] ownedDescriptor = descriptorBytes.clone();
        if (!Sha256Digest.of(ownedDescriptor).equals(expectedHash)) {
            throw error(MinimapErrorCode.HASH_MISMATCH, "Draft descriptor hash does not match");
        }

        List<Sha256> declaredHashes = parse(ownedDescriptor);
        if (referencedContent.size() > MinimapHardLimits.MAX_EDITOR_MUTATIONS) {
            throw error(MinimapErrorCode.QUOTA_EXCEEDED, "Draft content reference count is excessive");
        }
        Set<Sha256> declared = Set.copyOf(declaredHashes);
        Map<Sha256, byte[]> ownedContent = new LinkedHashMap<>();
        long submittedBytes = ownedDescriptor.length;
        for (Map.Entry<Sha256, byte[]> entry : referencedContent.entrySet()) {
            Sha256 hash = Objects.requireNonNull(entry.getKey(), "referenced content hash");
            byte[] value = Objects.requireNonNull(
                    entry.getValue(), "referenced content bytes"
            ).clone();
            if (!declared.contains(hash)) {
                throw error(
                        MinimapErrorCode.VALIDATION_FAILED,
                        "Draft submission contains unreferenced content"
                );
            }
            if (value.length == 0
                    || value.length > MinimapHardLimits.MAX_SOURCE_ENTRY_UPLOAD_BYTES) {
                throw error(MinimapErrorCode.QUOTA_EXCEEDED, "Draft content exceeds its limit");
            }
            if (!Sha256Digest.of(value).equals(hash)) {
                throw error(MinimapErrorCode.HASH_MISMATCH, "Draft content hash does not match");
            }
            try {
                submittedBytes = Math.addExact(submittedBytes, value.length);
            } catch (ArithmeticException overflow) {
                throw error(MinimapErrorCode.QUOTA_EXCEEDED, "Draft content size overflowed");
            }
            if (submittedBytes > maximumContentBytes) {
                throw error(MinimapErrorCode.QUOTA_EXCEEDED, "Draft content byte quota is exhausted");
            }
            ownedContent.put(hash, value);
        }
        for (byte[] content : ownedContent.values()) {
            try {
                BoundedPngReader.decode(content);
            } catch (PngValidationException invalidPng) {
                throw new DraftException(
                        MinimapErrorCode.VALIDATION_FAILED,
                        "Draft tile content is not a canonical PNG",
                        invalidPng
                );
            }
        }
        return new DraftOperationDescriptor(
                ownedDescriptor,
                declaredHashes,
                Collections.unmodifiableMap(ownedContent)
        );
    }

    void requireCompleteContent() {
        if (!submittedContent.keySet().equals(Set.copyOf(referencedContentHashes))) {
            throw error(
                    MinimapErrorCode.ENTRY_NOT_FOUND,
                    "Draft descriptor references content that was not submitted"
            );
        }
    }

    byte[] bytes() {
        return bytes.clone();
    }

    List<Sha256> referencedContentHashes() {
        return referencedContentHashes;
    }

    Map<Sha256, byte[]> submittedContent() {
        return submittedContent;
    }

    private static List<Sha256> parse(byte[] bytes) {
        try {
            JsonElement parsed = StrictJsonParser.parse(bytes);
            if (!parsed.isJsonObject()
                    || !Arrays.equals(bytes, JcsCanonicalizer.canonicalize(parsed))) {
                throw error(
                        MinimapErrorCode.VALIDATION_FAILED,
                        "Draft descriptor is not canonical JSON"
                );
            }
            JsonObject root = parsed.getAsJsonObject();
            if (!root.keySet().equals(ROOT_FIELDS)) {
                throw error(
                        MinimapErrorCode.VALIDATION_FAILED,
                        "Draft descriptor fields are invalid"
                );
            }
            JsonElement operationsValue = root.get("operations");
            if (operationsValue == null || !operationsValue.isJsonArray()) {
                throw error(
                        MinimapErrorCode.VALIDATION_FAILED,
                        "Draft descriptor operations are invalid"
                );
            }
            JsonArray operations = operationsValue.getAsJsonArray();
            if (operations.isEmpty()) {
                throw error(
                        MinimapErrorCode.VALIDATION_FAILED,
                        "Draft descriptor must contain an operation"
                );
            }
            if (operations.size() > MinimapHardLimits.MAX_EDITOR_MUTATIONS) {
                throw error(
                        MinimapErrorCode.QUOTA_EXCEEDED,
                        "Draft descriptor operation count is excessive"
                );
            }
            Set<Sha256> references = new LinkedHashSet<>();
            for (JsonElement operationValue : operations) {
                if (!operationValue.isJsonObject()) {
                    throw error(
                            MinimapErrorCode.VALIDATION_FAILED,
                            "Draft operation must be an object"
                    );
                }
                validateOperation(operationValue.getAsJsonObject(), references);
            }
            return references.stream()
                    .sorted(Comparator.comparing(Sha256::value))
                    .toList();
        } catch (DraftException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DraftException(
                    MinimapErrorCode.VALIDATION_FAILED,
                    "Draft descriptor is invalid",
                    exception
            );
        }
    }

    private static void validateOperation(JsonObject operation, Set<Sha256> references) {
        String kind = string(operation, "kind");
        String path = string(operation, "path");
        switch (kind) {
            case "set_opacity" -> {
                requireFields(operation, OPACITY_FIELDS);
                requireLayerPath(path);
                JsonPrimitive opacity = primitive(operation, "opacity");
                if (!opacity.isNumber()) {
                    throw validation("Draft opacity must be a number");
                }
                double value = opacity.getAsDouble();
                if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
                    throw validation("Draft opacity is outside [0, 1]");
                }
            }
            case "set_visibility" -> {
                requireFields(operation, VISIBILITY_FIELDS);
                requireLayerPath(path);
                requireBoolean(operation, "visible");
            }
            case "set_locked" -> {
                requireFields(operation, LOCKED_FIELDS);
                requireLayerPath(path);
                requireBoolean(operation, "locked");
            }
            case "put_tile" -> {
                if (!operation.keySet().equals(PUT_FIELDS)
                        && !operation.keySet().equals(PUT_WITH_OLD_FIELDS)) {
                    throw validation("Draft put-tile fields are invalid");
                }
                requireTilePath(path);
                if (operation.has("oldHash")) {
                    hash(operation, "oldHash");
                }
                references.add(hash(operation, "newHash"));
            }
            case "delete_tile" -> {
                requireFields(operation, DELETE_FIELDS);
                requireTilePath(path);
                hash(operation, "oldHash");
            }
            default -> throw validation("Draft operation kind is unsupported");
        }
    }

    private static void requireLayerPath(String value) {
        final ContainerPath path;
        try {
            path = ContainerPath.parse(value);
        } catch (IllegalArgumentException invalid) {
            throw invalidPath(invalid);
        }
        String[] segments = path.value().split("/", -1);
        if (segments.length != 4
                || !segments[0].equals("floors")
                || !segments[2].equals("layers")
                || !MinimapFormatContract.isInternalSlug(segments[1])
                || !MinimapFormatContract.isInternalSlug(segments[3])) {
            throw invalidPath(null);
        }
    }

    private static void requireTilePath(String value) {
        final ContainerPath path;
        try {
            path = ContainerPath.parse(value);
        } catch (IllegalArgumentException invalid) {
            throw invalidPath(invalid);
        }
        MinimapContainerLayout.SourceTileAddress address =
                MinimapContainerLayout.parseSourceTile(path).orElseThrow(
                        () -> invalidPath(null)
                );
        if (address.kind() != MinimapContainerLayout.SourceEntryKind.LAYER_TILE) {
            throw invalidPath(null);
        }
    }

    private static void requireFields(JsonObject value, Set<String> fields) {
        if (!value.keySet().equals(fields)) {
            throw validation("Draft operation fields are invalid");
        }
    }

    private static void requireBoolean(JsonObject value, String name) {
        JsonPrimitive primitive = primitive(value, name);
        if (!primitive.isBoolean()) {
            throw validation("Draft boolean field is invalid: " + name);
        }
    }

    private static Sha256 hash(JsonObject value, String name) {
        try {
            return Sha256.parse(string(value, name));
        } catch (IllegalArgumentException invalidHash) {
            throw new DraftException(
                    MinimapErrorCode.VALIDATION_FAILED,
                    "Draft SHA-256 field is invalid: " + name,
                    invalidHash
            );
        }
    }

    private static String string(JsonObject value, String name) {
        JsonPrimitive primitive = primitive(value, name);
        if (!primitive.isString()) {
            throw validation("Draft string field is invalid: " + name);
        }
        return primitive.getAsString();
    }

    private static JsonPrimitive primitive(JsonObject value, String name) {
        JsonElement element = value.get(name);
        if (element == null || !element.isJsonPrimitive()) {
            throw validation("Draft primitive field is invalid: " + name);
        }
        return element.getAsJsonPrimitive();
    }

    private static DraftException invalidPath(Throwable cause) {
        return cause == null
                ? error(MinimapErrorCode.INVALID_PATH, "Draft operation path is invalid")
                : new DraftException(
                        MinimapErrorCode.INVALID_PATH,
                        "Draft operation path is invalid",
                        cause
                );
    }

    private static DraftException validation(String message) {
        return error(MinimapErrorCode.VALIDATION_FAILED, message);
    }

    private static DraftException error(MinimapErrorCode code, String message) {
        return new DraftException(code, message);
    }
}
