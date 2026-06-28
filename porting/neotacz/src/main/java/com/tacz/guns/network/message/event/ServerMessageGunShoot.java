package com.tacz.guns.network.message.event;

import com.tacz.guns.network.NetworkBufferUtils;
import com.tacz.guns.network.NetworkContext;
import com.tacz.guns.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

public class ServerMessageGunShoot {
    private final int shooterId;
    private final ItemStack gunItemStack;

    public ServerMessageGunShoot(int shooterId, ItemStack gunItemStack) {
        this.shooterId = shooterId;
        this.gunItemStack = gunItemStack;
    }

    public static void encode(ServerMessageGunShoot message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.shooterId);
        NetworkBufferUtils.writeItem(buf, message.gunItemStack);
    }

    public static ServerMessageGunShoot decode(FriendlyByteBuf buf) {
        int shooterId = buf.readVarInt();
        ItemStack gunItemStack = NetworkBufferUtils.readItem(buf);
        return new ServerMessageGunShoot(shooterId, gunItemStack);
    }

    public static void handle(ServerMessageGunShoot message, Supplier<NetworkContext> contextSupplier) {
        NetworkHandler.handleClientboundMessage(message, contextSupplier);
    }

    public int getShooterId() {
        return shooterId;
    }

    public ItemStack getGunItemStack() {
        return gunItemStack;
    }
}
