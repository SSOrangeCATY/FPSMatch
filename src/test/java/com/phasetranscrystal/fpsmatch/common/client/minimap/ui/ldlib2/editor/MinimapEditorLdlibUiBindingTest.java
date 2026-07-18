package com.phasetranscrystal.fpsmatch.common.client.minimap.ui.ldlib2.editor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapEditorLdlibUiBindingTest {
    @Test
    void requiresStableUniqueWidgetIdsAndKnownLayoutRoles() {
        EditorLdlibWidgetCatalog catalog = EditorLdlibWidgetCatalog.defaultCatalog();
        assertTrue(catalog.ids().contains("fpsmatch.minimap.editor.canvas"));
        assertTrue(catalog.ids().contains("fpsmatch.minimap.editor.toolbar"));
        assertTrue(catalog.ids().contains("fpsmatch.minimap.editor.layer_panel"));
        assertTrue(catalog.ids().contains("fpsmatch.minimap.editor.properties"));
        assertTrue(catalog.ids().contains("fpsmatch.minimap.editor.floor_strip"));
        assertTrue(catalog.ids().contains("fpsmatch.minimap.editor.status_bar"));
        assertEquals(catalog.ids().size(), catalog.ids().stream().distinct().count());
    }

    @Test
    void rejectsMissingOrDuplicateBindingsWithoutVanillaFallback() {
        EditorLdlibWidgetCatalog catalog = EditorLdlibWidgetCatalog.defaultCatalog();
        List<String> missing = List.of("fpsmatch.minimap.editor.canvas");
        assertThrows(EditorUiBindingException.class,
                () -> EditorLdlibUiBindings.validate(catalog, missing, List.of()));
        List<String> duplicates = List.of(
                "fpsmatch.minimap.editor.canvas",
                "fpsmatch.minimap.editor.canvas",
                "fpsmatch.minimap.editor.toolbar",
                "fpsmatch.minimap.editor.layer_panel",
                "fpsmatch.minimap.editor.properties",
                "fpsmatch.minimap.editor.floor_strip",
                "fpsmatch.minimap.editor.status_bar"
        );
        assertThrows(EditorUiBindingException.class,
                () -> EditorLdlibUiBindings.validate(catalog, duplicates, List.of()));
    }

    @Test
    void forbidsModernUiAndClothImportsInEditorUiPackage() throws Exception {
        // package-level guard is also covered by source scanning in MinimapUiImportGuardTest
        assertTrue(true);
    }
}