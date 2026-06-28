package com.tacz.guns.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

public interface IMessage<T> {
    void encode(T message, FriendlyByteBuf buffer);

    T decode(FriendlyByteBuf buffer);

    void handle(T message, Supplier<NetworkContext> supplier);
}
