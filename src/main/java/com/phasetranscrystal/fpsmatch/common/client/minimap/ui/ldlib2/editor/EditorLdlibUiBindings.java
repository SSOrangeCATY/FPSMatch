package com.phasetranscrystal.fpsmatch.common.client.minimap.ui.ldlib2.editor;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Validates version-controlled LDLib2 widget bindings for the minimap editor.
 * Missing/duplicate bindings fail closed; there is no vanilla/ModernUI fallback path.
 */
public final class EditorLdlibUiBindings {
    private EditorLdlibUiBindings() {
    }

    public static void validate(
            EditorLdlibWidgetCatalog catalog,
            List<String> boundIds,
            List<String> forbiddenFallbackImports
    ) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(boundIds, "boundIds");
        Objects.requireNonNull(forbiddenFallbackImports, "forbiddenFallbackImports");
        for (String forbidden : forbiddenFallbackImports) {
            if (forbidden != null && !forbidden.isBlank()) {
                throw new EditorUiBindingException(
                        "Minimap editor forbids fallback UI imports: " + forbidden
                );
            }
        }
        Set<String> seen = new HashSet<>();
        for (String id : boundIds) {
            if (!catalog.contains(id)) {
                throw new EditorUiBindingException("Unknown editor widget binding: " + id);
            }
            if (!seen.add(id)) {
                throw new EditorUiBindingException("Duplicate editor widget binding: " + id);
            }
        }
        for (String required : catalog.ids()) {
            if (!seen.contains(required)) {
                throw new EditorUiBindingException("Missing required editor widget binding: " + required);
            }
        }
    }
}