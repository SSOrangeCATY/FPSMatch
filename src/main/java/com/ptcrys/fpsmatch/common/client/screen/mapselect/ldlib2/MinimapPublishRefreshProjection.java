package com.ptcrys.fpsmatch.common.client.screen.mapselect.ldlib2;

import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.minimap.editor.MinimapPublishRefreshRegistry;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomSummary;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;

import java.util.List;
import java.util.UUID;

/** Keeps snapshot projection separate from the near-limit selection screen. */
final class MinimapPublishRefreshProjection {
    private MinimapPublishRefreshProjection() {
    }

    static void requestPendingDetails(List<MapRoomSummary> summaries) {
        for (MapKey mapKey : MinimapPublishRefreshRegistry.global().pendingMaps(summaries)) {
            FPSMatch.sendToServer(new MapRoomActionC2SPacket(
                    MapRoomActionC2SPacket.Action.REQUEST_DETAIL,
                    mapKey.gameType(), mapKey.mapName(), new UUID(0L, 0L)
            ));
        }
    }

    static boolean acceptAuthoritativeDetail(MapRoomDetail detail) {
        return MinimapPublishRefreshRegistry.global().acceptAuthoritativeDetail(detail);
    }
}
