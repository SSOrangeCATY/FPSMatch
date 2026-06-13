package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail;

/**
 * 子屏幕数据更新接口
 */
public interface FPSMMapDetailChildScreen {
    void applyDetail(MapRoomDetail detail);
}