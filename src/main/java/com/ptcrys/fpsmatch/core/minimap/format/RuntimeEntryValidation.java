package com.ptcrys.fpsmatch.core.minimap.format;

import com.ptcrys.fpsmatch.core.minimap.codec.MinimapModelCodecs;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.model.ConnectionsFile;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeEntryDescriptor;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeManifest;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeRegionsFile;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeDefinition;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeStylesFile;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class RuntimeEntryValidation {
    private static final Set<String> MANIFEST_FIELDS = Set.of(
            "formatVersion", "documentId", "binding", "publishRevision", "sourceHash",
            "compilerProfile", "canvas", "defaultViewMode", "floors", "tileEdge", "entries"
    );
    private static final Set<String> REGIONS_FIELDS = Set.of("regions");
    private static final Set<String> CONNECTIONS_FIELDS = Set.of("connections");
    private static final Set<String> STYLES_FIELDS = Set.of("styles");
    private static final Set<ContainerPath> AUTHORITY_PATHS = Set.of(
            MinimapContainerLayout.RUNTIME_REGIONS,
            MinimapContainerLayout.CONNECTIONS,
            MinimapContainerLayout.RUNTIME_STYLES
    );

    private RuntimeEntryValidation() {
    }

    public static CanonicalModelJson.Document<RuntimeManifest> readManifest(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length > MinimapHardLimits.MAX_RUNTIME_MANIFEST_BYTES) {
            throw new ContainerValidationException("Runtime manifest exceeds its byte limit");
        }
        CanonicalModelJson.Document<RuntimeManifest> document = CanonicalModelJson.read(
                bytes, MinimapModelCodecs.RUNTIME_MANIFEST, MANIFEST_FIELDS
        );
        RuntimeManifest manifest = document.value();
        if (manifest.formatVersion().major() != MinimapFormatContract.CURRENT.major()
                || manifest.formatVersion().minor() < MinimapFormatContract.CURRENT.minor()) {
            throw new ContainerValidationException(
                    "Unsupported runtime map format major: " + manifest.formatVersion()
            );
        }
        validateManifestEntries(manifest);
        MinimapTileValidator.validateRuntimeManifestCoverage(manifest);
        return document;
    }

    public static RuntimeDefinition readDefinition(
            RuntimeManifest manifest,
            byte[] regionsBytes,
            byte[] connectionsBytes,
            byte[] stylesBytes
    ) {
        Objects.requireNonNull(manifest, "manifest");
        RuntimeRegionsFile regions = readModel(
                regionsBytes, MinimapModelCodecs.RUNTIME_REGIONS, REGIONS_FIELDS
        );
        ConnectionsFile connections = readModel(
                connectionsBytes, MinimapModelCodecs.CONNECTIONS, CONNECTIONS_FIELDS
        );
        RuntimeStylesFile styles = readModel(
                stylesBytes, MinimapModelCodecs.RUNTIME_STYLES, STYLES_FIELDS
        );
        RuntimeDefinition definition = new RuntimeDefinition(
                manifest, regions, connections, styles
        );
        List<MinimapValidationIssue> issues = MinimapValidator.validate(definition);
        if (!issues.isEmpty()) {
            throw new ContainerValidationException(
                    "Runtime definition validation failed: " + issues.get(0)
            );
        }
        return definition;
    }

    public static void validateEntry(
            RuntimeManifest manifest,
            ContainerPath path,
            byte[] payload
    ) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(payload, "payload");
        RuntimeEntryDescriptor descriptor = manifest.entries().stream()
                .filter(candidate -> candidate.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new ContainerValidationException(
                        "Runtime entry is not declared by the manifest: " + path
                ));
        if (descriptor.byteLength() != payload.length
                || !descriptor.sha256().equals(Sha256Digest.of(payload))) {
            throw new ContainerValidationException(
                    "Runtime entry length or hash does not match the manifest: " + path
            );
        }
        MinimapContainerLayout.RuntimeEntryKind kind = MinimapContainerLayout.classifyRuntime(path)
                .orElseThrow(() -> new ContainerValidationException(
                        "Path is not allowed in a runtime map: " + path
                ));
        switch (kind) {
            case MANIFEST -> throw new ContainerValidationException(
                    "Runtime manifest cannot list itself"
            );
            case REGIONS -> readModel(
                    payload, MinimapModelCodecs.RUNTIME_REGIONS, REGIONS_FIELDS
            );
            case CONNECTIONS -> readModel(
                    payload, MinimapModelCodecs.CONNECTIONS, CONNECTIONS_FIELDS
            );
            case STYLES -> readModel(
                    payload, MinimapModelCodecs.RUNTIME_STYLES, STYLES_FIELDS
            );
            case FLOOR_TILE -> MinimapTileValidator.validateRuntimeTile(
                    manifest, path, payload
            );
            case THUMBNAIL -> validatePng(payload, path);
        }
    }

    private static void validateManifestEntries(RuntimeManifest manifest) {
        List<MinimapValidationIssue> issues = MinimapValidator.validate(manifest);
        if (!issues.isEmpty()) {
            throw new ContainerValidationException(
                    "Runtime manifest validation failed: " + issues.get(0)
            );
        }
        Set<ContainerPath> paths = new HashSet<>();
        String previous = null;
        long declaredBytes = 0;
        for (RuntimeEntryDescriptor entry : manifest.entries()) {
            String current = entry.path().value();
            if (previous != null && previous.compareTo(current) >= 0) {
                throw new ContainerValidationException(
                        "Runtime manifest entries are not strictly sorted"
                );
            }
            if (!MinimapContainerLayout.isRuntimePath(entry.path())) {
                throw new ContainerValidationException(
                        "Path is not allowed in a runtime map: " + entry.path()
                );
            }
            paths.add(entry.path());
            try {
                declaredBytes = Math.addExact(declaredBytes, entry.byteLength());
            } catch (ArithmeticException exception) {
                throw new ContainerValidationException(
                        "Runtime manifest declared bytes overflow", exception
                );
            }
            if (declaredBytes > MinimapHardLimits.MAX_RUNTIME_EXPANDED_BYTES) {
                throw new ContainerValidationException(
                        "Runtime manifest declared bytes exceed the hard limit"
                );
            }
            previous = current;
        }
        if (!paths.containsAll(AUTHORITY_PATHS)) {
            throw new ContainerValidationException(
                    "Runtime map is missing an authority JSON entry"
            );
        }
    }

    private static <T> T readModel(
            byte[] payload,
            com.mojang.serialization.Codec<T> codec,
            Set<String> fields
    ) {
        Objects.requireNonNull(payload, "payload");
        if (payload.length > MinimapHardLimits.MAX_JSON_ENTRY_BYTES) {
            throw new ContainerValidationException("Runtime JSON entry exceeds its byte limit");
        }
        return CanonicalModelJson.read(payload, codec, fields).value();
    }

    private static void validatePng(byte[] payload, ContainerPath path) {
        try {
            BoundedPngReader.decode(payload);
        } catch (PngValidationException exception) {
            throw new ContainerValidationException(
                    "PNG entry is not canonical: " + path, exception
            );
        }
    }
}
