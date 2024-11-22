package com.phasetranscrystal.fpsmatch.core.map;

import com.phasetranscrystal.fpsmatch.core.BaseMap;
import org.jetbrains.annotations.NotNull;

public interface IMap<T extends BaseMap> {
    @NotNull T getMap();
}
