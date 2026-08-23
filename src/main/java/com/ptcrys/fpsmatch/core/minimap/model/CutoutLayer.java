package com.ptcrys.fpsmatch.core.minimap.model;

import java.util.Objects;

public record CutoutLayer(LayerCommon common) implements MinimapLayer {
    public CutoutLayer {
        Objects.requireNonNull(common, "common");
        if (common.blendMode() != BlendMode.NORMAL || common.maskEnabled()) {
            throw new IllegalArgumentException("Cutout layers use fixed DST_OUT and cannot use a mask");
        }
    }

    @Override
    public LayerType type() {
        return LayerType.CUTOUT;
    }
}
