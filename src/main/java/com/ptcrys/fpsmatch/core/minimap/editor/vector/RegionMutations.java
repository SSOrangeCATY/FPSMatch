package com.ptcrys.fpsmatch.core.minimap.editor.vector;

import com.ptcrys.fpsmatch.core.minimap.model.CanvasPoint;
import com.ptcrys.fpsmatch.core.minimap.model.MinimapRegion;
import com.ptcrys.fpsmatch.core.minimap.model.RegionGeometry;

public final class RegionMutations {
    private RegionMutations() {
    }

    public static MinimapRegion withGeometry(MinimapRegion region, RegionGeometry geometry) {
        return new MinimapRegion(
                region.id(),
                region.floorId(),
                region.label(),
                geometry,
                region.semanticType(),
                region.tags(),
                region.gameplayReference(),
                region.styleId(),
                region.styleOverride(),
                region.labelAnchor(),
                region.priority(),
                region.minVisibleScale(),
                region.maxVisibleScale()
        );
    }

    public static MinimapRegion withVisibility(
            MinimapRegion region,
            double minVisibleScale,
            double maxVisibleScale,
            int priority,
            CanvasPoint labelAnchor
    ) {
        return new MinimapRegion(
                region.id(),
                region.floorId(),
                region.label(),
                region.geometry(),
                region.semanticType(),
                region.tags(),
                region.gameplayReference(),
                region.styleId(),
                region.styleOverride(),
                labelAnchor,
                priority,
                minVisibleScale,
                maxVisibleScale
        );
    }
}
