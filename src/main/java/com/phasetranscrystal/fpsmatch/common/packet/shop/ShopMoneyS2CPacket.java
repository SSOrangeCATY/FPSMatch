package com.phasetranscrystal.fpsmatch.common.packet.shop;

import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record ShopMoneyS2CPacket(UUID owner, int money) {
    public static void encode(ShopMoneyS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.owner);
        buf.writeInt(packet.money);
    }

    public static ShopMoneyS2CPacket decode(FriendlyByteBuf buf) {
        return new ShopMoneyS2CPacket(
                buf.readUUID(), buf.readInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().player != null) {
                FPSMClient.getGlobalData().setPlayersMoney(this.owner, money);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
