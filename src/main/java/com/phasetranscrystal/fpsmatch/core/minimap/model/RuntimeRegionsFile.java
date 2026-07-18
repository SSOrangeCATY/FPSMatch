package com.phasetranscrystal.fpsmatch.core.minimap.model;

import java.util.List;

public record RuntimeRegionsFile(List<RuntimeRegion> regions) {
    public RuntimeRegionsFile {
        regions = List.copyOf(regions);
    }
}
