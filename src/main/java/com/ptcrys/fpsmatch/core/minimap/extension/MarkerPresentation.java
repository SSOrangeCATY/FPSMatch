package com.ptcrys.fpsmatch.core.minimap.extension;

import com.ptcrys.fpsmatch.core.minimap.model.DisplayLabel;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;

import java.util.Objects;

public record MarkerPresentation(
        NamespacedId typeId,
        NamespacedId styleId,
        NamespacedId textureId,
        DisplayLabel label,
        double scale
) {
    public MarkerPresentation {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(styleId, "styleId");
        Objects.requireNonNull(textureId, "textureId");
        Objects.requireNonNull(label, "label");
        if (!Double.isFinite(scale) || scale < 0.5 || scale > 2.0) {
            throw new IllegalArgumentException(
                    "Marker presentation scale must be in [0.5, 2.0]"
            );
        }
    }
}
