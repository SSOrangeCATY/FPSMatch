package com.phasetranscrystal.fpsmatch.common.net.register;

import com.phasetranscrystal.fpsmatch.common.net.*;
import com.phasetranscrystal.fpsmatch.common.net.cs.shop.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;


public class NetworkPacketRegister {
    private final AtomicInteger idCounter = new AtomicInteger(0);
    private final SimpleChannel channel;

    public NetworkPacketRegister(ResourceLocation channel,String version) {
        this.channel = NetworkRegistry.newSimpleChannel(
                channel,
                () -> version,
                version::equals,
                version::equals
        );
    }

    public NetworkPacketRegister(ResourceLocation channel, Supplier<String> networkProtocolVersion, Predicate<String> clientAcceptedVersions, Predicate<String> serverAcceptedVersions) {
        this.channel = NetworkRegistry.newSimpleChannel(
                channel,
                networkProtocolVersion,
                clientAcceptedVersions,
                serverAcceptedVersions
        );
    }

    @SuppressWarnings("unchecked")
    public <T> void registerPacket(Class<T> packetClass) {
        try {
            // 检查 encode
            Method encode = packetClass.getMethod("encode", packetClass, FriendlyByteBuf.class);
            if (!Modifier.isStatic(encode.getModifiers())) {
                throw new IllegalArgumentException("encode() must be static in " + packetClass.getName());
            }

            // 检查 decode
            Method decode = packetClass.getMethod("decode", FriendlyByteBuf.class);
            if (!Modifier.isStatic(decode.getModifiers())) {
                throw new IllegalArgumentException("decode() must be static in " + packetClass.getName());
            }
            if (!packetClass.isAssignableFrom(decode.getReturnType())) {
                throw new IllegalArgumentException("decode() must return " + packetClass.getName());
            }

            // 检查 handle
            Method handle = packetClass.getMethod("handle", Supplier.class);

            // 注册 Packet
            channel.messageBuilder(packetClass, idCounter.getAndIncrement())
                    .encoder((packet, buf) -> {
                        try {
                            encode.invoke(null, packet, buf);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to encode packet", e);
                        }
                    })
                    .decoder(buf -> {
                        try {
                            return (T) decode.invoke(null, buf);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to decode packet", e);
                        }
                    })
                    .consumerNetworkThread((packet, ctx) -> {
                        try {
                            handle.invoke(packet, ctx);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to handle packet", e);
                        }
                    })
                    .add();
            // LOGGER.info("{} registered", packetClass.getSimpleName());
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Packet class " + packetClass.getName() +
                    " is missing required methods (encode/decode/handle)", e);
        }
    }

    public SimpleChannel getChannel() {
        return channel;
    }
}
