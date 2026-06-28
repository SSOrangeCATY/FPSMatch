package com.tacz.guns.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public final class NetworkBufferUtils {
    private NetworkBufferUtils() {
    }

    public static void writeItem(FriendlyByteBuf buf, ItemStack stack) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(registryBuffer(buf), stack);
    }

    public static ItemStack readItem(FriendlyByteBuf buf) {
        return ItemStack.OPTIONAL_STREAM_CODEC.decode(registryBuffer(buf));
    }

    private static RegistryFriendlyByteBuf registryBuffer(FriendlyByteBuf buf) {
        if (buf instanceof RegistryFriendlyByteBuf registryFriendlyByteBuf) {
            return registryFriendlyByteBuf;
        }
        throw new IllegalArgumentException("TacZ ItemStack network serialization requires RegistryFriendlyByteBuf");
    }
}
