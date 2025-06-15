package com.phasetranscrystal.fpsmatch.common.net;

import com.phasetranscrystal.fpsmatch.common.client.music.FPSClientMusicManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class FPSMusicPlayS2CPacket {
    ResourceLocation location;
    public FPSMusicPlayS2CPacket(ResourceLocation location) {
        this.location = location;
    }
    public static void encode(FPSMusicPlayS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeResourceLocation(packet.location);
    }

    public static FPSMusicPlayS2CPacket decode(FriendlyByteBuf buf) {
        return new FPSMusicPlayS2CPacket(buf.readResourceLocation());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(()-> {
            FPSClientMusicManager.stop();
            FPSClientMusicManager.play(location);
        });
        ctx.get().setPacketHandled(true);
    }
}
