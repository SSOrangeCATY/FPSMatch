package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.client.screen.DeathMessageHud;
import com.phasetranscrystal.fpsmatch.core.data.DeathMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record DeathMessageS2CPacket(DeathMessage deathMessage) {
    public void encode(FriendlyByteBuf packetBuffer) {
        packetBuffer.writeComponent(deathMessage.killer());
        packetBuffer.writeComponent(deathMessage.dead());
        packetBuffer.writeItem(deathMessage.weapon());
        packetBuffer.writeBoolean(deathMessage.isHeadShot());
    }

    public static DeathMessageS2CPacket decode(FriendlyByteBuf packetBuffer) {
        return new DeathMessageS2CPacket(new DeathMessage(
                packetBuffer.readComponent(),
                packetBuffer.readComponent(),
                packetBuffer.readItem(),
                packetBuffer.readBoolean()
        ));
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            DeathMessageHud.INSTANCE.addKillMessage(deathMessage);
        });
        supplier.get().setPacketHandled(true);
    }
}
