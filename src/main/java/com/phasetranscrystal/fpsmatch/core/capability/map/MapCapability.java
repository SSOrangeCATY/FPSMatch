package com.phasetranscrystal.fpsmatch.core.capability.map;

import com.phasetranscrystal.fpsmatch.core.capability.FPSMCapability;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;

/**
 * 地图专属能力接口
 * 持有者固定为BaseMap，继承基础能力接口
 */
public abstract class MapCapability extends FPSMCapability<BaseMap> {

    public void victory() {}

    public void tick(){}
}