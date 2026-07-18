package com.phasetranscrystal.fpsmatch.core.minimap.editor.vector;

import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasPoint;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.VectorObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectTransformTest {
    @Test
    void moveCopyCutDeletePreserveIdsAccordingToOperation() {
        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(128, 128), 64, "ground", DisplayLabel.literal("Ground"));
        document.addFloor("upper", DisplayLabel.literal("Upper"));
        document.createLayer("ground", LayerType.VECTOR, DisplayLabel.literal("Vectors"));
        document.createLayer("upper", LayerType.VECTOR, DisplayLabel.literal("Vectors"));
        ObjectEditor editor = ObjectEditor.bind(document);

        VectorObject original = editor.createVector(VectorObject.line(
                "route_a",
                "ground",
                List.of(new CanvasPoint(0, 0), new CanvasPoint(10, 0)),
                NamespacedId.parse("fpsmatch:route"),
                1.0
        ));
        assertEquals("route_a", original.id());

        VectorObject moved = editor.moveVector("route_a", 5, 3);
        assertEquals("route_a", moved.id());
        assertEquals(new CanvasPoint(5, 3), moved.linePoints().get(0));
        assertEquals(new CanvasPoint(15, 3), moved.linePoints().get(1));

        VectorObject copied = editor.copyVector("route_a", "upper");
        assertNotEquals("route_a", copied.id());
        assertEquals("upper", copied.floorId());
        assertTrue(editor.vectorIds("ground").contains("route_a"));
        assertTrue(editor.vectorIds("upper").contains(copied.id()));

        VectorObject cut = editor.cutVector("route_a", "upper");
        assertEquals("route_a", cut.id());
        assertEquals("upper", cut.floorId());
        assertTrue(editor.vectorIds("ground").isEmpty() || !editor.vectorIds("ground").contains("route_a"));
        assertTrue(editor.vectorIds("upper").contains("route_a"));

        editor.deleteVector("route_a");
        assertThrows(IllegalArgumentException.class, () -> editor.vector("route_a"));
        assertTrue(editor.vectorIds("upper").contains(copied.id()));
    }

    @Test
    void transformIsIsolatedAcrossFloors() {
        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(64, 64), 32, "ground", DisplayLabel.literal("Ground"));
        document.addFloor("upper", DisplayLabel.literal("Upper"));
        ObjectEditor editor = ObjectEditor.bind(document);
        editor.createVector(VectorObject.line(
                "route_a", "ground",
                List.of(new CanvasPoint(0, 0), new CanvasPoint(2, 0)),
                NamespacedId.parse("fpsmatch:route"), 1.0));
        editor.createVector(VectorObject.line(
                "route_b", "upper",
                List.of(new CanvasPoint(0, 0), new CanvasPoint(2, 0)),
                NamespacedId.parse("fpsmatch:route"), 1.0));

        editor.moveVector("route_a", 10, 0);
        assertEquals(new CanvasPoint(10, 0), editor.vector("route_a").linePoints().get(0));
        assertEquals(new CanvasPoint(0, 0), editor.vector("route_b").linePoints().get(0));
    }
}
