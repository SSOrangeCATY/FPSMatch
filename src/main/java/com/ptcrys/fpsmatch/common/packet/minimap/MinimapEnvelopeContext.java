package com.ptcrys.fpsmatch.common.packet.minimap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;

interface MinimapEnvelopeContext {
    MinimapEnvelopeDirection direction();

    UUID senderId();

    Object connectionToken();

    void enqueueWork(Runnable work);

    void markHandled();

    static MinimapEnvelopeContext forge(NetworkEvent.Context context) {
        Objects.requireNonNull(context, "context");
        return new MinimapEnvelopeContext() {
            @Override
            public MinimapEnvelopeDirection direction() {
                NetworkDirection direction = context.getDirection();
                if (direction == NetworkDirection.PLAY_TO_SERVER) {
                    return MinimapEnvelopeDirection.PLAY_TO_SERVER;
                }
                if (direction == NetworkDirection.PLAY_TO_CLIENT) {
                    return MinimapEnvelopeDirection.PLAY_TO_CLIENT;
                }
                return MinimapEnvelopeDirection.OTHER;
            }

            @Override
            public UUID senderId() {
                ServerPlayer sender = context.getSender();
                return sender == null ? null : sender.getUUID();
            }

            @Override
            public Object connectionToken() {
                return context.getNetworkManager();
            }

            @Override
            public void enqueueWork(Runnable work) {
                context.enqueueWork(work);
            }

            @Override
            public void markHandled() {
                context.setPacketHandled(true);
            }
        };
    }
}
