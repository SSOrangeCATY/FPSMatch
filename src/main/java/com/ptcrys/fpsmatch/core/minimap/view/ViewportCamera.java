package com.ptcrys.fpsmatch.core.minimap.view;

import com.ptcrys.fpsmatch.core.minimap.model.AffineTransform2D;
import com.ptcrys.fpsmatch.core.minimap.model.CanvasPoint;
import com.ptcrys.fpsmatch.core.minimap.model.WorldPoint2D;

import java.util.Objects;

public final class ViewportCamera {
    private final double panX;
    private final double panY;
    private final double zoom;
    private final double viewportWidth;
    private final double viewportHeight;
    private final float rotationDegrees;
    private final float yawOffsetDegrees;

    private ViewportCamera(
            double panX,
            double panY,
            double zoom,
            double viewportWidth,
            double viewportHeight,
            float rotationDegrees,
            float yawOffsetDegrees
    ) {
        if (!(zoom > 0)
                || !Double.isFinite(zoom)
                || !Double.isFinite(panX)
                || !Double.isFinite(panY)) {
            throw new IllegalArgumentException(
                    "Camera values must be finite and zoom > 0"
            );
        }
        if (!(viewportWidth > 0) || !(viewportHeight > 0)) {
            throw new IllegalArgumentException("Viewport size must be positive");
        }
        if (!Float.isFinite(rotationDegrees)
                || !Float.isFinite(yawOffsetDegrees)) {
            throw new IllegalArgumentException("Camera rotations must be finite");
        }
        this.panX = panX;
        this.panY = panY;
        this.zoom = zoom;
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        this.rotationDegrees = rotationDegrees;
        this.yawOffsetDegrees = yawOffsetDegrees;
    }

    public static ViewportCamera fixedNorth(
            double panX,
            double panY,
            double zoom,
            double width,
            double height
    ) {
        return oriented(panX, panY, zoom, width, height, 0f, 0f);
    }

    public static ViewportCamera playerUp(
            double panX,
            double panY,
            double zoom,
            double width,
            double height,
            float playerYawDegrees
    ) {
        if (!Float.isFinite(playerYawDegrees)) {
            throw new IllegalArgumentException("playerYaw must be finite");
        }
        return oriented(
                panX,
                panY,
                zoom,
                width,
                height,
                playerYawDegrees,
                -playerYawDegrees
        );
    }

    public static ViewportCamera oriented(
            double panX,
            double panY,
            double zoom,
            double width,
            double height,
            float rotationDegrees,
            float yawOffsetDegrees
    ) {
        return new ViewportCamera(
                panX,
                panY,
                zoom,
                width,
                height,
                rotationDegrees,
                yawOffsetDegrees
        );
    }

    public static ViewportCamera fit(
            AffineTransform2D worldToCanvas,
            WorldPoint2D min,
            WorldPoint2D max,
            double width,
            double height,
            double paddingFraction
    ) {
        Objects.requireNonNull(worldToCanvas, "worldToCanvas");
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        CanvasPoint a = worldToCanvas.transform(min);
        CanvasPoint b = worldToCanvas.transform(max);
        double minU = Math.min(a.u(), b.u());
        double maxU = Math.max(a.u(), b.u());
        double minV = Math.min(a.v(), b.v());
        double maxV = Math.max(a.v(), b.v());
        double contentWidth = Math.max(1e-6, maxU - minU);
        double contentHeight = Math.max(1e-6, maxV - minV);
        double padding = Math.max(0.0, paddingFraction);
        double zoom = Math.min(
                width / (contentWidth * (1 + 2 * padding)),
                height / (contentHeight * (1 + 2 * padding))
        );
        double centerU = (minU + maxU) * 0.5;
        double centerV = (minV + maxV) * 0.5;
        return fixedNorth(centerU, centerV, zoom, width, height);
    }

    public double panX() {
        return panX;
    }

    public double panY() {
        return panY;
    }

    public double zoom() {
        return zoom;
    }

    public double viewportWidth() {
        return viewportWidth;
    }

    public double viewportHeight() {
        return viewportHeight;
    }

    public float rotationDegrees() {
        return rotationDegrees;
    }

    public ViewportCamera panBy(double dx, double dy) {
        double maxPanX = viewportWidth * 0.5 * 1.10 / zoom;
        double maxPanY = viewportHeight * 0.5 * 1.10 / zoom;
        return new ViewportCamera(
                clamp(panX + dx, -maxPanX, maxPanX),
                clamp(panY + dy, -maxPanY, maxPanY),
                zoom,
                viewportWidth,
                viewportHeight,
                rotationDegrees,
                yawOffsetDegrees
        );
    }

    public ViewportCamera zoomAt(
            double newZoom,
            double cursorScreenX,
            double cursorScreenY
    ) {
        double nextZoom = clamp(newZoom, 0.25, 16.0);
        double worldX = screenToWorldX(cursorScreenX);
        double worldY = screenToWorldY(cursorScreenY);
        ViewportCamera provisional = new ViewportCamera(
                panX,
                panY,
                nextZoom,
                viewportWidth,
                viewportHeight,
                rotationDegrees,
                yawOffsetDegrees
        );
        double afterX = provisional.screenToWorldX(cursorScreenX);
        double afterY = provisional.screenToWorldY(cursorScreenY);
        return new ViewportCamera(
                panX + (worldX - afterX),
                panY + (worldY - afterY),
                nextZoom,
                viewportWidth,
                viewportHeight,
                rotationDegrees,
                yawOffsetDegrees
        ).panBy(0, 0);
    }

    public ProjectedPose project(
            AffineTransform2D worldToCanvas,
            double x,
            double y,
            double z,
            float yawDegrees
    ) {
        Objects.requireNonNull(worldToCanvas, "worldToCanvas");
        CanvasPoint canvas = worldToCanvas.transform(new WorldPoint2D(x, z));
        return projectCanvas(canvas.u(), canvas.v(), yawDegrees);
    }

    public ProjectedPose projectCanvas(
            double canvasX,
            double canvasY,
            float yawDegrees
    ) {
        if (!Double.isFinite(canvasX)
                || !Double.isFinite(canvasY)
                || !Float.isFinite(yawDegrees)) {
            throw new IllegalArgumentException(
                    "Projected canvas pose must be finite"
            );
        }
        double localX = canvasX - panX;
        double localY = canvasY - panY;
        if (rotationDegrees != 0f) {
            double radians = Math.toRadians(rotationDegrees);
            double cosine = Math.cos(radians);
            double sine = Math.sin(radians);
            double rotatedX = localX * cosine - localY * sine;
            double rotatedY = localX * sine + localY * cosine;
            localX = rotatedX;
            localY = rotatedY;
        }
        return new ProjectedPose(
                localX * zoom,
                localY * zoom,
                yawDegrees + yawOffsetDegrees
        );
    }

    public boolean isInsideClip(
            ShapeMode shape,
            double screenX,
            double screenY
    ) {
        Objects.requireNonNull(shape, "shape");
        double halfWidth = viewportWidth * 0.5;
        double halfHeight = viewportHeight * 0.5;
        return switch (shape) {
            case SQUARE -> Math.abs(screenX) <= halfWidth
                    && Math.abs(screenY) <= halfHeight;
            case CIRCLE -> {
                double normalizedX = screenX / halfWidth;
                double normalizedY = screenY / halfHeight;
                yield normalizedX * normalizedX
                        + normalizedY * normalizedY <= 1.0;
            }
        };
    }

    public double screenToWorldX(double screenX) {
        return panX + screenX / zoom;
    }

    public double screenToWorldY(double screenY) {
        return panY + screenY / zoom;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
