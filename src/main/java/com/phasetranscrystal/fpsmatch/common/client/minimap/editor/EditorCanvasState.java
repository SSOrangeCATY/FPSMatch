package com.phasetranscrystal.fpsmatch.common.client.minimap.editor;

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
}