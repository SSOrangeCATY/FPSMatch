package com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2.editor;

import com.ptcrys.fpsmatch.common.client.minimap.editor.MinimapEditorController;

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
        return EditorUiLayoutModel.responsive(width, height);
    }
}
