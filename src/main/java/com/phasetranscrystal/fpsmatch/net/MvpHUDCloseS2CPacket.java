package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.client.data.ClientData;
import com.phasetranscrystal.fpsmatch.client.screen.MVPHud;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MvpHUDCloseS2CPacket {
    public MvpHUDCloseS2CPacket() {
    }
    public static void encode(MvpHUDCloseS2CPacket packet, FriendlyByteBuf buf) {
    }

    public static MvpHUDCloseS2CPacket decode(FriendlyByteBuf buf) {
        return new MvpHUDCloseS2CPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(MVPHud.INSTANCE::triggerCloseAnimation);
        ctx.get().setPacketHandled(true);
    }
}
