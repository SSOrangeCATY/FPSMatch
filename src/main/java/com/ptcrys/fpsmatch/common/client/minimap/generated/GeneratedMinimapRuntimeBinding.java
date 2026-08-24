package com.ptcrys.fpsmatch.common.client.minimap.generated;

import com.ptcrys.fpsmatch.common.client.minimap.RuntimeGeneration;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeFloor;

import java.util.Objects;

/** Active published runtime identity used to scope generated client tiles. */
public record GeneratedMinimapRuntimeBinding(
        RuntimeGeneration generation,
        RuntimeFloor floor,
        int zoom,
        int tileEdge
) {
    public GeneratedMinimapRuntimeBinding {
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(floor, "floor");
        if (zoom < 0 || zoom >= floor.zoomLevels()) {
            throw new IllegalArgumentException("Generated zoom is outside the active floor");
        }
        if (tileEdge <= 0) {
            throw new IllegalArgumentException("Generated tile edge must be positive");
        }
    }
}
