package com.ptcrys.fpsmatch.core.minimap.model;

import java.util.Objects;
import java.util.Optional;

public record RuntimeStyle(
        NamespacedId id,
        Optional<TextAppearance> label,
        Optional<IconAppearance> icon
) {
    public RuntimeStyle {
        Objects.requireNonNull(id, "id");
        label = Objects.requireNonNull(label, "label");
        icon = Objects.requireNonNull(icon, "icon");
    }
}
