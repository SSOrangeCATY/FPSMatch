package com.phasetranscrystal.fpsmatch.common.packet.team;

import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.common.client.data.FPSMClientGlobalData;
import com.phasetranscrystal.fpsmatch.core.team.ClientTeam;
import com.phasetranscrystal.fpsmatch.core.team.ServerTeam;
import com.phasetranscrystal.fpsmatch.core.team.TeamData;
import com.phasetranscrystal.fpsmatch.util.RenderUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record FPSMAddTeamS2CPacket(String gameType, String mapName, int color, TeamData teamData) {
    public static FPSMAddTeamS2CPacket of(ServerTeam team){
        return new FPSMAddTeamS2CPacket(team.gameType, team.mapName,team.getColor(), TeamData.of(team));
    }

    public static void encode(FPSMAddTeamS2CPacket packet, FriendlyByteBuf packetBuffer) {
        packetBuffer.writeUtf(packet.gameType);
        packetBuffer.writeUtf(packet.mapName);
        packetBuffer.writeInt(packet.color);
        packetBuffer.writeJsonWithCodec(TeamData.CODEC, packet.teamData);
    }

    public static FPSMAddTeamS2CPacket decode(FriendlyByteBuf packetBuffer) {
        return new FPSMAddTeamS2CPacket(
                packetBuffer.readUtf(),
                packetBuffer.readUtf(),
                packetBuffer.readInt(),
                packetBuffer.readJsonWithCodec(TeamData.CODEC)
        );
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            FPSMClientGlobalData data = FPSMClient.getGlobalData();
            if(data.getTeamByName(teamData.name()).isPresent()) return;
            ClientTeam team = new ClientTeam(gameType, mapName, teamData);
            team.setColor(RenderUtil.color(color));
            data.addTeam(team);
        });
        context.setPacketHandled(true);
    }
}
