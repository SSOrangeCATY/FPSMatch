package com.ptcrys.fpsmatch.core.minimap.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record SourceDocument(
        WorldBounds worldBounds,
        CanvasBounds canvas,
        DefaultViewMode defaultViewMode,
        List<SourceFloor> floors,
        Map<String, List<String>> layerOrder
) {
    public SourceDocument {
        Objects.requireNonNull(worldBounds, "worldBounds");
        Objects.requireNonNull(canvas, "canvas");
        Objects.requireNonNull(defaultViewMode, "defaultViewMode");
        floors = List.copyOf(floors);
        Objects.requireNonNull(layerOrder, "layerOrder");
        Map<String, List<String>> copiedOrder = new LinkedHashMap<>();
        layerOrder.forEach((floorId, order) -> copiedOrder.put(floorId, List.copyOf(order)));
        layerOrder = Collections.unmodifiableMap(copiedOrder);
    }
}
