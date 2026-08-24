package com.ptcrys.fpsmatch.common.client.minimap.editor;

import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorEdit;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorCommandException;
import com.ptcrys.fpsmatch.core.minimap.editor.document.EditableLayer;
import com.ptcrys.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.ptcrys.fpsmatch.core.minimap.editor.raster.BrushStamp;
import com.ptcrys.fpsmatch.core.minimap.editor.raster.IntPoint;
import com.ptcrys.fpsmatch.core.minimap.model.LayerType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Coordinates one pointer gesture without exposing LDLib2 to editor document logic. */
public final class EditorCanvasGesture {
    private static final int EDIT_BUTTON = 0;

    private final MinimapEditorController controller;
    private final EditorCanvasTransform transform = new EditorCanvasTransform();
    private final List<IntPoint> path = new ArrayList<>();

    private EditorDocument gestureDocument;
    private EditorTool gestureTool;
    private String gestureFloorId;
    private String gestureLayerId;
    private int gestureBrushSize;
    private int gestureColor;
    private boolean editingEnabled = true;
    private boolean active;

    public EditorCanvasGesture(MinimapEditorController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    public boolean begin(
            double x,
            double y,
            int button,
            EditorCanvasTransform.ViewportRect viewport
    ) {
        cancel();
        if (button != EDIT_BUTTON || controller.isClosed()) {
            return false;
        }
        EditorTool tool = controller.toolState().tool();
        if (!editingEnabled && tool != EditorTool.PAN) {
            return false;
        }
        if (tool == EditorTool.PAN) {
            requireDocumentPoint(x, y, viewport);
            gestureDocument = controller.document();
            gestureTool = tool;
            active = true;
            return true;
        }
        if (tool != EditorTool.BRUSH && tool != EditorTool.ERASER) {
            return false;
        }

        EditorDocument document = controller.document();
        IntPoint point = toPoint(x, y, viewport, document, false);
        if (point == null || !hasEditableSelection(document)) {
            return false;
        }
        gestureDocument = document;
        gestureTool = tool;
        gestureFloorId = controller.selectedFloorId();
        gestureLayerId = controller.selectedLayerId();
        gestureBrushSize = controller.toolState().brushSize();
        gestureColor = controller.toolState().colorArgb();
        path.add(point);
        active = true;
        return true;
    }

    public void drag(
            double x,
            double y,
            double deltaX,
            double deltaY,
            EditorCanvasTransform.ViewportRect viewport
    ) {
        if (!active) {
            return;
        }
        if (controller.document() != gestureDocument) {
            cancel();
            return;
        }
        if (gestureTool == EditorTool.PAN) {
            requireFinite(deltaX, "deltaX");
            requireFinite(deltaY, "deltaY");
            double zoom = controller.canvasState().zoom();
            controller.panBy(-deltaX / zoom, -deltaY / zoom);
            return;
        }
        appendPoint(toPoint(x, y, viewport, gestureDocument, true));
    }

    public boolean end(
            double x,
            double y,
            EditorCanvasTransform.ViewportRect viewport
    ) {
        if (!active) {
            return false;
        }
        if (controller.document() != gestureDocument) {
            cancel();
            return false;
        }
        if (gestureTool == EditorTool.PAN) {
            cancel();
            return false;
        }
        appendPoint(toPoint(x, y, viewport, gestureDocument, true));

        EditorDocument document = gestureDocument;
        EditorTool tool = gestureTool;
        List<IntPoint> completedPath = List.copyOf(path);
        String floorId = gestureFloorId;
        String layerId = gestureLayerId;
        int brushSize = gestureBrushSize;
        int color = gestureColor;
        cancel();

        if (document != controller.document() || floorId == null || layerId == null) {
            return false;
        }
        try {
            EditorCanvasInteractor interactor = new EditorCanvasInteractor(document);
            BrushStamp stamp = BrushStamp.round(brushSize, true);
            EditorEdit edit = tool == EditorTool.ERASER
                    ? interactor.erasePath(floorId, layerId, completedPath, stamp)
                    : interactor.brushPath(floorId, layerId, completedPath, stamp, color);
            controller.recordAppliedEdit(edit);
            return true;
        } catch (EditorCommandException noChange) {
            return false;
        }
    }

    public void zoomBy(
            double delta,
            double anchorX,
            double anchorY,
            EditorCanvasTransform.ViewportRect viewport
    ) {
        cancel();
        transform.zoomAt(delta, anchorX, anchorY, viewport, controller.canvasState());
    }

    public void cancel() {
        active = false;
        gestureDocument = null;
        gestureTool = null;
        gestureFloorId = null;
        gestureLayerId = null;
        gestureBrushSize = 0;
        gestureColor = 0;
        path.clear();
    }

    public void setEditingEnabled(boolean editingEnabled) {
        if (this.editingEnabled == editingEnabled) {
            return;
        }
        this.editingEnabled = editingEnabled;
        if (!editingEnabled) {
            cancelEditingGesture();
        }
    }

    public boolean cancelEditingGesture() {
        if (!active || gestureTool == EditorTool.PAN) {
            return false;
        }
        cancel();
        return true;
    }

    public boolean active() {
        return active;
    }

    private boolean hasEditableSelection(EditorDocument document) {
        String floorId = controller.selectedFloorId();
        String layerId = controller.selectedLayerId();
        if (floorId == null || layerId == null) {
            return false;
        }
        try {
            EditableLayer layer = document.layer(floorId, layerId);
            return layer.type() == LayerType.RASTER_PAINT
                    && layer.visible() && !layer.locked();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private IntPoint toPoint(
            double x,
            double y,
            EditorCanvasTransform.ViewportRect viewport,
            EditorDocument document,
            boolean clipOutside
    ) {
        EditorCanvasTransform.DocumentPoint point = transform.toDocument(
                x, y, viewport, controller.canvasState()
        );
        if (!clipOutside && (point.x() < 0.0 || point.y() < 0.0
                || point.x() >= document.canvas().width()
                || point.y() >= document.canvas().height())) {
            return null;
        }
        int margin = BrushStamp.MAX_SIZE;
        int pointX = boundedFloor(point.x(), -margin, document.canvas().width() - 1 + margin);
        int pointY = boundedFloor(point.y(), -margin, document.canvas().height() - 1 + margin);
        return new IntPoint(pointX, pointY);
    }

    private void appendPoint(IntPoint point) {
        if (point != null && (path.isEmpty() || !path.get(path.size() - 1).equals(point))) {
            path.add(point);
        }
    }

    private void requireDocumentPoint(
            double x,
            double y,
            EditorCanvasTransform.ViewportRect viewport
    ) {
        transform.toDocument(x, y, viewport, controller.canvasState());
    }

    private static int boundedFloor(double value, int minimum, int maximum) {
        requireFinite(value, "document coordinate");
        return (int) Math.max(minimum, Math.min(maximum, Math.floor(value)));
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
