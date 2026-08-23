package com.ptcrys.fpsmatch.core.minimap.model;

public record CanvasRect(double minU, double minV, double maxU, double maxV) {
    public CanvasRect {
        if (!Double.isFinite(minU) || !Double.isFinite(minV)
                || !Double.isFinite(maxU) || !Double.isFinite(maxV)
                || minU >= maxU || minV >= maxV) {
            throw new IllegalArgumentException("Canvas rectangle must be finite and non-empty");
        }
    }
}
