package com.phasetranscrystal.fpsmatch.core.minimap.editor.document;

import java.util.ArrayList;
import java.util.List;

public final class DirtyRegionSet {
    private final List<DirtyRegion> regions = new ArrayList<>();

    public void add(DirtyRegion region) {
        if (region == null || region.isEmpty()) {
            return;
        }
        DirtyRegion pending = region;
        boolean merged;
        do {
            merged = false;
            for (int index = 0; index < regions.size(); index++) {
                DirtyRegion existing = regions.get(index);
                if (existing.intersectsOrTouches(pending)) {
                    pending = existing.union(pending);
                    regions.remove(index);
                    merged = true;
                    break;
                }
            }
        } while (merged);
        regions.add(pending);
    }

    public List<DirtyRegion> snapshot() {
        return List.copyOf(regions);
    }

    public void clear() {
        regions.clear();
    }

    public boolean isEmpty() {
        return regions.isEmpty();
    }
}
