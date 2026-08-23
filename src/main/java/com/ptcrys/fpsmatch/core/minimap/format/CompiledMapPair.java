package com.ptcrys.fpsmatch.core.minimap.format;

import com.ptcrys.fpsmatch.core.minimap.model.RuntimeDefinition;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeManifest;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.model.SourceManifest;

import java.util.Objects;

public final class CompiledMapPair {
    private final SourceManifest sourceManifest;
    private final RuntimeDefinition runtimeDefinition;
    private final byte[] runtimeBytes;
    private final byte[] runtimeManifestBytes;
    private final Sha256 sourceHash;
    private final Sha256 runtimeHash;
    private final Sha256 runtimeContainerHash;

    CompiledMapPair(
            SourceManifest sourceManifest,
            RuntimeDefinition runtimeDefinition,
            byte[] runtimeBytes,
            byte[] runtimeManifestBytes,
            Sha256 sourceHash,
            Sha256 runtimeHash,
            Sha256 runtimeContainerHash
    ) {
        this.sourceManifest = Objects.requireNonNull(sourceManifest, "sourceManifest");
        this.runtimeDefinition = Objects.requireNonNull(runtimeDefinition, "runtimeDefinition");
        this.runtimeBytes = Objects.requireNonNull(runtimeBytes, "runtimeBytes").clone();
        this.runtimeManifestBytes = Objects.requireNonNull(runtimeManifestBytes, "runtimeManifestBytes").clone();
        this.sourceHash = Objects.requireNonNull(sourceHash, "sourceHash");
        this.runtimeHash = Objects.requireNonNull(runtimeHash, "runtimeHash");
        this.runtimeContainerHash = Objects.requireNonNull(runtimeContainerHash, "runtimeContainerHash");
    }

    public SourceManifest sourceManifest() {
        return sourceManifest;
    }

    public RuntimeDefinition runtimeDefinition() {
        return runtimeDefinition;
    }

    public RuntimeManifest runtimeManifest() {
        return runtimeDefinition.manifest();
    }

    public byte[] runtimeBytes() {
        return runtimeBytes.clone();
    }

    public byte[] runtimeContainer() {
        return runtimeBytes();
    }

    public byte[] runtimeManifestBytes() {
        return runtimeManifestBytes.clone();
    }

    public Sha256 sourceHash() {
        return sourceHash;
    }

    public Sha256 runtimeHash() {
        return runtimeHash;
    }

    public Sha256 runtimeContainerHash() {
        return runtimeContainerHash;
    }

    public Sha256 containerHash() {
        return runtimeContainerHash;
    }

    public static void verifyBinding(SourceMap source, RuntimeMap runtime) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(runtime, "runtime");
        runtime.containerHash();
        if (!source.sourceHash().equals(runtime.manifest().sourceHash())) {
            throw new ContainerValidationException("Source/runtime source hash does not match");
        }
        if (!source.manifest().documentId().equals(runtime.manifest().documentId())) {
            throw new ContainerValidationException("Source/runtime document ID does not match");
        }
        if (!source.manifest().binding().equals(runtime.manifest().binding())) {
            throw new ContainerValidationException("Source/runtime map binding does not match");
        }
        if (source.manifest().revision() != runtime.manifest().publishRevision()) {
            throw new ContainerValidationException("Source/runtime publish revision does not match");
        }
    }
}
