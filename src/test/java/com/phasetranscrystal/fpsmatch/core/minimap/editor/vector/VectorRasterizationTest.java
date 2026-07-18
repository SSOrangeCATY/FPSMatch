package com.phasetranscrystal.fpsmatch.core.minimap.editor.vector;

import com.phasetranscrystal.fpsmatch.core.minimap.editor.raster.Rgba8;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasPoint;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasRect;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.FillStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RectangleGeometry;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RgbaColor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.TextAppearance;
import com.phasetranscrystal.fpsmatch.core.minimap.model.VectorObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorRasterizationTest {
    @Test
    void rasterizerReturnsPlatformNeutralDrawPrimitivesAndPatches() {
        VectorObject line = VectorObject.line(
                "route_a", "ground",
                List.of(new CanvasPoint(0, 0), new CanvasPoint(3, 0)),
                NamespacedId.parse("fpsmatch:route"),
                1.0
        );
        VectorObject rect = VectorObject.rectangle(
                "box_a", "ground",
                new RectangleGeometry(new CanvasRect(1, 1, 4, 3)),
                NamespacedId.parse("fpsmatch:box"),
                Optional.of(new FillStyle(new RgbaColor(10, 20, 30, 255), 1.0)),
                1.0
        );
        VectorObject text = VectorObject.text(
                "label_a", "ground",
                new CanvasPoint(2, 2),
                DisplayLabel.literal("A"),
                NamespacedId.parse("fpsmatch:label"),
                new TextAppearance(new RgbaColor(255, 255, 255, 255), 1.0),
                1.0
        );

        VectorDrawPlan plan = VectorRasterizer.plan(List.of(line, rect, text));
        assertFalse(plan.primitives().isEmpty());
        assertTrue(plan.primitives().stream().anyMatch(p -> p.kind() == VectorDrawPrimitive.Kind.LINE));
        assertTrue(plan.primitives().stream().anyMatch(p -> p.kind() == VectorDrawPrimitive.Kind.RECT_FILL));
        assertTrue(plan.primitives().stream().anyMatch(p -> p.kind() == VectorDrawPrimitive.Kind.TEXT));
        assertTrue(plan.primitives().stream().noneMatch(p -> p.getClass().getName().contains("minecraft")));

        int[] patch = VectorRasterizer.rasterizeRectanglePatch(rect, 8, 8);
        assertEquals(8 * 8, patch.length);
        assertEquals(Rgba8.of(10, 20, 30, 255), patch[1 * 8 + 1]);
        assertEquals(0, patch[0]);
    }
}
