package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PullGameInfoC2SPacket() {
    public static void encode(PullGameInfoC2SPacket packet, FriendlyByteBuf buf) {
    }

    public static PullGameInfoC2SPacket decode(FriendlyByteBuf buf) {
        return new PullGameInfoC2SPacket();
    }


    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                BaseMap map = FPSMCore.getInstance().getMapByPlayer(player);
                if (map != null) {
                    map.pullGameInfo(player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}