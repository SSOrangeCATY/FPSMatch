package com.phasetranscrystal.fpsmatch.core.minimap.model;

import java.util.Objects;

public record MinimapDefinition(
        SourceManifest manifest,
        SourceDocument document,
        RegionsFile regions,
        ConnectionsFile connections,
        StylesFile styles
) {
    public MinimapDefinition {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(regions, "regions");
        Objects.requireNonNull(connections, "connections");
        Objects.requireNonNull(styles, "styles");
    }
}
