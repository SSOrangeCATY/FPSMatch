package com.tacz.guns.network.message;

import com.tacz.guns.api.entity.IGunOperator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.tacz.guns.network.NetworkContext;

import java.util.function.Supplier;

public class ClientMessagePlayerReloadGun {
    public ClientMessagePlayerReloadGun() {
    }

    public static void encode(ClientMessagePlayerReloadGun message, FriendlyByteBuf buf) {
    }

    public static ClientMessagePlayerReloadGun decode(FriendlyByteBuf buf) {
        return new ClientMessagePlayerReloadGun();
    }

    public static void handle(ClientMessagePlayerReloadGun message, Supplier<NetworkContext> contextSupplier) {
        NetworkContext context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isServer()) {
            context.enqueueWork(() -> {
                ServerPlayer entity = context.getSender();
                if (entity == null) {
                    return;
                }
                IGunOperator.fromLivingEntity(entity).reload();
            });
        }
        context.setPacketHandled(true);
    }
}
