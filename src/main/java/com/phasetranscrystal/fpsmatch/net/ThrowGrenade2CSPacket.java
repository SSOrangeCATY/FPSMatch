package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.item.Grenade;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ThrowGrenade2CSPacket {
    float velocity;
    float inaccuracy;

    public ThrowGrenade2CSPacket(float velocity, float inaccuracy) {
        this.velocity = velocity;
        this.inaccuracy = inaccuracy;
    }
    public static void encode(ThrowGrenade2CSPacket packet, FriendlyByteBuf buf) {
        buf.writeFloat(packet.velocity);
        buf.writeFloat(packet.inaccuracy);

    }

    public static ThrowGrenade2CSPacket decode(FriendlyByteBuf buf) {
        return new ThrowGrenade2CSPacket(
                buf.readFloat(),
                buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if(player == null) {
                ctx.get().setPacketHandled(true);
                return;
            }
            if (player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof Grenade grenade) {
                grenade.throwGrenade(player, player.level(), InteractionHand.MAIN_HAND, 1.5F, 1.0F);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
