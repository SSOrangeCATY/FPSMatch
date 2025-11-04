package com.phasetranscrystal.fpsmatch.common.packet.team;

import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.core.team.ClientTeam;
import com.phasetranscrystal.fpsmatch.core.team.ServerTeam;
import com.phasetranscrystal.fpsmatch.util.RenderUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record FPSMAddTeamS2CPacket(String gameType, String mapName, String teamName, int color) {
    public static FPSMAddTeamS2CPacket of(ServerTeam team){
        return new FPSMAddTeamS2CPacket(team.gameType, team.mapName, team.name,team.getColor());
    }

    public static void encode(FPSMAddTeamS2CPacket packet, FriendlyByteBuf packetBuffer) {
        packetBuffer.writeUtf(packet.gameType);
        packetBuffer.writeUtf(packet.mapName);
        packetBuffer.writeUtf(packet.teamName);
        packetBuffer.writeInt(packet.color);
    }

    public static FPSMAddTeamS2CPacket decode(FriendlyByteBuf packetBuffer) {
        return new FPSMAddTeamS2CPacket(
                packetBuffer.readUtf(),
                packetBuffer.readUtf(),
                packetBuffer.readUtf(),
                packetBuffer.readInt()
        );
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ClientTeam team = new ClientTeam(gameType, mapName, teamName);
            team.setColor(RenderUtil.color(color));
            FPSMClient.getGlobalData().addTeam(team);
        });
        context.setPacketHandled(true);
    }
}
