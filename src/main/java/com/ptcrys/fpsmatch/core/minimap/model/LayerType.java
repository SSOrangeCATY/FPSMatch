package com.ptcrys.fpsmatch.core.minimap.model;

public enum LayerType {
    IMPORTED_IMAGE(CompositionOperator.SOURCE_OVER),
    WORLD_BAKE(CompositionOperator.SOURCE_OVER),
    RASTER_PAINT(CompositionOperator.SOURCE_OVER),
    VECTOR(CompositionOperator.SOURCE_OVER),
    REGION_VISUAL(CompositionOperator.SOURCE_OVER),
    CUTOUT(CompositionOperator.DST_OUT);

    private final CompositionOperator operator;

    LayerType(CompositionOperator operator) {
        this.operator = operator;
    }

    public CompositionOperator operator() {
        return operator;
    }
}
