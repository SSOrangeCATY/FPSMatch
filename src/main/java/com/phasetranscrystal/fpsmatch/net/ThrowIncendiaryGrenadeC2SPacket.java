package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.item.CTIncendiaryGrenade;
import com.phasetranscrystal.fpsmatch.item.TIncendiaryGrenade;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ThrowIncendiaryGrenadeC2SPacket {
    float velocity;
    float inaccuracy;

    public ThrowIncendiaryGrenadeC2SPacket(float velocity, float inaccuracy) {
        this.velocity = velocity;
        this.inaccuracy = inaccuracy;
    }
    public static void encode(ThrowIncendiaryGrenadeC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeFloat(packet.velocity);
        buf.writeFloat(packet.inaccuracy);

    }

    public static ThrowIncendiaryGrenadeC2SPacket decode(FriendlyByteBuf buf) {
        return new ThrowIncendiaryGrenadeC2SPacket(
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
            if (player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof TIncendiaryGrenade grenade) {
                grenade.throwIncendiaryGrenade(player, player.level(), InteractionHand.MAIN_HAND, velocity, inaccuracy);
            }

            if (player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof CTIncendiaryGrenade grenade) {
                grenade.throwIncendiaryGrenade(player, player.level(), InteractionHand.MAIN_HAND, velocity, inaccuracy);
            }

        });
        ctx.get().setPacketHandled(true);
    }
}
