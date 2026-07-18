package com.phasetranscrystal.fpsmatch.common.client.minimap.render;

public record MinimapViewerPose(
        double x,
        double y,
        double z,
        float yawDegrees
) {
    public MinimapViewerPose {
        if (!Double.isFinite(x)
                || !Double.isFinite(y)
                || !Double.isFinite(z)
                || !Float.isFinite(yawDegrees)) {
            throw new IllegalArgumentException("Viewer pose must be finite");
        }
    }
}
