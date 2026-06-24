package com.phasetranscrystal.fpsmatch.common.packet.team;

import com.phasetranscrystal.fpsmatch.common.packet.ClientPacketExecutor;
import net.minecraft.network.FriendlyByteBuf;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;

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

    public void handle(Supplier<NetworkPacketRegister.Context> supplier) {
        ClientPacketExecutor.execute(supplier, this);
    }
}
