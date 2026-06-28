package com.tacz.guns.network.message;

import com.tacz.guns.network.NetworkContext;
import com.tacz.guns.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

public class ServerMessageCraft {
    private final int menuId;

    public ServerMessageCraft(int menuId) {
        this.menuId = menuId;
    }

    public static void encode(ServerMessageCraft message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.menuId);
    }

    public static ServerMessageCraft decode(FriendlyByteBuf buf) {
        return new ServerMessageCraft(buf.readVarInt());
    }

    public static void handle(ServerMessageCraft message, Supplier<NetworkContext> contextSupplier) {
        NetworkHandler.handleClientboundMessage(message, contextSupplier);
    }

    public int getMenuId() {
        return menuId;
    }
}
