package com.phasetranscrystal.fpsmatch.common.client.minimap.ui.ldlib2;

import org.junit.jupiter.api.Test;
import com.phasetranscrystal.fpsmatch.core.minimap.view.ViewportCamera;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TacticalCanvasInputTest {
    @Test
    void convertsAbsoluteMousePositionToCanvasCenteredCoordinates() {
        TacticalCanvasInput.Point point = TacticalCanvasInput.centered(
                420, 260,
                220, 40,
                800, 440
        );

        assertEquals(-200.0, point.x());
        assertEquals(0.0, point.y());
    }

    @Test
    void convertsAbsoluteMousePositionToCanvasCoordinatesThroughCamera() {
        TacticalCanvasInput.Point point = TacticalCanvasInput.canvasPoint(
                620,
                300,
                220,
                40,
                800,
                440,
                ViewportCamera.fixedNorth(32, 24, 2, 800, 440)
        );

        assertEquals(32.0, point.x());
        assertEquals(44.0, point.y());
    }
}
