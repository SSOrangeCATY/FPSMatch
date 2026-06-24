package com.phasetranscrystal.fpsmatch.common.packet;

import net.minecraft.network.FriendlyByteBuf;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;

import java.util.function.Supplier;

public record FPSMInventorySelectedS2CPacket(int selected) {
    public static void encode(FPSMInventorySelectedS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.selected);
    }

    public static FPSMInventorySelectedS2CPacket decode(FriendlyByteBuf buf) {
        return new FPSMInventorySelectedS2CPacket(buf.readInt());
    }

    public void handle(Supplier<NetworkPacketRegister.Context> ctx) {
        ClientPacketExecutor.execute(ctx, this);
    }
}
