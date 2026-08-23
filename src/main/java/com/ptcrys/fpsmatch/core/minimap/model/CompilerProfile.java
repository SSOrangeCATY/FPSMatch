package com.ptcrys.fpsmatch.core.minimap.model;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapFormatVersion;

import java.util.Objects;

public record CompilerProfile(NamespacedId id, MinimapFormatVersion version) {
    public CompilerProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
    }
}
