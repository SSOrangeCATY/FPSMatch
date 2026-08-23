package com.ptcrys.fpsmatch.core.minimap.model;

import java.util.Objects;

public record TextStyle(NamespacedId id, TextAppearance text) implements MinimapStyle {
    public TextStyle {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(text, "text");
    }

    @Override
    public StyleType type() {
        return StyleType.TEXT;
    }
}
