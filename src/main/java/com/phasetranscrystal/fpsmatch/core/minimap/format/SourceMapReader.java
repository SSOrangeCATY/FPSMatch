package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.phasetranscrystal.fpsmatch.core.minimap.codec.MinimapModelCodecs;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ConnectionsFile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MediaType;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapDefinition;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RegionsFile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceEntryDescriptor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceManifest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.StylesFile;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SourceMapReader {
    private static final Set<String> MANIFEST_FIELDS = Set.of(
            "formatVersion", "documentId", "binding", "revision", "dimension",
            "provenance", "tileEdge", "entries"
    );
    private static final Set<String> DOCUMENT_FIELDS = Set.of(
            "worldBounds", "canvas", "defaultViewMode", "floors", "layerOrder"
    );
    private static final Set<String> REGIONS_FIELDS = Set.of("regions");
    private static final Set<String> CONNECTIONS_FIELDS = Set.of("connections");
    private static final Set<String> STYLES_FIELDS = Set.of("styles");

    private SourceMapReader() {
    }

    public static SourceMap read(byte[] container) {
        return read(container, ContainerLimits.sourceHardLimits());
    }

    public static SourceMap read(byte[] container, ContainerLimits limits) {
        return parse(CanonicalZipReader.read(container, limits));
    }

    public static SourceMap read(byte[] container, FormatMigrationRegistry migrations) {
        return read(container, migrations, ContainerLimits.sourceHardLimits());
    }

    public static SourceMap read(
            byte[] container,
            FormatMigrationRegistry migrations,
            ContainerLimits limits
    ) {
        Objects.requireNonNull(migrations, "migrations");
        CanonicalZipReader.Archive archive = CanonicalZipReader.read(container, limits);
        com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatVersion version;
        try {
            version = readFormatVersion(archive, MinimapContainerLayout.SOURCE_MANIFEST);
            if (version.major() == MinimapFormatContract.CURRENT.major()
                    && version.minor() >= MinimapFormatContract.CURRENT.minor()) {
                return parse(archive);
            }
            FormatMigrationRegistry.Snapshot snapshot = new FormatMigrationRegistry.Snapshot(
                    FormatMigrationRegistry.ContainerKind.SOURCE,
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
            throw new ContainerValidationException("Failed to close source migration snapshot", exception);
        } catch (RuntimeException exception) {
            try {
                archive.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    public static SourceMap open(SeekableByteChannel channel, long containerSize) {
        return open(channel, containerSize, ContainerLimits.sourceHardLimits());
    }

    public static SourceMap open(
            SeekableByteChannel channel,
            long containerSize,
            ContainerLimits limits
    ) {
        return parse(CanonicalZipReader.open(channel, containerSize, limits));
    }

    private static SourceMap parse(CanonicalZipReader.Archive archive) {
        try {
            validatePaths(archive);
            CanonicalModelJson.Document<SourceManifest> manifestDocument = readModel(
                    archive,
                    MinimapContainerLayout.SOURCE_MANIFEST,
                    MinimapHardLimits.MAX_SOURCE_MANIFEST_BYTES,
                    MinimapModelCodecs.SOURCE_MANIFEST,
                    MANIFEST_FIELDS
            );
            SourceManifest manifest = manifestDocument.value();
            if (manifest.formatVersion().major() != MinimapFormatContract.CURRENT.major()
                    || manifest.formatVersion().minor() < MinimapFormatContract.CURRENT.minor()) {
                throw new ContainerValidationException(
                        "Unsupported source map format major: " + manifest.formatVersion()
                );
            }
            validateManifestOrderAndTypes(manifest);

            List<CanonicalZipReader.ExpectedEntry> expected = manifest.entries().stream()
                    .map(entry -> new CanonicalZipReader.ExpectedEntry(
                            entry.path(), entry.byteLength(), entry.sha256()
                    ))
                    .toList();
            archive.verifyManifestEntries(MinimapContainerLayout.SOURCE_MANIFEST, expected);
            requireAuthorityEntries(manifest);

            CanonicalModelJson.Document<SourceDocument> document = readModel(
                    archive,
                    MinimapContainerLayout.SOURCE_DOCUMENT,
                    MinimapHardLimits.MAX_JSON_ENTRY_BYTES,
                    MinimapModelCodecs.SOURCE_DOCUMENT,
                    DOCUMENT_FIELDS
            );
            CanonicalModelJson.Document<RegionsFile> regions = readModel(
                    archive,
                    MinimapContainerLayout.SOURCE_REGIONS,
                    MinimapHardLimits.MAX_JSON_ENTRY_BYTES,
                    MinimapModelCodecs.REGIONS,
                    REGIONS_FIELDS
            );
            CanonicalModelJson.Document<ConnectionsFile> connections = readModel(
                    archive,
                    MinimapContainerLayout.CONNECTIONS,
                    MinimapHardLimits.MAX_JSON_ENTRY_BYTES,
                    MinimapModelCodecs.CONNECTIONS,
                    CONNECTIONS_FIELDS
            );
            CanonicalModelJson.Document<StylesFile> styles = readModel(
                    archive,
                    MinimapContainerLayout.SOURCE_STYLES,
                    MinimapHardLimits.MAX_JSON_ENTRY_BYTES,
                    MinimapModelCodecs.STYLES,
                    STYLES_FIELDS
            );

            MinimapDefinition definition = new MinimapDefinition(
                    manifest, document.value(), regions.value(), connections.value(), styles.value()
            );
            MinimapTileValidator.validateSourceArchive(archive, definition);
            validateOpaqueEntries(archive, manifest);
            List<MinimapValidationIssue> issues = MinimapValidator.validate(definition);
            if (!issues.isEmpty()) {
                throw new ContainerValidationException("Source map validation failed: " + issues.get(0));
            }

            Map<ContainerPath, PreservedExtensions> extensions = new LinkedHashMap<>();
            extensions.put(MinimapContainerLayout.SOURCE_MANIFEST, manifestDocument.extensions());
            extensions.put(MinimapContainerLayout.SOURCE_DOCUMENT, document.extensions());
            extensions.put(MinimapContainerLayout.SOURCE_REGIONS, regions.extensions());
            extensions.put(MinimapContainerLayout.CONNECTIONS, connections.extensions());
            extensions.put(MinimapContainerLayout.SOURCE_STYLES, styles.extensions());
            return new SourceMap(definition, archive, extensions);
        } catch (RuntimeException exception) {
            try {
                archive.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            if (exception instanceof ContainerValidationException validationException) {
                throw validationException;
            }
            throw new ContainerValidationException("Invalid source map container", exception);
        }
    }

    private static void validatePaths(CanonicalZipReader.Archive archive) {
        for (ContainerPath path : archive.paths()) {
            if (!MinimapContainerLayout.isSourcePath(path)) {
                throw new ContainerValidationException("Path is not allowed in a source map: " + path);
            }
        }
    }

    private static com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatVersion
    readFormatVersion(CanonicalZipReader.Archive archive, ContainerPath manifestPath) {
        if (archive.entryLength(manifestPath) > MinimapHardLimits.MAX_SOURCE_MANIFEST_BYTES) {
            throw new ContainerValidationException("Source manifest exceeds its byte limit");
        }
        byte[] bytes = archive.entryBytes(manifestPath);
        com.google.gson.JsonElement parsed = StrictJsonParser.parse(bytes);
        if (!Arrays.equals(bytes, JcsCanonicalizer.canonicalize(parsed)) || !parsed.isJsonObject()) {
            throw new ContainerValidationException("Source manifest is not a canonical JSON object");
        }
        com.google.gson.JsonElement value = parsed.getAsJsonObject().get("formatVersion");
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new ContainerValidationException("Source manifest formatVersion is missing");
        }
        try {
            return com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatVersion.parse(
                    value.getAsString()
            );
        } catch (IllegalArgumentException exception) {
            throw new ContainerValidationException("Source manifest formatVersion is invalid", exception);
        }
    }

    private static void validateManifestOrderAndTypes(SourceManifest manifest) {
        String previous = null;
        for (SourceEntryDescriptor entry : manifest.entries()) {
            String current = entry.path().value();
            if (previous != null && previous.compareTo(current) >= 0) {
                throw new ContainerValidationException("Source manifest entries are not strictly sorted");
            }
            previous = current;
            MediaType expected = current.endsWith(".png")
                    ? MediaType.IMAGE_PNG
                    : MediaType.APPLICATION_JSON;
            if (entry.mediaType() != expected) {
                throw new ContainerValidationException("Source manifest media type does not match path");
            }
        }
    }

    private static void requireAuthorityEntries(SourceManifest manifest) {
        Set<ContainerPath> required = Set.of(
                MinimapContainerLayout.SOURCE_DOCUMENT,
                MinimapContainerLayout.SOURCE_REGIONS,
                MinimapContainerLayout.CONNECTIONS,
                MinimapContainerLayout.SOURCE_STYLES
        );
        Set<ContainerPath> actual = new java.util.HashSet<>();
        manifest.entries().forEach(entry -> actual.add(entry.path()));
        if (!actual.containsAll(required)) {
            throw new ContainerValidationException("Source map is missing an authority JSON entry");
        }
    }

    private static <T> CanonicalModelJson.Document<T> readModel(
            CanonicalZipReader.Archive archive,
            ContainerPath path,
            long maxBytes,
            com.mojang.serialization.Codec<T> codec,
            Set<String> fields
    ) {
        long length = archive.entryLength(path);
        if (length > maxBytes) {
            throw new ContainerValidationException("Source JSON entry exceeds its byte limit: " + path);
        }
        return CanonicalModelJson.read(archive.entryBytes(path), codec, fields);
    }

    private static void validateOpaqueEntries(
            CanonicalZipReader.Archive archive,
            SourceManifest manifest
    ) {
        for (SourceEntryDescriptor entry : manifest.entries()) {
            ContainerPath path = entry.path();
            if (path.equals(MinimapContainerLayout.SOURCE_DOCUMENT)
                    || path.equals(MinimapContainerLayout.SOURCE_REGIONS)
                    || path.equals(MinimapContainerLayout.CONNECTIONS)
                    || path.equals(MinimapContainerLayout.SOURCE_STYLES)) {
                continue;
            }
            if (entry.mediaType() == MediaType.APPLICATION_JSON) {
                if (entry.byteLength() > MinimapHardLimits.MAX_JSON_ENTRY_BYTES) {
                    throw new ContainerValidationException("Source JSON entry exceeds its byte limit: " + path);
                }
                byte[] bytes = archive.entryBytes(path);
                byte[] canonical = JcsCanonicalizer.canonicalize(bytes);
                if (!Arrays.equals(bytes, canonical)) {
                    throw new ContainerValidationException("Source JSON entry is not canonical: " + path);
                }
            } else if (entry.mediaType() == MediaType.IMAGE_PNG) {
                MinimapContainerLayout.SourceEntryKind kind = MinimapContainerLayout.classifySource(path)
                        .orElseThrow(() -> new ContainerValidationException("Invalid source path: " + path));
                if (kind == MinimapContainerLayout.SourceEntryKind.LAYER_TILE
                        || kind == MinimapContainerLayout.SourceEntryKind.LAYER_MASK
                        || kind == MinimapContainerLayout.SourceEntryKind.ASSET_TILE) {
                    continue;
                }
                BoundedPngReader.decode(archive.entryBytes(path));
            }
        }
    }
}
