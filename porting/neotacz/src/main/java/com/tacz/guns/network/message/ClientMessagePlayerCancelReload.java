package com.tacz.guns.network.message;

import com.tacz.guns.api.entity.IGunOperator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.tacz.guns.network.NetworkContext;

import java.util.function.Supplier;

public class ClientMessagePlayerCancelReload {
    public ClientMessagePlayerCancelReload() {
    }

    public static void encode(ClientMessagePlayerCancelReload message, FriendlyByteBuf buf) {
    }

    public static ClientMessagePlayerCancelReload decode(FriendlyByteBuf buf) {
        return new ClientMessagePlayerCancelReload();
    }

    public static void handle(ClientMessagePlayerCancelReload message, Supplier<NetworkContext> contextSupplier) {
        NetworkContext context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isServer()) {
            context.enqueueWork(() -> {
                ServerPlayer entity = context.getSender();
                if (entity == null) {
                    return;
                }
                IGunOperator.fromLivingEntity(entity).cancelReload();
            });
        }
        context.setPacketHandled(true);
    }
}
