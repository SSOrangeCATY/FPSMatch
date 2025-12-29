package com.phasetranscrystal.fpsmatch.common.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record FPSMInventorySelectedS2CPacket(int selected) {
    public static void encode(FPSMInventorySelectedS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.selected);
    }

    public static FPSMInventorySelectedS2CPacket decode(FriendlyByteBuf buf) {
        return new FPSMInventorySelectedS2CPacket(buf.readInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            LocalPlayer localPlayer = Minecraft.getInstance().player;
            if (localPlayer != null) {
                localPlayer.getInventory().selected = selected;
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
