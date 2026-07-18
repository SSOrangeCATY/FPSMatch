package com.phasetranscrystal.fpsmatch.core.minimap.model;

public sealed interface MinimapLayer permits ImportedImageLayer, WorldBakeLayer, RasterPaintLayer,
        VectorLayer, RegionVisualLayer, CutoutLayer {
    LayerCommon common();

    LayerType type();

    default CompositionOperator operator() {
        return type().operator();
    }
}
