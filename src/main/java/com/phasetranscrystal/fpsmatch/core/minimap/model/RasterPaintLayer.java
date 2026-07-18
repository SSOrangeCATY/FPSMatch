package com.phasetranscrystal.fpsmatch.core.minimap.model;

import java.util.Objects;

public record RasterPaintLayer(LayerCommon common) implements MinimapLayer {
    public RasterPaintLayer {
        Objects.requireNonNull(common, "common");
    }

    @Override
    public LayerType type() {
        return LayerType.RASTER_PAINT;
    }
}
