package com.ptcrys.fpsmatch.core.minimap.model;

import java.util.List;

public record FloorCalibration(
        List<ControlPoint> controlPoints,
        boolean allowMirror,
        double maxResidualPixels
) {
    public FloorCalibration {
        controlPoints = List.copyOf(controlPoints);
        if (controlPoints.size() < 3) {
            throw new IllegalArgumentException("Floor calibration requires at least three control points");
        }
        if (!Double.isFinite(maxResidualPixels) || maxResidualPixels < 0) {
            throw new IllegalArgumentException("Maximum calibration residual must be finite and non-negative");
        }
    }

    public AffineFit fit() {
        return AffineFit.fit(controlPoints, allowMirror);
    }
}
