package com.ptcrys.fpsmatch.core.minimap.model;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;

import java.util.Objects;

public record SourceEntryDescriptor(
        ContainerPath path,
        long byteLength,
        MediaType mediaType,
        Sha256 sha256
) {
    public SourceEntryDescriptor {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(sha256, "sha256");
        if (byteLength < 0 || byteLength > MinimapHardLimits.MAX_ZIP_ENTRY_BYTES) {
            throw new IllegalArgumentException("Source entry length exceeds the hard limit");
        }
    }
}
