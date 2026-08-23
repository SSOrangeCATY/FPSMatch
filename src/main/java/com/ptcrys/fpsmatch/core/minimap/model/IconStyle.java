package com.ptcrys.fpsmatch.core.minimap.model;

import java.util.Objects;

public record IconStyle(NamespacedId id, IconAppearance icon) implements MinimapStyle {
    public IconStyle {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(icon, "icon");
    }

    @Override
    public StyleType type() {
        return StyleType.ICON;
    }
}
