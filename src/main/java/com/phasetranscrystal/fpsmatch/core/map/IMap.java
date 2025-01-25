package com.phasetranscrystal.fpsmatch.core.map;

import com.mojang.serialization.Codec;
import com.phasetranscrystal.fpsmatch.core.BaseMap;
import com.phasetranscrystal.fpsmatch.core.data.save.ISavedData;

public interface IMap<T extends BaseMap> {
    T getMap();
}
