package com.tacz.guns.network.message;

import com.tacz.guns.network.NetworkContext;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.NetworkBufferUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

public class ServerMessageLevelUp {
    private final ItemStack gun;
    private final int level;

    public ServerMessageLevelUp(ItemStack gun, int level) {
        this.gun = gun;
        this.level = level;
    }

    public static void encode(ServerMessageLevelUp message, FriendlyByteBuf buf) {
        NetworkBufferUtils.writeItem(buf, message.gun);
        buf.writeInt(message.level);
    }

    public static ServerMessageLevelUp decode(FriendlyByteBuf buf) {
        ItemStack gun = NetworkBufferUtils.readItem(buf);
        int level = buf.readInt();
        return new ServerMessageLevelUp(gun, level);
    }

    public static void handle(ServerMessageLevelUp message, Supplier<NetworkContext> contextSupplier) {
        NetworkHandler.handleClientboundMessage(message, contextSupplier);
    }

    public ItemStack getGun() {
        return this.gun;
    }

    public int getLevel() {
        return this.level;
    }
}
