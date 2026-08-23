package com.ptcrys.fpsmatch.core.minimap.editor.vector;

import com.ptcrys.fpsmatch.core.minimap.model.CanvasPoint;
import com.ptcrys.fpsmatch.core.minimap.model.DisplayLabel;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record VectorDrawPrimitive(
        Kind kind,
        String objectId,
        List<CanvasPoint> points,
        Optional<DisplayLabel> text,
        int rgba,
        double opacity
) {
    public VectorDrawPrimitive {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(objectId, "objectId");
        points = List.copyOf(points);
        text = Objects.requireNonNull(text, "text");
    }

    public enum Kind {
        LINE,
        RECT_FILL,
        RECT_STROKE,
        POLYGON_FILL,
        TEXT,
        ICON
    }
}
