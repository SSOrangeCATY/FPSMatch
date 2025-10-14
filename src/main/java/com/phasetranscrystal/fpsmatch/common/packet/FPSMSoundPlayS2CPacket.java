package com.phasetranscrystal.fpsmatch.common.packet;

import com.phasetranscrystal.fpsmatch.common.client.music.FPSClientMusicManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class FPSMSoundPlayS2CPacket {
    ResourceLocation location;
    public FPSMSoundPlayS2CPacket(ResourceLocation location) {
        this.location = location;
    }
    public static void encode(FPSMSoundPlayS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeResourceLocation(packet.location);
    }

    public static FPSMSoundPlayS2CPacket decode(FriendlyByteBuf buf) {
        return new FPSMSoundPlayS2CPacket(buf.readResourceLocation());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(()-> {
            FPSClientMusicManager.playSound(location);
        });
        ctx.get().setPacketHandled(true);
    }
}
