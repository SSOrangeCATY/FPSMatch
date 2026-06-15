package com.phasetranscrystal.fpsmatch.common.client.net;

import com.phasetranscrystal.fpsmatch.common.packet.AddAreaDataS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.AddPointDataS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.ClientPacketRegistry;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMInventorySelectedS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMSoundPlayS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMatchGameTypeS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMatchRespawnS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMatchStatsResetS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMusicPlayS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMusicStopS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.OpenMapCreatorToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.OpenSpawnPointToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.RemoveDebugDataByPrefixS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetailS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomInvitationS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomToastS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapSelectionAccessS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapSelectionSnapshotS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.shop.ShopDataSlotS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.shop.ShopMoneyS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.team.FPSMAddTeamS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.team.TeamCapabilitiesS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.team.TeamManageResultS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.team.TeamPlayerLeaveS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.team.TeamPlayerStatsS2CPacket;
import com.phasetranscrystal.fpsmatch.compat.spectate.net.SpectatorInspectPackets;
import com.phasetranscrystal.fpsmatch.compat.spectate.net.SpectatorLrtAttackPackets;

public final class FPSMClientPacketRegistrar {
    private static boolean registered;

    private FPSMClientPacketRegistrar() {
    }

    public static void registerAll() {
        if (registered) {
            return;
        }
        registered = true;

        ClientPacketRegistry.register(OpenMapCreatorToolScreenS2CPacket.class, FPSMClientPacketHandlers::handleOpenMapCreatorToolScreen);
        ClientPacketRegistry.register(OpenSpawnPointToolScreenS2CPacket.class, FPSMClientPacketHandlers::handleOpenSpawnPointToolScreen);
        ClientPacketRegistry.register(FPSMatchRespawnS2CPacket.class, FPSMClientPacketHandlers::handleRespawn);
        ClientPacketRegistry.register(FPSMInventorySelectedS2CPacket.class, FPSMClientPacketHandlers::handleInventorySelected);
        ClientPacketRegistry.register(AddAreaDataS2CPacket.class, FPSMClientPacketHandlers::handleAddAreaData);
        ClientPacketRegistry.register(AddPointDataS2CPacket.class, FPSMClientPacketHandlers::handleAddPointData);
        ClientPacketRegistry.register(FPSMatchGameTypeS2CPacket.class, FPSMClientPacketHandlers::handleGameType);
        ClientPacketRegistry.register(FPSMatchStatsResetS2CPacket.class, FPSMClientPacketHandlers::handleStatsReset);
        ClientPacketRegistry.register(FPSMSoundPlayS2CPacket.class, FPSMClientPacketHandlers::handleSoundPlay);
        ClientPacketRegistry.register(FPSMusicPlayS2CPacket.class, FPSMClientPacketHandlers::handleMusicPlay);
        ClientPacketRegistry.register(FPSMusicStopS2CPacket.class, FPSMClientPacketHandlers::handleMusicStop);
        ClientPacketRegistry.register(RemoveDebugDataByPrefixS2CPacket.class, FPSMClientPacketHandlers::handleRemoveDebugDataByPrefix);
        ClientPacketRegistry.register(ShopDataSlotS2CPacket.class, FPSMClientPacketHandlers::handleShopDataSlot);
        ClientPacketRegistry.register(ShopMoneyS2CPacket.class, FPSMClientPacketHandlers::handleShopMoney);
        ClientPacketRegistry.register(FPSMAddTeamS2CPacket.class, FPSMClientPacketHandlers::handleAddTeam);
        ClientPacketRegistry.register(TeamCapabilitiesS2CPacket.class, FPSMClientPacketHandlers::handleTeamCapabilities);
        ClientPacketRegistry.register(TeamPlayerLeaveS2CPacket.class, FPSMClientPacketHandlers::handleTeamPlayerLeave);
        ClientPacketRegistry.register(TeamPlayerStatsS2CPacket.class, FPSMClientPacketHandlers::handleTeamPlayerStats);
        ClientPacketRegistry.register(MapSelectionAccessS2CPacket.class, FPSMClientPacketHandlers::handleMapSelectionAccess);
        ClientPacketRegistry.register(MapSelectionSnapshotS2CPacket.class, FPSMClientPacketHandlers::handleMapSelectionSnapshot);
        ClientPacketRegistry.register(MapRoomDetailS2CPacket.class, FPSMClientPacketHandlers::handleMapRoomDetail);
        ClientPacketRegistry.register(MapRoomToastS2CPacket.class, FPSMClientPacketHandlers::handleMapRoomToast);
        ClientPacketRegistry.register(MapRoomInvitationS2CPacket.class, FPSMClientPacketHandlers::handleMapRoomInvitation);
        ClientPacketRegistry.register(TeamManageResultS2CPacket.class, FPSMClientPacketHandlers::handleTeamManageResult);
        ClientPacketRegistry.register(SpectatorInspectPackets.S2CWatchedPlayerInspectPacket.class, SpectatorClientPacketHandlers::handleWatchedPlayerInspect);
        ClientPacketRegistry.register(SpectatorLrtAttackPackets.S2CWatchedPlayerLrtAttackPacket.class, SpectatorClientPacketHandlers::handleWatchedPlayerLrtAttack);
    }
}
