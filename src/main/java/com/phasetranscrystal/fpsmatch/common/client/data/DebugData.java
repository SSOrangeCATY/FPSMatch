package com.phasetranscrystal.fpsmatch.common.client.data;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class DebugData {
    private final Map<String, RenderableArea> areas = new LinkedHashMap<>();
    private final Map<String, RenderablePoint> points = new LinkedHashMap<>();

    public void addRenderableArea(RenderableArea area) {
        upsertRenderableArea(area.key(), area);
    }

    public void upsertRenderableArea(String key, RenderableArea area) {
        areas.put(key, area);
    }

    public void upsertRenderablePoint(String key, RenderablePoint point) {
        points.put(key, point);
    }

    public Collection<RenderableArea> getAreas() {
        return areas.values();
    }

    public Collection<RenderablePoint> getPoints() {
        return points.values();
    }

    public void removeByPrefix(String prefix) {
        areas.keySet().removeIf(key -> key.startsWith(prefix));
        points.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public void clearAll() {
        areas.clear();
        points.clear();
    }

    public void clearAreas() {
        clearAll();
    }
}
