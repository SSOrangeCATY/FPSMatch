package com.tacz.guns.network.message;

import com.tacz.guns.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import com.tacz.guns.network.NetworkContext;

import java.util.function.Supplier;

public class ServerMessageSyncBaseTimestamp {
    private long clientReceiveTimestamp;

    public ServerMessageSyncBaseTimestamp() { }

    public static void encode(ServerMessageSyncBaseTimestamp message, FriendlyByteBuf buf) { }

    public static ServerMessageSyncBaseTimestamp decode(FriendlyByteBuf buf) {
        return new ServerMessageSyncBaseTimestamp();
    }

    public static void handle(ServerMessageSyncBaseTimestamp message, Supplier<NetworkContext> contextSupplier) {
        NetworkContext context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            message.clientReceiveTimestamp = System.currentTimeMillis();
            NetworkHandler.enqueueClientboundMessage(context, message);
            context.setPacketHandled(true);
            NetworkHandler.CHANNEL.reply(new ClientMessageSyncBaseTimestamp(), context);
            return;
        }
        context.setPacketHandled(true);
    }

    public long getClientReceiveTimestamp() {
        return clientReceiveTimestamp;
    }
}
