package com.tacz.guns.network.message;

import com.tacz.guns.network.NetworkContext;
import com.tacz.guns.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

public class ServerMessageRefreshRefitScreen {
    public static void encode(ServerMessageRefreshRefitScreen message, FriendlyByteBuf buf) {
    }

    public static ServerMessageRefreshRefitScreen decode(FriendlyByteBuf buf) {
        return new ServerMessageRefreshRefitScreen();
    }

    public static void handle(ServerMessageRefreshRefitScreen message, Supplier<NetworkContext> contextSupplier) {
        NetworkHandler.handleClientboundMessage(message, contextSupplier);
    }
}
