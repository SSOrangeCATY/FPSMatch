package com.phasetranscrystal.fpsmatch.core.minimap.editor.vector;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.phasetranscrystal.fpsmatch.core.minimap.codec.MinimapModelCodecs;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasPoint;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasRect;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapRegion;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RectangleGeometry;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RegionStyleOverride;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RegionsFile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionEditorTest {
    @Test
    void semanticRegionsRoundTripOnlyThroughRegionsFile() {
        MinimapRegion region = region("site_a");
        RegionsFile file = new RegionsFile(List.of(region));
        JsonElement encoded = MinimapModelCodecs.REGIONS_FILE.encodeStart(JsonOps.INSTANCE, file)
                .result().orElseThrow();
        assertEquals(file, MinimapModelCodecs.REGIONS_FILE.parse(JsonOps.INSTANCE, encoded)
                .result().orElseThrow());
        String json = encoded.toString();
        assertTrue(json.contains("site_a"));
        assertFalse(json.contains("vectorIds"));
    }

    @Test
    void regionEditorCreatesUpdatesAndDeletesThroughDocumentStore() {
        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(64, 64), 32, "ground", DisplayLabel.literal("Ground"));
        document.createLayer("ground", LayerType.REGION_VISUAL, DisplayLabel.literal("Regions"));
        ObjectEditor editor = ObjectEditor.bind(document);

        MinimapRegion created = editor.createRegion(region("site_a"));
        assertEquals("site_a", created.id());
        assertEquals(List.of("site_a"), editor.regionIds("ground"));

        MinimapRegion moved = editor.updateRegion(RegionMutations.withGeometry(
                region("site_a"),
                new RectangleGeometry(new CanvasRect(20, 20, 40, 40))));
        assertEquals(20, ((RectangleGeometry) moved.geometry()).bounds().minU());

        editor.deleteRegion("site_a");
        assertTrue(editor.regionIds("ground").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> editor.region("site_a"));
    }

    @Test
    void regionVertexAndVisibilityEditsAreDeterministic() {
        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(64, 64), 32, "ground", DisplayLabel.literal("Ground"));
        ObjectEditor editor = ObjectEditor.bind(document);
        editor.createRegion(region("site_a"));
        MinimapRegion updated = editor.updateRegionVisibility("site_a", 0.25, 4.0, 7,
                new CanvasPoint(12, 14));
        assertEquals(0.25, updated.minVisibleScale());
        assertEquals(4.0, updated.maxVisibleScale());
        assertEquals(7, updated.priority());
        assertEquals(new CanvasPoint(12, 14), updated.labelAnchor());
    }

    private static MinimapRegion region(String id) {
        return new MinimapRegion(
                id,
                "ground",
                DisplayLabel.literal(id),
                new RectangleGeometry(new CanvasRect(0, 0, 10, 10)),
                NamespacedId.parse("fpsmatch:site"),
                List.of(),
                Optional.empty(),
                NamespacedId.parse("fpsmatch:site-style"),
                RegionStyleOverride.empty(),
                new CanvasPoint(5, 5),
                0,
                0.0,
                16.0
        );
    }
}
