package com.phasetranscrystal.fpsmatch.core.map;

import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;

public interface EndTeleportMap<T extends BaseMap> extends IMap<T> {
    void setMatchEndTeleportPoint(SpawnPointData data);
}
