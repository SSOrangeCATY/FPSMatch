package com.phasetranscrystal.fpsmatch.common.client.minimap.tactical;

import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasRect;
import com.phasetranscrystal.fpsmatch.core.minimap.view.ViewportCamera;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TacticalViewportTest {
    private static final TacticalViewport VIEWPORT = new TacticalViewport(
            new CanvasBounds(64, 64),
            new CanvasRect(8, 16, 56, 48),
            256,
            128
    );

    @Test
    void computesExactFitAndZoomLimits() {
        assertEquals(2.0, VIEWPORT.fitAllZoom(), 1e-9);
        assertEquals(4.0, VIEWPORT.fitFloorZoom(), 1e-9);
        assertEquals(1.0, VIEWPORT.minZoom(), 1e-9);
        assertEquals(8.0, VIEWPORT.maxZoom(), 1e-9);

        ViewportCamera all = VIEWPORT.fitAll();
        assertEquals(32.0, all.panX(), 1e-9);
        assertEquals(32.0, all.panY(), 1e-9);
        assertEquals(2.0, all.zoom(), 1e-9);

        ViewportCamera floor = VIEWPORT.fitFloor();
        assertEquals(32.0, floor.panX(), 1e-9);
        assertEquals(32.0, floor.panY(), 1e-9);
        assertEquals(4.0, floor.zoom(), 1e-9);
    }

    @Test
    void wheelZoomUsesOnePointTwoFiveAndKeepsCursorAnchored() {
        ViewportCamera camera = VIEWPORT.fitAll();
        double canvasUnderCursor = camera.screenToWorldX(64);

        ViewportCamera zoomed = VIEWPORT.zoomByWheel(camera, 1, 64, 0);

        assertEquals(2.5, zoomed.zoom(), 1e-9);
        assertEquals(canvasUnderCursor, zoomed.screenToWorldX(64), 1e-9);
        assertEquals(1.0,
                VIEWPORT.zoomByWheel(camera, -100, 0, 0).zoom(), 1e-9);
        assertEquals(8.0,
                VIEWPORT.zoomByWheel(camera, 100, 0, 0).zoom(), 1e-9);
    }

    @Test
    void dragUsesScreenPixelsAndKeepsTenPercentViewportIntersection() {
        ViewportCamera camera = VIEWPORT.fitAll();

        ViewportCamera draggedRight = VIEWPORT.panByPixels(
                camera, 10_000, 0
        );
        ViewportCamera draggedLeft = VIEWPORT.panByPixels(
                camera, -10_000, 0
        );

        assertEquals(-51.2, draggedRight.panX(), 1e-9);
        assertEquals(115.2, draggedLeft.panX(), 1e-9);
        assertEquals(32.0, draggedRight.panY(), 1e-9);
    }
}
