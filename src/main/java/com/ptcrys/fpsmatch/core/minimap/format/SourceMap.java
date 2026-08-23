package com.ptcrys.fpsmatch.core.minimap.format;

import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.MinimapDefinition;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.model.SourceManifest;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SourceMap implements Closeable {
    private static final Set<ContainerPath> GENERATED_AUTHORITY_PATHS = Set.of(
            ContainerPath.parse("manifest.json"),
            ContainerPath.parse("document.json"),
            ContainerPath.parse("regions.json"),
            ContainerPath.parse("connections.json"),
            ContainerPath.parse("styles.json")
    );

    private final MinimapDefinition definition;
    private final CanonicalZipReader.Archive archive;
    private final Map<ContainerPath, PreservedExtensions> authorityExtensions;

    SourceMap(
            MinimapDefinition definition,
            CanonicalZipReader.Archive archive,
            Map<ContainerPath, PreservedExtensions> authorityExtensions
    ) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.archive = Objects.requireNonNull(archive, "archive");
        this.authorityExtensions = Map.copyOf(new LinkedHashMap<>(authorityExtensions));
    }

    public MinimapDefinition definition() {
        return definition;
    }

    public SourceManifest manifest() {
        return definition.manifest();
    }

    public Sha256 sourceHash() {
        return archive.containerSha256();
    }

    public Sha256 containerHash() {
        return archive.containerSha256();
    }

    public Set<ContainerPath> paths() {
        return archive.paths();
    }

    public long entryLength(ContainerPath path) {
        return archive.entryLength(path);
    }

    public Sha256 entrySha256(ContainerPath path) {
        return archive.entrySha256(path);
    }

    public byte[] entryBytes(ContainerPath path) {
        return archive.entryBytes(path);
    }

    public InputStream openEntry(ContainerPath path) {
        return archive.openBounded(path);
    }

    public Map<ContainerPath, PreservedExtensions> authorityExtensions() {
        return authorityExtensions;
    }

    public SourceMapDraft toDraft() {
        List<CanonicalZipWriter.EntrySource> entries = new ArrayList<>();
        for (ContainerPath path : archive.paths()) {
            if (!GENERATED_AUTHORITY_PATHS.contains(path)) {
                entries.add(entrySource(path));
            }
        }
        return new SourceMapDraft(definition, entries, authorityExtensions);
    }

    public CanonicalZipWriter.EntrySource entrySource(ContainerPath path) {
        Objects.requireNonNull(path, "path");
        long size = archive.entryLength(path);
        return new CanonicalZipWriter.EntrySource() {
            @Override
            public ContainerPath path() {
                return path;
            }

            @Override
            public long size() {
                return size;
            }

            @Override
            public InputStream openStream() {
                return openEntry(path);
            }
        };
    }

    @Override
    public void close() throws IOException {
        archive.close();
    }
}
