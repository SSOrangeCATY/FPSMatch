package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.mojang.serialization.Codec;
import com.phasetranscrystal.fpsmatch.core.minimap.codec.MinimapModelCodecs;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ConnectionsFile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MediaType;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeDefinition;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeEntryDescriptor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeManifest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeRegionsFile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeStylesFile;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RuntimeMapReader {
    private static final Set<String> REGIONS_FIELDS = Set.of("regions");
    private static final Set<String> CONNECTIONS_FIELDS = Set.of("connections");
    private static final Set<String> STYLES_FIELDS = Set.of("styles");

    private RuntimeMapReader() {
    }

    public static RuntimeMap read(byte[] container) {
        return read(container, ContainerLimits.runtimeHardLimits());
    }

    public static RuntimeMap read(byte[] container, ContainerLimits limits) {
        return parse(CanonicalZipReader.read(container, limits));
    }

    public static RuntimeMap read(byte[] container, FormatMigrationRegistry migrations) {
        return read(container, migrations, ContainerLimits.runtimeHardLimits());
    }

    public static RuntimeMap read(
            byte[] container,
            FormatMigrationRegistry migrations,
            ContainerLimits limits
    ) {
        Objects.requireNonNull(migrations, "migrations");
        CanonicalZipReader.Archive archive = CanonicalZipReader.read(container, limits);
        try {
            com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatVersion version =
                    readFormatVersion(archive);
            if (version.major() == MinimapFormatContract.CURRENT.major()
                    && version.minor() >= MinimapFormatContract.CURRENT.minor()) {
                return parse(archive);
            }
            FormatMigrationRegistry.Snapshot snapshot = new FormatMigrationRegistry.Snapshot(
                    FormatMigrationRegistry.ContainerKind.RUNTIME,
                    version,
                    archive.entries()
            );
            FormatMigrationRegistry.Snapshot migrated = migrations.migrate(
                    snapshot, MinimapFormatContract.CURRENT
            );
            byte[] migratedContainer = CanonicalZipWriter.write(
                    migrated.entries(), limits
            );
            archive.close();
            return read(migratedContainer, limits);
        } catch (IOException exception) {
            try {
                archive.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw new ContainerValidationException("Failed to close runtime migration snapshot", exception);
        } catch (RuntimeException exception) {
            try {
                archive.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    public static RuntimeMap open(SeekableByteChannel channel, long containerSize) {
        return open(channel, containerSize, ContainerLimits.runtimeHardLimits());
    }

    public static RuntimeMap open(
            SeekableByteChannel channel,
            long containerSize,
            ContainerLimits limits
    ) {
        return parse(CanonicalZipReader.open(channel, containerSize, limits));
    }

    private static RuntimeMap parse(CanonicalZipReader.Archive archive) {
        try {
            validatePaths(archive);
            byte[] manifestBytes = readBytes(
                    archive, MinimapContainerLayout.RUNTIME_MANIFEST,
                    MinimapHardLimits.MAX_RUNTIME_MANIFEST_BYTES
            );
            CanonicalModelJson.Document<RuntimeManifest> manifestDocument =
                    RuntimeEntryValidation.readManifest(manifestBytes);
            RuntimeManifest manifest = manifestDocument.value();
            List<CanonicalZipReader.ExpectedEntry> expected = manifest.entries().stream()
                    .map(entry -> new CanonicalZipReader.ExpectedEntry(
                            entry.path(), entry.byteLength(), entry.sha256()
                    ))
                    .toList();
            archive.verifyManifestEntries(MinimapContainerLayout.RUNTIME_MANIFEST, expected);

            CanonicalModelJson.Document<RuntimeRegionsFile> regions = readModel(
                    archive, MinimapContainerLayout.RUNTIME_REGIONS,
                    MinimapModelCodecs.RUNTIME_REGIONS, REGIONS_FIELDS
            );
            CanonicalModelJson.Document<ConnectionsFile> connections = readModel(
                    archive, MinimapContainerLayout.CONNECTIONS,
                    MinimapModelCodecs.CONNECTIONS, CONNECTIONS_FIELDS
            );
            CanonicalModelJson.Document<RuntimeStylesFile> styles = readModel(
                    archive, MinimapContainerLayout.RUNTIME_STYLES,
                    MinimapModelCodecs.RUNTIME_STYLES, STYLES_FIELDS
            );

            RuntimeDefinition definition = new RuntimeDefinition(
                    manifest, regions.value(), connections.value(), styles.value()
            );
            MinimapTileValidator.validateRuntimeArchive(archive, manifest);
            validateOpaqueEntries(archive, manifest);
            List<MinimapValidationIssue> issues = MinimapValidator.validate(definition);
            if (!issues.isEmpty()) {
                throw new ContainerValidationException("Runtime map validation failed: " + issues.get(0));
            }
            Map<ContainerPath, PreservedExtensions> extensions = new java.util.LinkedHashMap<>();
            extensions.put(MinimapContainerLayout.RUNTIME_MANIFEST, manifestDocument.extensions());
            extensions.put(MinimapContainerLayout.RUNTIME_REGIONS, regions.extensions());
            extensions.put(MinimapContainerLayout.CONNECTIONS, connections.extensions());
            extensions.put(MinimapContainerLayout.RUNTIME_STYLES, styles.extensions());
            return new RuntimeMap(definition, archive, manifestBytes, extensions);
        } catch (RuntimeException exception) {
            try {
                archive.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            if (exception instanceof ContainerValidationException validationException) {
                throw validationException;
            }
            throw new ContainerValidationException("Invalid runtime map container", exception);
        }
    }

    private static void validatePaths(CanonicalZipReader.Archive archive) {
        for (ContainerPath path : archive.paths()) {
            if (!MinimapContainerLayout.isRuntimePath(path)) {
                throw new ContainerValidationException("Path is not allowed in a runtime map: " + path);
            }
        }
    }

    private static com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatVersion
    readFormatVersion(CanonicalZipReader.Archive archive) {
        if (archive.entryLength(MinimapContainerLayout.RUNTIME_MANIFEST)
                > MinimapHardLimits.MAX_RUNTIME_MANIFEST_BYTES) {
            throw new ContainerValidationException("Runtime manifest exceeds its byte limit");
        }
        byte[] bytes = archive.entryBytes(MinimapContainerLayout.RUNTIME_MANIFEST);
        com.google.gson.JsonElement parsed = StrictJsonParser.parse(bytes);
        if (!Arrays.equals(bytes, JcsCanonicalizer.canonicalize(parsed)) || !parsed.isJsonObject()) {
            throw new ContainerValidationException("Runtime manifest is not a canonical JSON object");
        }
        com.google.gson.JsonElement value = parsed.getAsJsonObject().get("formatVersion");
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new ContainerValidationException("Runtime manifest formatVersion is missing");
        }
        try {
            return com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatVersion.parse(
                    value.getAsString()
            );
        } catch (IllegalArgumentException exception) {
            throw new ContainerValidationException("Runtime manifest formatVersion is invalid", exception);
        }
    }

    private static byte[] readBytes(
            CanonicalZipReader.Archive archive,
            ContainerPath path,
            long maxBytes
    ) {
        long length = archive.entryLength(path);
        if (length > maxBytes) {
            throw new ContainerValidationException("Runtime manifest exceeds its byte limit");
        }
        return archive.entryBytes(path);
    }

    private static <T> CanonicalModelJson.Document<T> readModel(
            CanonicalZipReader.Archive archive,
            ContainerPath path,
            Codec<T> codec,
            Set<String> fields
    ) {
        long length = archive.entryLength(path);
        if (length > MinimapHardLimits.MAX_JSON_ENTRY_BYTES) {
            throw new ContainerValidationException("Runtime JSON entry exceeds its byte limit: " + path);
        }
        return CanonicalModelJson.read(archive.entryBytes(path), codec, fields);
    }

    private static void validateOpaqueEntries(
            CanonicalZipReader.Archive archive,
            RuntimeManifest manifest
    ) {
        Set<ContainerPath> authority = Set.of(
                MinimapContainerLayout.RUNTIME_REGIONS,
                MinimapContainerLayout.CONNECTIONS,
                MinimapContainerLayout.RUNTIME_STYLES
        );
        for (RuntimeEntryDescriptor entry : manifest.entries()) {
            if (authority.contains(entry.path())) {
                continue;
            }
            if (entry.path().value().endsWith(".png")) {
                if (MinimapContainerLayout.classifyRuntime(entry.path()).orElse(null)
                        == MinimapContainerLayout.RuntimeEntryKind.FLOOR_TILE) {
                    continue;
                }
                BoundedPngReader.decode(archive.entryBytes(entry.path()));
            } else {
                byte[] bytes = archive.entryBytes(entry.path());
                if (!Arrays.equals(bytes, JcsCanonicalizer.canonicalize(bytes))) {
                    throw new ContainerValidationException("Runtime JSON entry is not canonical");
                }
            }
        }
    }
}
