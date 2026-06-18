package com.phasetranscrystal.fpsmatch.common.client.net;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.common.client.data.FPSMClientGlobalData;
import com.phasetranscrystal.fpsmatch.common.client.data.RenderableArea;
import com.phasetranscrystal.fpsmatch.common.client.data.RenderablePoint;
import com.phasetranscrystal.fpsmatch.common.client.screen.MapCreatorToolScreen;
import com.phasetranscrystal.fpsmatch.common.client.screen.SpawnPointToolScreen;
import com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.FPSMMapSelectScreens;
import com.phasetranscrystal.fpsmatch.common.packet.AddAreaDataS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.AddPointDataS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMInventorySelectedS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMSoundPlayS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMatchGameTypeS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMusicPlayS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMusicStopS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMatchRespawnS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMatchStatsResetS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.OpenMapCreatorToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.OpenSpawnPointToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.RemoveDebugDataByPrefixS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetailS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomInvitationS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomReadyStateS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomToastS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapSelectionAccessS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapSelectionSnapshotS2CPacket;
import com.phasetranscrystal.fpsmatch.common.client.music.FPSClientMusicManager;
import com.phasetranscrystal.fpsmatch.common.packet.shop.ShopDataSlotS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.shop.ShopMoneyS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.team.FPSMAddTeamS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.team.TeamCapabilitiesS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.team.TeamManageResultS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.team.TeamPlayerLeaveS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.team.TeamPlayerStatsS2CPacket;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.team.ClientTeam;
import com.phasetranscrystal.fpsmatch.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.Optional;

public final class FPSMClientPacketHandlers {
    private FPSMClientPacketHandlers() {
    }

    public static void handleOpenMapCreatorToolScreen(OpenMapCreatorToolScreenS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MapCreatorToolScreen screen) {
            screen.applyData(packet);
        } else {
            minecraft.setScreen(new MapCreatorToolScreen(packet));
        }
    }

    public static void handleOpenSpawnPointToolScreen(OpenSpawnPointToolScreenS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SpawnPointToolScreen screen) {
            screen.applyData(packet);
        } else {
            minecraft.setScreen(new SpawnPointToolScreen(packet));
        }
    }

    public static void handleRespawn(FPSMatchRespawnS2CPacket packet) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().setScreen(null);
            Minecraft.getInstance().player.respawn();
        }
    }

    public static void handleInventorySelected(FPSMInventorySelectedS2CPacket packet) {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            localPlayer.getInventory().selected = packet.selected();
        }
    }

    public static void handleAddAreaData(AddAreaDataS2CPacket packet) {
        FPSMClient.getGlobalData().getDebugData().upsertRenderableArea(packet.key(), new RenderableArea(packet.key(), packet.name(), packet.color(), packet.areaData()));
    }

    public static void handleAddPointData(AddPointDataS2CPacket packet) {
        FPSMClient.getGlobalData().getDebugData().upsertRenderablePoint(packet.key(), new RenderablePoint(packet.key(), packet.name(), packet.color(), packet.position()));
    }

    public static void handleGameType(FPSMatchGameTypeS2CPacket packet) {
        FPSMClient.getGlobalData().setCurrentGameType(packet.getGameType());
        FPSMClient.getGlobalData().setCurrentMap(packet.getMapName());
    }

    public static void handleStatsReset(FPSMatchStatsResetS2CPacket packet) {
        FPSMClient.reset();
    }

    public static void handleSoundPlay(FPSMSoundPlayS2CPacket packet) {
        FPSClientMusicManager.playSound(packet.getLocation());
    }

    public static void handleMusicPlay(FPSMusicPlayS2CPacket packet) {
        FPSClientMusicManager.stopMusic();
        FPSClientMusicManager.playMusic(packet.getLocation());
    }

    public static void handleMusicStop(FPSMusicStopS2CPacket packet) {
        FPSClientMusicManager.stopMusic();
    }

    public static void handleRemoveDebugDataByPrefix(RemoveDebugDataByPrefixS2CPacket packet) {
        FPSMClient.getGlobalData().getDebugData().removeByPrefix(packet.prefix());
    }

    public static void handleShopDataSlot(ShopDataSlotS2CPacket packet) {
        var currentSlot = FPSMClient.getGlobalData().getSlotData(packet.type.name(), packet.index);
        if (currentSlot != null) {
            currentSlot.setItemStack(packet.itemStack);
            currentSlot.setCost(packet.cost);
            currentSlot.setBoughtCount(packet.boughtCount);
            currentSlot.setLock(packet.locked);
        } else {
            FPSMatch.LOGGER.error("Failed to update slot data for {} at index {}", packet.type.name(), packet.index);
        }
    }

    public static void handleShopMoney(ShopMoneyS2CPacket packet) {
        if (Minecraft.getInstance().player != null) {
            FPSMClient.getGlobalData().setPlayerMoney(packet.owner(), packet.money());
        }
    }

    public static void handleAddTeam(FPSMAddTeamS2CPacket packet) {
        FPSMClientGlobalData data = FPSMClient.getGlobalData();
        if (data.getTeamByName(packet.teamData().name()).isPresent()) return;
        ClientTeam team = new ClientTeam(packet.gameType(), packet.mapName(), packet.teamData());
        team.setColor(RenderUtil.color(packet.color()));
        data.addTeam(team);
    }

    public static void handleTeamCapabilities(TeamCapabilitiesS2CPacket packet) {
        FPSMClient.getGlobalData().getTeamByName(packet.teamName()).ifPresent(team -> {
            team.getCapabilityMap().deserializeCapability(packet.capName(), packet.capabilityData());
        });
        packet.capabilityData().release();
    }

    public static void handleTeamPlayerLeave(TeamPlayerLeaveS2CPacket packet) {
        FPSMClient.getGlobalData().removePlayer(packet.player());
    }

    public static void handleTeamPlayerStats(TeamPlayerStatsS2CPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        FPSMClientGlobalData global = FPSMClient.getGlobalData();
        if (packet.getUuid().equals(mc.player.getUUID()) && !FPSMClient.getGlobalData().isCurrentTeam(packet.getTeamName())) {
            global.setCurrentTeam(packet.getTeamName());
        }

        Optional<PlayerData> opt = FPSMClient.getGlobalData().getPlayerData(packet.getTeamName(), packet.getUuid());
        PlayerData data = opt.orElse(new PlayerData(packet.getUuid(), packet.getPlayerName(), false));
        data.setScores(packet.getScores());
        data.setKills(packet.getKills());
        data.setDeaths(packet.getDeaths());
        data.setAssists(packet.getAssists());
        data.setDamage(packet.getDamage());
        data.setMvpCount(packet.getMvpCount());
        data.setLiving(packet.isLiving());
        data.setHeadshotKills(packet.getHeadshotKills());
        data.setHealthPercent(packet.getHealthPercent());
        FPSMClient.getGlobalData().updatePlayerTeamData(packet.getTeamName(), packet.getUuid(), data);
    }

    public static void handleMapSelectionSnapshot(MapSelectionSnapshotS2CPacket packet) {
        FPSMClient.getGlobalData().setMapSelectionSnapshot(packet);
        FPSMClient.getGlobalData().setMapSelectionButtonVisible(packet.viewerOp() || packet.nonOpButtonEnabled());
        FPSMMapSelectScreens.openSelection(packet);
    }

    public static void handleMapSelectionAccess(MapSelectionAccessS2CPacket packet) {
        FPSMClient.getGlobalData().setMapSelectionButtonVisible(packet.visible());
    }

    public static void handleMapRoomDetail(MapRoomDetailS2CPacket packet) {
        FPSMClient.getGlobalData().setMapRoomDetail(packet.detail());
        FPSMMapSelectScreens.openDetail(packet);
    }

    public static void handleMapRoomReadyState(MapRoomReadyStateS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof com.phasetranscrystal.fpsmatch.common.client.screen.FPSMTeamManageScreen screen) {
            screen.applyReadyState(packet.gameType(), packet.mapName(), packet.countdownSeconds(), packet.readyPlayers());
        }
    }

    public static void handleMapRoomToast(MapRoomToastS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        FPSMClient.getGlobalData().setMapRoomToast(packet);
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(packet.message(), packet.error());
        }
    }

    public static void handleMapRoomInvitation(MapRoomInvitationS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        FPSMClient.getGlobalData().setMapRoomInvitation(packet.gameType(), packet.mapName(), packet.message());
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(packet.message(), false);
        }
        FPSMMapSelectScreens.openInvitation(packet);
    }

    public static void handleTeamManageResult(TeamManageResultS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(packet.message(), false);
        }
    }
}
