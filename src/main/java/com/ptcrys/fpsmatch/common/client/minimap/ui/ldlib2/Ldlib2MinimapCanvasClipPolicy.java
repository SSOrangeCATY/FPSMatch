package com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2;

import com.ptcrys.fpsmatch.common.client.minimap.render.MinimapFrame;
import com.ptcrys.fpsmatch.core.minimap.view.ShapeMode;

import java.util.Objects;

final class Ldlib2MinimapCanvasClipPolicy {
    private Ldlib2MinimapCanvasClipPolicy() {
    }

    static boolean usesCircularClip(ShapeMode shape) {
        return Objects.requireNonNull(shape, "shape") == ShapeMode.CIRCLE;
    }

    static float circularRadius(MinimapFrame frame) {
        Objects.requireNonNull(frame, "frame");
        return (float) (Math.min(
                frame.camera().viewportWidth(),
                frame.camera().viewportHeight()
        ) / 2.0);
    }
}
