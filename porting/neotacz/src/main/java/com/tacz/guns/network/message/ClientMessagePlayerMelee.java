package com.tacz.guns.network.message;

import com.tacz.guns.api.entity.IGunOperator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.tacz.guns.network.NetworkContext;

import java.util.function.Supplier;

public class ClientMessagePlayerMelee {
    public static void encode(ClientMessagePlayerMelee message, FriendlyByteBuf buf) {
    }

    public static ClientMessagePlayerMelee decode(FriendlyByteBuf buf) {
        return new ClientMessagePlayerMelee();
    }

    public static void handle(ClientMessagePlayerMelee message, Supplier<NetworkContext> contextSupplier) {
        NetworkContext context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isServer()) {
            context.enqueueWork(() -> {
                ServerPlayer entity = context.getSender();
                if (entity == null) {
                    return;
                }
                IGunOperator.fromLivingEntity(entity).melee();
            });
        }
        context.setPacketHandled(true);
    }
}
