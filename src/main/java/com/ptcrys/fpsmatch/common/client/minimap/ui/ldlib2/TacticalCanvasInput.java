package com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2;

import com.ptcrys.fpsmatch.core.minimap.view.ViewportCamera;

public final class TacticalCanvasInput {
    private TacticalCanvasInput() {
    }

    public static Point centered(
            double mouseX,
            double mouseY,
            double canvasX,
            double canvasY,
            double canvasWidth,
            double canvasHeight
    ) {
        if (!Double.isFinite(mouseX)
                || !Double.isFinite(mouseY)
                || !Double.isFinite(canvasX)
                || !Double.isFinite(canvasY)
                || !Double.isFinite(canvasWidth)
                || !Double.isFinite(canvasHeight)
                || canvasWidth <= 0
                || canvasHeight <= 0) {
            throw new IllegalArgumentException(
                    "Tactical canvas input must be finite and positive"
            );
        }
        return new Point(
                mouseX - canvasX - canvasWidth * 0.5,
                mouseY - canvasY - canvasHeight * 0.5
        );
    }

    public static Point canvasPoint(
            double mouseX,
            double mouseY,
            double canvasX,
            double canvasY,
            double canvasWidth,
            double canvasHeight,
            ViewportCamera camera
    ) {
        Point centered = centered(
                mouseX,
                mouseY,
                canvasX,
                canvasY,
                canvasWidth,
                canvasHeight
        );
        return new Point(
                camera.screenToWorldX(centered.x()),
                camera.screenToWorldY(centered.y())
        );
    }

    public record Point(double x, double y) {
    }
}
