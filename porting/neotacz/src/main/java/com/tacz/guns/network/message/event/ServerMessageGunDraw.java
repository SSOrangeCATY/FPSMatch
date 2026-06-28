package com.tacz.guns.network.message.event;

import com.tacz.guns.network.NetworkBufferUtils;
import com.tacz.guns.network.NetworkContext;
import com.tacz.guns.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

public class ServerMessageGunDraw {
    private final int entityId;
    private final ItemStack previousGunItem;
    private final ItemStack currentGunItem;

    public ServerMessageGunDraw(int entityId, ItemStack previousGunItem, ItemStack currentGunItem) {
        this.entityId = entityId;
        this.previousGunItem = previousGunItem;
        this.currentGunItem = currentGunItem;
    }

    public static void encode(ServerMessageGunDraw message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.entityId);
        NetworkBufferUtils.writeItem(buf, message.previousGunItem);
        NetworkBufferUtils.writeItem(buf, message.currentGunItem);
    }

    public static ServerMessageGunDraw decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        ItemStack previousGunItem = NetworkBufferUtils.readItem(buf);
        ItemStack currentGunItem = NetworkBufferUtils.readItem(buf);
        return new ServerMessageGunDraw(entityId, previousGunItem, currentGunItem);
    }

    public static void handle(ServerMessageGunDraw message, Supplier<NetworkContext> contextSupplier) {
        NetworkHandler.handleClientboundMessage(message, contextSupplier);
    }

    public int getEntityId() {
        return entityId;
    }

    public ItemStack getPreviousGunItem() {
        return previousGunItem;
    }

    public ItemStack getCurrentGunItem() {
        return currentGunItem;
    }
}
