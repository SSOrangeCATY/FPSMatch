package com.ptcrys.fpsmatch.common.client.minimap.editor;

public final class EditorCanvasState {
    private double panX;
    private double panY;
    private double zoom = 1.0;

    public double panX() {
        return panX;
    }

    public double panY() {
        return panY;
    }

    public double zoom() {
        return zoom;
    }

    void panBy(double dx, double dy) {
        this.panX += dx;
        this.panY += dy;
    }

    void zoomBy(double delta) {
        double next = zoom + delta;
        if (next < 0.25) {
            next = 0.25;
        }
        if (next > 8.0) {
            next = 8.0;
        }
        this.zoom = next;
    }

    void setViewport(double panX, double panY, double zoom) {
        if (!Double.isFinite(panX) || !Double.isFinite(panY)
                || !Double.isFinite(zoom) || zoom < 0.25 || zoom > 8.0) {
            throw new IllegalArgumentException("Viewport values are outside the supported range");
        }
        this.panX = panX;
        this.panY = panY;
        this.zoom = zoom;
    }
}
