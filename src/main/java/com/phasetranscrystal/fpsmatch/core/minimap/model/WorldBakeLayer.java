package com.phasetranscrystal.fpsmatch.core.minimap.model;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;

import java.util.Objects;

public record WorldBakeLayer(LayerCommon common, String generatorId) implements MinimapLayer {
    public WorldBakeLayer {
        Objects.requireNonNull(common, "common");
        if (!MinimapFormatContract.isInternalSlug(generatorId)) {
            throw new IllegalArgumentException("World bake generator ID must be a valid internal slug");
        }
    }

    @Override
    public LayerType type() {
        return LayerType.WORLD_BAKE;
    }
}
