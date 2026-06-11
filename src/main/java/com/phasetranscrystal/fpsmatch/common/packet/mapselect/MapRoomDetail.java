package com.phasetranscrystal.fpsmatch.common.packet.mapselect;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record MapRoomDetail(
        MapRoomSummary summary,
        List<MapRoomPlayerInfo> players,
        List<MapRoomSettingInfo> settings,
        List<MapRoomPlayerInfo> availableInviteTargets,
        List<EditableShopInfo> editableShops,
        String rulesKey,
        String iconTexture,
        String backgroundTexture
) {
    private static final int RESOURCE_MAX_LENGTH = 256;

    public static void encode(MapRoomDetail detail, FriendlyByteBuf buf) {
        MapRoomSummary.encode(detail.summary(), buf);
        buf.writeCollection(detail.players(), (buffer, info) -> MapRoomPlayerInfo.encode(info, buffer));
        buf.writeCollection(detail.settings(), (buffer, info) -> MapRoomSettingInfo.encode(info, buffer));
        buf.writeCollection(detail.availableInviteTargets(), (buffer, info) -> MapRoomPlayerInfo.encode(info, buffer));
        buf.writeCollection(detail.editableShops(), (buffer, info) -> info.encode(buffer));
        buf.writeUtf(detail.rulesKey(), RESOURCE_MAX_LENGTH);
        buf.writeUtf(detail.iconTexture(), RESOURCE_MAX_LENGTH);
        buf.writeUtf(detail.backgroundTexture(), RESOURCE_MAX_LENGTH);
    }

    public static MapRoomDetail decode(FriendlyByteBuf buf) {
        MapRoomSummary summary = MapRoomSummary.decode(buf);
        List<MapRoomPlayerInfo> players = buf.readCollection(ArrayList::new, MapRoomPlayerInfo::decode);
        List<MapRoomSettingInfo> settings = buf.readCollection(ArrayList::new, MapRoomSettingInfo::decode);
        List<MapRoomPlayerInfo> availableInviteTargets = buf.readCollection(ArrayList::new, MapRoomPlayerInfo::decode);
        List<EditableShopInfo> editableShops = buf.readCollection(ArrayList::new, EditableShopInfo::decode);
        return new MapRoomDetail(summary, players, settings, availableInviteTargets, editableShops, buf.readUtf(RESOURCE_MAX_LENGTH), buf.readUtf(RESOURCE_MAX_LENGTH), buf.readUtf(RESOURCE_MAX_LENGTH));
    }
}
