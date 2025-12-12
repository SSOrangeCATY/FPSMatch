package com.phasetranscrystal.fpsmatch.common.packet.tacz;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class StartInspectC2SPacket {
    public static StartInspectC2SPacket decode(FriendlyByteBuf b) {
        return new StartInspectC2SPacket();
    }

    public static void encode(StartInspectC2SPacket p, FriendlyByteBuf b) {
    }

    public void handleStartInspectPacket(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) return;
            WatchedPlayerInspectS2CPacket pkt = new WatchedPlayerInspectS2CPacket(sp.getUUID());
            sp.server.getPlayerList().getPlayers().forEach(
                    pl -> FPSMatch.INSTANCE.sendTo(pkt, pl.connection.connection, NetworkDirection.PLAY_TO_CLIENT));
        });
        ctx.get().setPacketHandled(true);
    }
}