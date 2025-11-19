package com.phasetranscrystal.fpsmatch.common.packet.team;

import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.core.capability.FPSMCapability;
import com.phasetranscrystal.fpsmatch.core.team.ServerTeam;
import com.phasetranscrystal.fpsmatch.core.capability.team.TeamCapability;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record TeamCapabilitySyncS2CPacket(
    String teamName,
    FriendlyByteBuf capabilityData
) {

    public static <T extends TeamCapability & FPSMCapability.Synchronizable> TeamCapabilitySyncS2CPacket of(ServerTeam team, Class<T> clazz) {
        FriendlyByteBuf dataBuf = new FriendlyByteBuf(Unpooled.buffer());
        dataBuf.writeUtf(clazz.getSimpleName());
        team.getCapabilityMap().serializeCapability(clazz,dataBuf);

        return new TeamCapabilitySyncS2CPacket(
                team.name,
                dataBuf
        );
    }

    public static <T extends TeamCapability & FPSMCapability.Synchronizable> List<TeamCapabilitySyncS2CPacket> toList(ServerTeam team,List<Class<T>> classes){
        List<TeamCapabilitySyncS2CPacket> packets = new ArrayList<>();
        for (Class<T> clazz : classes) {
            packets.add(of(team,clazz));
        }
        return packets;
    }


    public static void encode(TeamCapabilitySyncS2CPacket packet, FriendlyByteBuf packetBuffer) {
        packetBuffer.writeUtf(packet.teamName);

        packetBuffer.writeInt(packet.capabilityData.readableBytes());
        packetBuffer.writeBytes(packet.capabilityData);
    }

    public static TeamCapabilitySyncS2CPacket decode(FriendlyByteBuf packetBuffer) {
        String teamName = packetBuffer.readUtf();

        int dataLength = packetBuffer.readInt();
        FriendlyByteBuf capabilityData = new FriendlyByteBuf(packetBuffer.readBytes(dataLength));
        
        return new TeamCapabilitySyncS2CPacket(
            teamName, 
            capabilityData
        );
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            FPSMClient.getGlobalData().getTeamByName(teamName).ifPresent(team -> {
                team.getCapabilityMap().deserializeCapability(capabilityData);
            });
            capabilityData.release();
        });
        context.setPacketHandled(true);
    }
}