package com.tacz.guns.network.message;

import com.tacz.guns.inventory.GunSmithTableMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import com.tacz.guns.network.NetworkContext;

import java.util.function.Supplier;

public class ClientMessageCraft {
    private final Identifier recipeId;
    private final int menuId;

    public ClientMessageCraft(Identifier recipeId, int menuId) {
        this.recipeId = recipeId;
        this.menuId = menuId;
    }

    public static void encode(ClientMessageCraft message, FriendlyByteBuf buf) {
        buf.writeIdentifier(message.recipeId);
        buf.writeVarInt(message.menuId);
    }

    public static ClientMessageCraft decode(FriendlyByteBuf buf) {
        return new ClientMessageCraft(buf.readIdentifier(), buf.readVarInt());
    }

    public static void handle(ClientMessageCraft message, Supplier<NetworkContext> contextSupplier) {
        NetworkContext context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isServer()) {
            context.enqueueWork(() -> {
                ServerPlayer entity = context.getSender();
                if (entity == null) {
                    return;
                }
                if (entity.containerMenu.containerId == message.menuId && entity.containerMenu instanceof GunSmithTableMenu menu) {
                    menu.doCraft(message.recipeId, entity);
                }
            });
        }
        context.setPacketHandled(true);
    }
}
