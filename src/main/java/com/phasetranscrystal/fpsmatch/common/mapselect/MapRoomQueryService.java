package com.phasetranscrystal.fpsmatch.common.mapselect;

import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomPlayerInfo;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomSettingInfo;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomSummary;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.data.Setting;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.team.ServerTeam;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class MapRoomQueryService {
    private MapRoomQueryService() {
    }

    public static Optional<BaseMap> findMap(String gameType, String mapName) {
        if (!FPSMCore.initialized()) {
            return Optional.empty();
        }
        return FPSMCore.getInstance().getMapByTypeWithName(gameType, mapName);
    }

    public static List<MapRoomSummary> summaries(ServerPlayer viewer) {
        List<MapRoomSummary> summaries = new ArrayList<>();
        if (!FPSMCore.initialized()) {
            return summaries;
        }
        FPSMCore.getInstance().getAllMaps().forEach((type, maps) -> maps.forEach(map -> summaries.add(summary(viewer, map))));
        summaries.sort(Comparator.comparing(MapRoomSummary::gameType).thenComparing(MapRoomSummary::mapName));
        return summaries;
    }

    public static MapRoomDetail detail(ServerPlayer viewer, BaseMap map) {
        return new MapRoomDetail(
                summary(viewer, map),
                players(map),
                settings(viewer, map),
                availableInviteTargets(viewer, map),
                "gui.fpsm.map_select.rules." + map.getGameType(),
                "",
                ""
        );
    }

    public static MapRoomSummary summary(ServerPlayer viewer, BaseMap map) {
        int joined = map.getMapTeams().getJoinedUUID().size();
        List<ServerTeam> normalTeams = map.getMapTeams().getNormalTeams();
        boolean unlimited = normalTeams.stream().anyMatch(team -> team.getPlayerLimit() < 0);
        int max = unlimited ? -1 : normalTeams.stream()
                .mapToInt(ServerTeam::getPlayerLimit)
                .filter(limit -> limit > 0)
                .sum();
        boolean joinedMap = map.checkGameHasPlayer(viewer);
        boolean spectating = map.checkSpecHasPlayer(viewer);
        return new MapRoomSummary(
                map.getGameType(),
                map.getMapName(),
                map.getServerLevel().dimension().location().toString(),
                areaText(map.getMapArea().pos1(), map.getMapArea().pos2()),
                map.isStart(),
                map.isDebug(),
                map.allowJoinInProgress(),
                joined,
                max,
                joinedMap,
                spectating,
                viewer.hasPermissions(2)
        );
    }

    public static List<MapRoomPlayerInfo> players(BaseMap map) {
        List<MapRoomPlayerInfo> players = new ArrayList<>();
        map.getMapTeams().getTeamsWithSpectator().forEach(team -> team.getPlayersData().forEach(data -> players.add(playerInfo(team, data))));
        players.sort(Comparator.comparing(MapRoomPlayerInfo::teamName).thenComparing(MapRoomPlayerInfo::name));
        return players;
    }

    public static List<MapRoomPlayerInfo> availableInviteTargets(ServerPlayer viewer, BaseMap map) {
        List<MapRoomPlayerInfo> targets = new ArrayList<>();
        if (!map.checkGameHasPlayer(viewer) && !map.checkSpecHasPlayer(viewer)) {
            return targets;
        }
        if (!canInviteInto(map)) {
            return targets;
        }
        FPSMCore.getInstance().getServer().getPlayerList().getPlayers().stream()
                .filter(player -> !player.getUUID().equals(viewer.getUUID()))
                .filter(MapRoomQueryService::isAvailableInviteTarget)
                .map(player -> new MapRoomPlayerInfo(player.getUUID(), player.getGameProfile().getName(), "", false, true))
                .sorted(Comparator.comparing(MapRoomPlayerInfo::name))
                .forEach(targets::add);
        return targets;
    }

    public static boolean canInviteInto(BaseMap map) {
        return !map.isStart() || map.allowJoinInProgress();
    }

    private static boolean isAvailableInviteTarget(ServerPlayer player) {
        return FPSMCore.getInstance().getMapByPlayerWithSpec(player).isEmpty();
    }

    public static List<MapRoomSettingInfo> settings(ServerPlayer viewer, BaseMap map) {
        boolean editable = viewer.hasPermissions(2);
        return map.settings().stream()
                .map(setting -> settingInfo(setting, editable))
                .sorted(Comparator.comparing(MapRoomSettingInfo::name))
                .toList();
    }

    private static MapRoomPlayerInfo playerInfo(ServerTeam team, PlayerData data) {
        return new MapRoomPlayerInfo(
                data.getOwner(),
                data.getPlayer().map(player -> player.getGameProfile().getName()).orElse(data.getOwner().toString()),
                team.getName(),
                team.isSpectator(),
                data.getPlayer().isPresent()
        );
    }

    private static MapRoomSettingInfo settingInfo(Setting<?> setting, boolean editable) {
        return new MapRoomSettingInfo(setting.getConfigName(), setting.toString(), String.valueOf(setting.getDefaultValue()), editable);
    }

    private static String areaText(BlockPos from, BlockPos to) {
        return from.getX() + " " + from.getY() + " " + from.getZ() + " -> " + to.getX() + " " + to.getY() + " " + to.getZ();
    }
}
