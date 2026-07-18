package com.phasetranscrystal.fpsmatch.common.client.minimap.ui.ldlib2.editor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class EditorLdlibWidgetCatalog {
    private final Set<String> ids;

    private EditorLdlibWidgetCatalog(Set<String> ids) {
        this.ids = Set.copyOf(ids);
    }

    public static EditorLdlibWidgetCatalog defaultCatalog() {
        return new EditorLdlibWidgetCatalog(new LinkedHashSet<>(List.of(
                "fpsmatch.minimap.editor.canvas",
                "fpsmatch.minimap.editor.toolbar",
                "fpsmatch.minimap.editor.layer_panel",
                "fpsmatch.minimap.editor.properties",
                "fpsmatch.minimap.editor.floor_strip",
                "fpsmatch.minimap.editor.status_bar"
        )));
    }

    public Set<String> ids() {
        return ids;
    }

    public boolean contains(String id) {
        return ids.contains(Objects.requireNonNull(id, "id"));
    }
}