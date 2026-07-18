package com.phasetranscrystal.fpsmatch.core.minimap.region;

import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;

import java.util.List;

@FunctionalInterface
public interface MinimapRegionProvider {
    List<RuntimeRegionDescriptor> collect(MapKey mapKey, String defaultFloorId);
}