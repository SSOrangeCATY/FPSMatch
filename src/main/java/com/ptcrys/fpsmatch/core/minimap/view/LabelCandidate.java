package com.ptcrys.fpsmatch.core.minimap.view;

import java.util.Objects;

public record LabelCandidate(
        String id,
        double x,
        double y,
        double width,
        double height,
        int priority
) {
    public LabelCandidate {
        Objects.requireNonNull(id, "id");
        if (!(width > 0) || !(height > 0)) {
            throw new IllegalArgumentException("Label size must be positive");
        }
    }

    public LabelCandidate(
            String id,
            double x,
            double y,
            double width,
            double height
    ) {
        this(id, x, y, width, height, 0);
    }
}
