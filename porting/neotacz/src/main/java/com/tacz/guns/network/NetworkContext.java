package com.tacz.guns.network;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class NetworkContext {
    private final IPayloadContext context;

    public NetworkContext(IPayloadContext context) {
        this.context = context;
    }

    public PacketFlow getDirection() {
        return context.flow();
    }

    public CompletableFuture<Void> enqueueWork(Runnable task) {
        return context.enqueueWork(task);
    }

    public <T> CompletableFuture<T> enqueueWork(Supplier<T> task) {
        return context.enqueueWork(task);
    }

    public ServerPlayer getSender() {
        if (context.flow() != PacketFlow.SERVERBOUND) {
            Component reason = Component.literal("Connection closed - [TacZ] Serverbound sender requested from " + context.flow() + " payload.");
            context.disconnect(reason);
            throw new IllegalStateException(reason.getString());
        }
        Player player = context.player();
        if (player instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }
        Component reason = Component.literal("Connection closed - [TacZ] Serverbound payload has no server player sender.");
        context.disconnect(reason);
        throw new IllegalStateException(reason.getString());
    }

    public Connection getNetworkManager() {
        return context.connection();
    }

    public void disconnect(Component reason) {
        context.disconnect(reason);
    }

    public void setPacketHandled(boolean handled) {
    }

    public void reply(Object message) {
        context.reply(NetworkHandler.wrapReply(message, context.flow()));
    }
}
