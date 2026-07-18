package com.phasetranscrystal.fpsmatch.core.minimap.model;

import java.util.List;

public record RegionsFile(List<MinimapRegion> regions) {
    public RegionsFile {
        regions = List.copyOf(regions);
    }
}
