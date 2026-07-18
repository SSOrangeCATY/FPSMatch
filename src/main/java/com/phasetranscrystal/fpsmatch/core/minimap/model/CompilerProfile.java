package com.phasetranscrystal.fpsmatch.core.minimap.model;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatVersion;

import java.util.Objects;

public record CompilerProfile(NamespacedId id, MinimapFormatVersion version) {
    public CompilerProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
    }
}
