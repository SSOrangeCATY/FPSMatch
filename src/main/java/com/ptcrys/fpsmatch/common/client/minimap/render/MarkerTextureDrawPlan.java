package com.ptcrys.fpsmatch.common.client.minimap.render;

import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;

import java.util.Objects;
import java.util.Optional;

public record MarkerTextureDrawPlan(
        NamespacedId textureId,
        int left,
        int top,
        int size,
        float yawDegrees,
        float opacity
) {
    public MarkerTextureDrawPlan {
        Objects.requireNonNull(textureId, "textureId");
        if (size < 8 || size > 32
                || !Float.isFinite(yawDegrees)
                || !Float.isFinite(opacity)
                || opacity < 0f
                || opacity > 1f) {
            throw new IllegalArgumentException("Invalid marker texture draw plan");
        }
    }

    public static Optional<MarkerTextureDrawPlan> create(
            double centerX,
            double centerY,
            float yawDegrees,
            float opacity,
            Optional<MarkerPresentationResolver.Resolved> presentation
    ) {
        Objects.requireNonNull(presentation, "presentation");
        if (!Double.isFinite(centerX) || !Double.isFinite(centerY)) {
            throw new IllegalArgumentException("Marker center must be finite");
        }
        return presentation.map(resolved -> {
            int size = (int) Math.round(resolved.sizePixels());
            return new MarkerTextureDrawPlan(
                    resolved.textureId(),
                    (int) Math.floor(centerX - size * 0.5),
                    (int) Math.floor(centerY - size * 0.5),
                    size,
                    yawDegrees,
                    opacity
            );
        });
    }
}
