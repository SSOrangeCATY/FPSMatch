package com.phasetranscrystal.fpsmatch.common.packet;

import com.phasetranscrystal.fpsmatch.common.client.music.FPSClientMusicManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class FPSMusicStopS2CPacket {
    public FPSMusicStopS2CPacket() {
    }
    public static void encode(FPSMusicStopS2CPacket packet, FriendlyByteBuf buf) {
    }

    public static FPSMusicStopS2CPacket decode(FriendlyByteBuf buf) {
        return new FPSMusicStopS2CPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(FPSClientMusicManager::stopMusic);
        ctx.get().setPacketHandled(true);
    }
}
