package com.phasetranscrystal.fpsmatch.core.minimap.model;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;

public record CanvasBounds(int width, int height) {
    public CanvasBounds {
        if (width <= 0 || height <= 0
                || width > MinimapHardLimits.MAX_CANVAS_EDGE
                || height > MinimapHardLimits.MAX_CANVAS_EDGE) {
            throw new IllegalArgumentException("Canvas dimensions exceed the hard limit");
        }
    }

    public boolean contains(CanvasPoint point) {
        return point.u() >= 0 && point.u() <= width
                && point.v() >= 0 && point.v() <= height;
    }

    public boolean contains(CanvasRect rect) {
        return rect.minU() >= 0 && rect.minV() >= 0
                && rect.maxU() <= width && rect.maxV() <= height;
    }
}
