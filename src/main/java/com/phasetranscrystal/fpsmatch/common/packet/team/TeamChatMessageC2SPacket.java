package com.phasetranscrystal.fpsmatch.common.packet.team;

import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerPlayer;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;

import java.util.function.Supplier;

public record TeamChatMessageC2SPacket(Component message) {
    public static void encode(TeamChatMessageC2SPacket packet, RegistryFriendlyByteBuf packetBuffer) {
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(packetBuffer, packet.message);
    }

    public static TeamChatMessageC2SPacket decode(RegistryFriendlyByteBuf packetBuffer) {
        return new TeamChatMessageC2SPacket(
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(packetBuffer)
        );
    }

    public void handle(Supplier<NetworkPacketRegister.Context> supplier) {
        NetworkPacketRegister.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            FPSMCore.getInstance().getMapByPlayer(player)
                    .flatMap(map -> map.getMapTeams().getTeamByPlayer(player))
                    .ifPresent(team -> team.sendMessage(message));
        });
        context.setPacketHandled(true);
    }
}
