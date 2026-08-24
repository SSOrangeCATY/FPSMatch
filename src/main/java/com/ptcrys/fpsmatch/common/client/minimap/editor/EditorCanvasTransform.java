package com.ptcrys.fpsmatch.common.client.minimap.editor;

import java.util.Objects;

/** Platform-neutral conversions between screen-space canvas coordinates and document coordinates. */
public final class EditorCanvasTransform {
    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 8.0;

    public DocumentPoint toDocument(
            double screenX,
            double screenY,
            ViewportRect canvas,
            EditorCanvasState state
    ) {
        requireFinite(screenX, "screenX");
        requireFinite(screenY, "screenY");
        requireCanvas(canvas);
        requireState(state);
        return new DocumentPoint(
                state.panX() + (screenX - canvas.x()) / state.zoom(),
                state.panY() + (screenY - canvas.y()) / state.zoom());
    }

    public void zoomAt(
            double delta,
            double anchorX,
            double anchorY,
            ViewportRect canvas,
            EditorCanvasState state
    ) {
        requireFinite(delta, "delta");
        requireFinite(anchorX, "anchorX");
        requireFinite(anchorY, "anchorY");
        requireCanvas(canvas);
        requireState(state);

        double nextZoom = clamp(state.zoom() + delta, MIN_ZOOM, MAX_ZOOM);
        double documentX = state.panX() + (anchorX - canvas.x()) / state.zoom();
        double documentY = state.panY() + (anchorY - canvas.y()) / state.zoom();
        double nextPanX = documentX - (anchorX - canvas.x()) / nextZoom;
        double nextPanY = documentY - (anchorY - canvas.y()) / nextZoom;
        if (!Double.isFinite(nextPanX) || !Double.isFinite(nextPanY)) {
            throw new IllegalArgumentException("Zoom would produce a non-finite viewport");
        }
        state.setViewport(nextPanX, nextPanY, nextZoom);
    }

    private static void requireCanvas(ViewportRect canvas) {
        Objects.requireNonNull(canvas, "canvas");
        requireFinite(canvas.x(), "canvas.x");
        requireFinite(canvas.y(), "canvas.y");
        requireFinite(canvas.width(), "canvas.width");
        requireFinite(canvas.height(), "canvas.height");
        if (canvas.width() <= 0.0 || canvas.height() <= 0.0) {
            throw new IllegalArgumentException("Canvas dimensions must be positive");
        }
    }

    private static void requireState(EditorCanvasState state) {
        Objects.requireNonNull(state, "state");
        requireFinite(state.panX(), "state.panX");
        requireFinite(state.panY(), "state.panY");
        requireFinite(state.zoom(), "state.zoom");
        if (state.zoom() < MIN_ZOOM || state.zoom() > MAX_ZOOM) {
            throw new IllegalArgumentException("Viewport zoom is outside the supported range");
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record ViewportRect(double x, double y, double width, double height) {
    }

    public record DocumentPoint(double x, double y) {
    }
}
