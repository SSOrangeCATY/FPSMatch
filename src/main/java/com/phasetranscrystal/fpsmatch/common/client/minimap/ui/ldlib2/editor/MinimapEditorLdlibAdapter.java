package com.phasetranscrystal.fpsmatch.common.client.minimap.ui.ldlib2.editor;

import com.phasetranscrystal.fpsmatch.common.client.minimap.editor.MinimapEditorController;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Presentation adapter boundary for LDLib2. Controllers remain free of Widget types.
 * Concrete Widget tree construction lives in client-only LDLib2 UI code that consumes this layout model.
 */
public final class MinimapEditorLdlibAdapter {
    private final MinimapEditorController controller;
    private final EditorLdlibWidgetCatalog catalog;
    private final List<String> boundIds = new ArrayList<>();

    public MinimapEditorLdlibAdapter(MinimapEditorController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.catalog = EditorLdlibWidgetCatalog.defaultCatalog();
        this.boundIds.addAll(catalog.ids());
        EditorLdlibUiBindings.validate(catalog, boundIds, List.of());
    }

    public MinimapEditorController controller() {
        return controller;
    }

    public EditorLdlibWidgetCatalog catalog() {
        return catalog;
    }

    public List<String> layoutRoles() {
        return List.copyOf(boundIds);
    }

    public EditorUiLayoutModel layoutModel(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Layout dimensions must be positive");
        }
        int toolbar = 48;
        int status = 28;
        int side = Math.max(160, width / 5);
        int floor = 36;
        return new EditorUiLayoutModel(
                new EditorUiLayoutModel.Rect(toolbar, 0, width - toolbar - side, height - status - floor),
                new EditorUiLayoutModel.Rect(0, 0, toolbar, height - status),
                new EditorUiLayoutModel.Rect(width - side, 0, side, (height - status) / 2),
                new EditorUiLayoutModel.Rect(width - side, (height - status) / 2, side, (height - status) / 2),
                new EditorUiLayoutModel.Rect(toolbar, height - status - floor, width - toolbar - side, floor),
                new EditorUiLayoutModel.Rect(0, height - status, width, status)
        );
    }
}