package com.phasetranscrystal.fpsmatch.core.minimap.model;

import java.util.Objects;

public record RuntimeDefinition(
        RuntimeManifest manifest,
        RuntimeRegionsFile regions,
        ConnectionsFile connections,
        RuntimeStylesFile styles
) {
    public RuntimeDefinition {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(regions, "regions");
        Objects.requireNonNull(connections, "connections");
        Objects.requireNonNull(styles, "styles");
    }
}
