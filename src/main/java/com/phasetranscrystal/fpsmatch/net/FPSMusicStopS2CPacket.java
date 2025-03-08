package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.client.music.FPSClientMusicManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
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
        ctx.get().enqueueWork(FPSClientMusicManager::stop);
        ctx.get().setPacketHandled(true);
    }
}
