package com.phasetranscrystal.fpsmatch.core.minimap.editor.vector;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.phasetranscrystal.fpsmatch.core.minimap.codec.MinimapModelCodecs;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasPoint;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasRect;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.FillStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.IconAppearance;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.PolygonGeometry;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RectangleGeometry;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RgbaColor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.StrokeStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.TextAppearance;
import com.phasetranscrystal.fpsmatch.core.minimap.model.VectorObject;
import com.phasetranscrystal.fpsmatch.core.minimap.model.VectorObjectType;
import com.phasetranscrystal.fpsmatch.core.minimap.model.VectorsFile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VectorObjectCodecTest {
    @Test
    void freezesVectorObjectTypes() {
        assertEquals(List.of("LINE", "RECTANGLE", "POLYGON", "TEXT", "ICON"),
                java.util.Arrays.stream(VectorObjectType.values()).map(Enum::name).toList());
    }

    @Test
    void lineRectanglePolygonTextAndIconRoundTripThroughVectorsFile() {
        VectorsFile file = new VectorsFile(List.of(
                line(),
                rectangle(),
                polygon(),
                text(),
                icon()
        ));
        JsonElement encoded = MinimapModelCodecs.VECTORS_FILE.encodeStart(JsonOps.INSTANCE, file)
                .result().orElseThrow();
        assertEquals(file, MinimapModelCodecs.VECTORS_FILE.parse(JsonOps.INSTANCE, encoded)
                .result().orElseThrow());
    }

    @Test
    void vectorIdsMustBeInternalSlugsAndUniqueInFile() {
        assertThrows(IllegalArgumentException.class, () -> lineWithId("Bad/ID"));
        assertThrows(IllegalArgumentException.class, () -> new VectorsFile(List.of(line(), line())));
    }

    private static VectorObject line() {
        return lineWithId("route_a");
    }

    private static VectorObject lineWithId(String id) {
        return VectorObject.line(
                id,
                "ground",
                List.of(new CanvasPoint(0, 0), new CanvasPoint(10, 0)),
                NamespacedId.parse("fpsmatch:route"),
                1.0
        );
    }

    private static VectorObject rectangle() {
        return VectorObject.rectangle(
                "box_a",
                "ground",
                new RectangleGeometry(new CanvasRect(1, 2, 5, 6)),
                NamespacedId.parse("fpsmatch:box"),
                Optional.of(new FillStyle(new RgbaColor(1, 2, 3, 255), 0.5)),
                1.0
        );
    }

    private static VectorObject polygon() {
        return VectorObject.polygon(
                "poly_a",
                "ground",
                new PolygonGeometry(List.of(
                        new CanvasPoint(0, 0),
                        new CanvasPoint(4, 0),
                        new CanvasPoint(4, 4),
                        new CanvasPoint(0, 4)
                )),
                NamespacedId.parse("fpsmatch:poly"),
                Optional.empty(),
                1.0
        );
    }

    private static VectorObject text() {
        return VectorObject.text(
                "label_a",
                "ground",
                new CanvasPoint(8, 8),
                DisplayLabel.literal("Hello"),
                NamespacedId.parse("fpsmatch:label"),
                new TextAppearance(new RgbaColor(255, 255, 255, 255), 1.0),
                1.0
        );
    }

    private static VectorObject icon() {
        return VectorObject.icon(
                "icon_a",
                "ground",
                new CanvasPoint(12, 12),
                new IconAppearance(NamespacedId.parse("fpsmatch:marker"), 1.0),
                1.0
        );
    }
}
