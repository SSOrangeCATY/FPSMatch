package com.tacz.guns.network.message.handshake;

import com.tacz.guns.GunMod;
import com.tacz.guns.network.IMessage;
import com.tacz.guns.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import com.tacz.guns.network.NetworkContext;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.util.function.Supplier;

public class Acknowledge implements IMessage<Acknowledge> {
    public static final Marker ACKNOWLEDGE = MarkerManager.getMarker("HANDSHAKE_ACKNOWLEDGE");

    @Override
    public void encode(Acknowledge message, FriendlyByteBuf buffer) {
    }

    @Override
    public Acknowledge decode(FriendlyByteBuf buf) {
        return new Acknowledge();
    }

    @Override
    public void handle(Acknowledge message, Supplier<NetworkContext> c) {
        GunMod.LOGGER.debug(ACKNOWLEDGE, "Received acknowledgement from client");
        NetworkContext context = c.get();
        if (!context.getDirection().getReceptionSide().isServer()) {
            context.disconnect(net.minecraft.network.chat.Component.literal("Connection closed - [TacZ] Acknowledgement arrived on the wrong side."));
            context.setPacketHandled(true);
            return;
        }
        ServerPlayer sender = context.getSender();
        NetworkHandler.acknowledgeSyncedEntityDataMapping(sender);
        context.setPacketHandled(true);
    }
}
