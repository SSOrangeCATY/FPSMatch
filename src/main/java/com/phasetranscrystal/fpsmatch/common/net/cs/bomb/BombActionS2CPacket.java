package com.phasetranscrystal.fpsmatch.common.net.cs.bomb;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.key.DismantleBombKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record BombActionS2CPacket() {
    public static void encode(BombActionS2CPacket packet, FriendlyByteBuf buf) {
    }

    public static BombActionS2CPacket decode(FriendlyByteBuf buf) {
        return new BombActionS2CPacket();
    }


    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            FPSMatch.INSTANCE.sendToServer(new BombActionC2SPacket(DismantleBombKey.DISMANTLE_BOMB_KEY.isDown()));
        });
        ctx.get().setPacketHandled(true);
    }
}
