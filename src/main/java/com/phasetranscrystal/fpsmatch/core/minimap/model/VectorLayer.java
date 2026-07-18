package com.phasetranscrystal.fpsmatch.core.minimap.model;

import java.util.List;
import java.util.Objects;

public record VectorLayer(LayerCommon common, List<String> vectorIds) implements MinimapLayer {
    public VectorLayer {
        Objects.requireNonNull(common, "common");
        vectorIds = LayerReferences.copyAndValidate(vectorIds, "vector");
    }

    @Override
    public LayerType type() {
        return LayerType.VECTOR;
    }
}
