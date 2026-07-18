package com.phasetranscrystal.fpsmatch.common.client.minimap.ui.ldlib2;

import com.phasetranscrystal.fpsmatch.common.client.minimap.tactical.TacticalMapController;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Presentation boundary for the read-only tactical map.
 * Passive {@link #applyIfOpen} never instantiates or opens the UI; it only refreshes when open.
 * Missing/duplicate bindings are diagnostic failures, not a fallback UI.
 */
public final class Ldlib2TacticalMapUi {
    private final TacticalMapWidgetCatalog catalog = TacticalMapWidgetCatalog.defaultCatalog();
    private final Set<String> boundIds = new HashSet<>();
    private boolean visible;

    public TacticalMapWidgetCatalog catalog() {
        return catalog;
    }

    public void bind(List<String> boundWidgetIds) {
        Objects.requireNonNull(boundWidgetIds, "boundWidgetIds");
        Set<String> required = new HashSet<>(catalog.ids());
        Set<String> provided = new HashSet<>(boundWidgetIds);
        if (!provided.containsAll(required)) {
            Set<String> missing = new HashSet<>(required);
            missing.removeAll(provided);
            throw new IllegalStateException("Missing LDLib2 tactical widget bindings: " + missing);
        }
        for (String id : provided) {
            if (!required.contains(id)) {
                throw new IllegalStateException("Unknown LDLib2 tactical widget binding: " + id);
            }
        }
        if (provided.size() != boundWidgetIds.size()) {
            throw new IllegalStateException("Duplicate LDLib2 tactical widget bindings");
        }
        boundIds.clear();
        boundIds.addAll(required);
    }

    public boolean isBound() {
        return boundIds.containsAll(catalog.ids());
    }

    /**
     * Passive refresh following the existing apply*IfOpen pattern.
     * Never opens the controller or constructs a Screen.
     */
    public void applyIfOpen(TacticalMapController controller) {
        Objects.requireNonNull(controller, "controller");
        if (!controller.isOpen()) {
            visible = false;
            return;
        }
        if (!isBound()) {
            throw new IllegalStateException("LDLib2 tactical UI is not fully bound");
        }
        visible = true;
    }

    public boolean isVisible() {
        return visible;
    }
}