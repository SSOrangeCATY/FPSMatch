package com.phasetranscrystal.fpsmatch.common.client.data;

import java.util.LinkedList;
import java.util.List;

public class DebugData {
    private final List<RenderableArea> areas = new LinkedList<>();

    public void addRenderableArea(RenderableArea area) {
        areas.add(area);
    }

    public List<RenderableArea> getAreas() {
        return areas;
    }

    public void clearAreas() {
        areas.clear();
    }
}
