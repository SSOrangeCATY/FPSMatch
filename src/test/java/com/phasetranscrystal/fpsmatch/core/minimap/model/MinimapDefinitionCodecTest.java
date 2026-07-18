package com.phasetranscrystal.fpsmatch.core.minimap.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.phasetranscrystal.fpsmatch.core.minimap.codec.MinimapModelCodecs;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MinimapDefinitionCodecTest {
    @Test
    void sourceDocumentRoundTripsOnlyItsAuthoritativeFields() {
        SourceDocument document = validDocument();
        JsonElement encoded = MinimapModelCodecs.SOURCE_DOCUMENT
                .encodeStart(JsonOps.INSTANCE, document)
                .result().orElseThrow();
        JsonObject root = encoded.getAsJsonObject();

        assertEquals(Set.of("worldBounds", "canvas", "defaultViewMode", "floors", "layerOrder"),
                root.keySet());
        for (String manifestOnly : List.of(
                "formatVersion", "documentId", "binding", "revision", "dimension", "provenance", "entries"
        )) {
            assertFalse(root.has(manifestOnly), manifestOnly);
        }
        JsonObject floor = root.getAsJsonArray("floors").get(0).getAsJsonObject();
        assertFalse(floor.has("worldToCanvas"));
        assertFalse(floor.has("northVector"));
        JsonArray layers = floor.getAsJsonArray("layers");
        assertEquals(List.of("imported_image", "raster_paint", "cutout"), layers.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .map(layer -> layer.get("type").getAsString())
                .toList());

        SourceDocument decoded = MinimapModelCodecs.SOURCE_DOCUMENT
                .parse(JsonOps.INSTANCE, encoded)
                .result().orElseThrow();
        assertEquals(document, decoded);
    }

    static SourceDocument validDocument() {
        List<MinimapLayer> layers = List.of(
                new ImportedImageLayer(
                        LayerCompositionContractTest.common("imported", BlendMode.NORMAL, false), "overview"
                ),
                new RasterPaintLayer(
                        LayerCompositionContractTest.common("paint", BlendMode.SCREEN, true)
                ),
                new CutoutLayer(
                        LayerCompositionContractTest.common("cutout", BlendMode.NORMAL, false)
                )
        );
        SourceFloor floor = new SourceFloor(
                new MinimapFloor("ground", -10, 20, 0, 0.5, 1),
                DisplayLabel.translation("fpsmatch.minimap.floor.ground"),
                Optional.of(new CanvasRect(0, 0, 512, 512)),
                new FloorBackground(new RgbaColor(12, 18, 24, 255)),
                approvedCalibration(),
                layers
        );
        return new SourceDocument(
                new WorldBounds(0, 0, 100, 100),
                new CanvasBounds(512, 512),
                DefaultViewMode.FULL_MAP,
                List.of(floor),
                Map.of("ground", List.of("imported", "paint", "cutout"))
        );
    }

    static FloorCalibration approvedCalibration() {
        return new FloorCalibration(List.of(
                point(0, 0, 100, 200),
                point(100, 0, 300, 175),
                point(0, 100, 150, 350),
                point(100, 100, 350, 325)
        ), false, 2.0);
    }

    private static ControlPoint point(double x, double z, double u, double v) {
        return new ControlPoint(new WorldPoint2D(x, z), new CanvasPoint(u, v));
    }
}
