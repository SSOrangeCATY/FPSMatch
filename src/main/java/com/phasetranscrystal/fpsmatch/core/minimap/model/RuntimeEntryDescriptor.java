package com.phasetranscrystal.fpsmatch.core.minimap.model;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;

import java.util.Objects;

public record RuntimeEntryDescriptor(ContainerPath path, long byteLength, Sha256 sha256) {
    public RuntimeEntryDescriptor {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(sha256, "sha256");
        if (byteLength < 0 || byteLength > MinimapHardLimits.MAX_ZIP_ENTRY_BYTES) {
            throw new IllegalArgumentException("Runtime entry length exceeds the hard limit");
        }
    }
}
