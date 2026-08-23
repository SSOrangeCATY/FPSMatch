package com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2.editor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class EditorLdlibWidgetCatalog {
    public static final String CANVAS = "fpsmatch.minimap.editor.canvas";
    public static final String TOOLBAR = "fpsmatch.minimap.editor.toolbar";
    public static final String LAYER_PANEL = "fpsmatch.minimap.editor.layer_panel";
    public static final String PROPERTIES = "fpsmatch.minimap.editor.properties";
    public static final String FLOOR_STRIP = "fpsmatch.minimap.editor.floor_strip";
    public static final String STATUS_BAR = "fpsmatch.minimap.editor.status_bar";

    private final Set<String> ids;

    private EditorLdlibWidgetCatalog(Set<String> ids) {
        this.ids = Set.copyOf(ids);
    }

    public static EditorLdlibWidgetCatalog defaultCatalog() {
        return new EditorLdlibWidgetCatalog(new LinkedHashSet<>(List.of(
                CANVAS,
                TOOLBAR,
                LAYER_PANEL,
                PROPERTIES,
                FLOOR_STRIP,
                STATUS_BAR
        )));
    }

    public Set<String> ids() {
        return ids;
    }

    public boolean contains(String id) {
        return ids.contains(Objects.requireNonNull(id, "id"));
    }
}
