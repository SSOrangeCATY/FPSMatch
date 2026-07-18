package com.phasetranscrystal.fpsmatch.common.client.minimap.tactical;

import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasRect;
import com.phasetranscrystal.fpsmatch.core.minimap.view.ViewportCamera;

import java.util.Objects;

public final class TacticalViewport {
    private static final double WHEEL_FACTOR = 1.25;
    private static final double MIN_INTERSECTION_FRACTION = 0.10;

    private final CanvasBounds canvas;
    private final CanvasRect floorBounds;
    private final int viewportWidth;
    private final int viewportHeight;

    public TacticalViewport(
            CanvasBounds canvas,
            CanvasRect floorBounds,
            int viewportWidth,
            int viewportHeight
    ) {
        this.canvas = Objects.requireNonNull(canvas, "canvas");
        this.floorBounds = Objects.requireNonNull(floorBounds, "floorBounds");
        if (!canvas.contains(floorBounds)) {
            throw new IllegalArgumentException(
                    "Floor bounds must be inside the tactical canvas"
            );
        }
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            throw new IllegalArgumentException(
                    "Tactical viewport must be positive"
            );
        }
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
    }

    public double fitAllZoom() {
        return fitZoom(0, 0, canvas.width(), canvas.height());
    }

    public double fitFloorZoom() {
        return fitZoom(
                floorBounds.minU(), floorBounds.minV(),
                floorBounds.maxU(), floorBounds.maxV()
        );
    }

    public double minZoom() {
        return 0.5 * fitAllZoom();
    }

    public double maxZoom() {
        return Math.max(8.0, Math.max(fitFloorZoom(), fitAllZoom()));
    }

    public ViewportCamera fitAll() {
        return cameraFor(
                0, 0, canvas.width(), canvas.height(), fitAllZoom()
        );
    }

    public ViewportCamera fitFloor() {
        return cameraFor(
                floorBounds.minU(), floorBounds.minV(),
                floorBounds.maxU(), floorBounds.maxV(), fitFloorZoom()
        );
    }

    public ViewportCamera constrain(ViewportCamera camera) {
        Objects.requireNonNull(camera, "camera");
        double zoom = clamp(camera.zoom(), minZoom(), maxZoom());
        return constrainedCamera(camera.panX(), camera.panY(), zoom);
    }

    public ViewportCamera zoomByWheel(
            ViewportCamera camera,
            double wheelTicks,
            double cursorX,
            double cursorY
    ) {
        Objects.requireNonNull(camera, "camera");
        if (!Double.isFinite(wheelTicks)
                || !Double.isFinite(cursorX)
                || !Double.isFinite(cursorY)) {
            throw new IllegalArgumentException(
                    "Tactical zoom input must be finite"
            );
        }
        ViewportCamera current = constrain(camera);
        double zoom = clamp(
                current.zoom() * Math.pow(WHEEL_FACTOR, wheelTicks),
                minZoom(), maxZoom()
        );
        double canvasX = current.screenToWorldX(cursorX);
        double canvasY = current.screenToWorldY(cursorY);
        double panX = canvasX - cursorX / zoom;
        double panY = canvasY - cursorY / zoom;
        return constrainedCamera(panX, panY, zoom);
    }

    public ViewportCamera panByPixels(
            ViewportCamera camera,
            double deltaX,
            double deltaY
    ) {
        Objects.requireNonNull(camera, "camera");
        if (!Double.isFinite(deltaX) || !Double.isFinite(deltaY)) {
            throw new IllegalArgumentException(
                    "Tactical pan input must be finite"
            );
        }
        ViewportCamera current = constrain(camera);
        return constrainedCamera(
                current.panX() - deltaX / current.zoom(),
                current.panY() - deltaY / current.zoom(),
                current.zoom()
        );
    }

    private ViewportCamera constrainedCamera(
            double panX,
            double panY,
            double zoom
    ) {
        double visibleWidth = viewportWidth / zoom;
        double visibleHeight = viewportHeight / zoom;
        double requiredWidth = visibleWidth * MIN_INTERSECTION_FRACTION;
        double requiredHeight = visibleHeight * MIN_INTERSECTION_FRACTION;
        double minPanX = requiredWidth - visibleWidth * 0.5;
        double maxPanX = canvas.width() - requiredWidth
                + visibleWidth * 0.5;
        double minPanY = requiredHeight - visibleHeight * 0.5;
        double maxPanY = canvas.height() - requiredHeight
                + visibleHeight * 0.5;
        return ViewportCamera.fixedNorth(
                clamp(panX, minPanX, maxPanX),
                clamp(panY, minPanY, maxPanY),
                zoom,
                viewportWidth,
                viewportHeight
        );
    }

    private double fitZoom(
            double minU,
            double minV,
            double maxU,
            double maxV
    ) {
        return Math.min(
                viewportWidth / (maxU - minU),
                viewportHeight / (maxV - minV)
        );
    }

    private ViewportCamera cameraFor(
            double minU,
            double minV,
            double maxU,
            double maxV,
            double zoom
    ) {
        return ViewportCamera.fixedNorth(
                (minU + maxU) * 0.5,
                (minV + maxV) * 0.5,
                zoom,
                viewportWidth,
                viewportHeight
        );
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
