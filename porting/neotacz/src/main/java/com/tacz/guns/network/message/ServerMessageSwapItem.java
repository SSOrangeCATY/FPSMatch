package com.tacz.guns.network.message;

import com.tacz.guns.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import com.tacz.guns.network.NetworkContext;

import java.util.function.Supplier;

public class ServerMessageSwapItem {
    public ServerMessageSwapItem() {
    }

    public static void encode(ServerMessageSwapItem message, FriendlyByteBuf buf) {
    }

    public static ServerMessageSwapItem decode(FriendlyByteBuf buf) {
        return new ServerMessageSwapItem();
    }

    public static void handle(ServerMessageSwapItem message, Supplier<NetworkContext> contextSupplier) {
        NetworkHandler.handleClientboundMessage(message, contextSupplier);
    }
}
