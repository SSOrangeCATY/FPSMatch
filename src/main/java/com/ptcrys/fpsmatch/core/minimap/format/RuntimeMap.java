package com.ptcrys.fpsmatch.core.minimap.format;

import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeDefinition;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeManifest;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;

public final class RuntimeMap implements Closeable {
    private final RuntimeDefinition definition;
    private final CanonicalZipReader.Archive archive;
    private final byte[] manifestBytes;
    private final Sha256 runtimeHash;
    private final Map<ContainerPath, PreservedExtensions> authorityExtensions;

    RuntimeMap(
            RuntimeDefinition definition,
            CanonicalZipReader.Archive archive,
            byte[] manifestBytes,
            Map<ContainerPath, PreservedExtensions> authorityExtensions
    ) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.archive = Objects.requireNonNull(archive, "archive");
        this.manifestBytes = Objects.requireNonNull(manifestBytes, "manifestBytes").clone();
        this.runtimeHash = Sha256Digest.of(this.manifestBytes);
        this.authorityExtensions = Map.copyOf(new LinkedHashMap<>(authorityExtensions));
    }

    public RuntimeDefinition definition() {
        return definition;
    }

    public RuntimeManifest manifest() {
        return definition.manifest();
    }

    public Sha256 runtimeHash() {
        return runtimeHash;
    }

    public Sha256 manifestHash() {
        return runtimeHash;
    }

    public Sha256 containerHash() {
        return archive.containerSha256();
    }

    public Sha256 runtimeContainerHash() {
        return archive.containerSha256();
    }

    public byte[] manifestBytes() {
        return manifestBytes.clone();
    }

    public Map<ContainerPath, PreservedExtensions> authorityExtensions() {
        return authorityExtensions;
    }

    public Set<ContainerPath> paths() {
        return archive.paths();
    }

    public long entryLength(ContainerPath path) {
        return archive.entryLength(path);
    }

    public byte[] entryBytes(ContainerPath path) {
        return archive.entryBytes(path);
    }

    public InputStream openEntry(ContainerPath path) {
        return archive.openBounded(path);
    }

    @Override
    public void close() throws IOException {
        archive.close();
    }
}
