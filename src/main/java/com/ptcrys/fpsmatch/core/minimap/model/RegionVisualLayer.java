package com.ptcrys.fpsmatch.core.minimap.model;

import java.util.List;
import java.util.Objects;

public record RegionVisualLayer(LayerCommon common, List<String> regionIds) implements MinimapLayer {
    public RegionVisualLayer {
        Objects.requireNonNull(common, "common");
        regionIds = LayerReferences.copyAndValidate(regionIds, "region");
    }

    @Override
    public LayerType type() {
        return LayerType.REGION_VISUAL;
    }
}
