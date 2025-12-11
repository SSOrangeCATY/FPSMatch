package com.phasetranscrystal.fpsmatch.common.packet.team;

import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record TeamPlayerLeaveS2CPacket(UUID player) {
    public static void encode(TeamPlayerLeaveS2CPacket packet, FriendlyByteBuf packetBuffer) {
        packetBuffer.writeUUID(packet.player);
    }

    public static TeamPlayerLeaveS2CPacket decode(FriendlyByteBuf packetBuffer) {
        return new TeamPlayerLeaveS2CPacket(
                packetBuffer.readUUID()
        );
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            FPSMClient.getGlobalData().removePlayer(player);
        });
        context.setPacketHandled(true);
    }
}
