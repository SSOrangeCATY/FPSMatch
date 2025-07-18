package com.phasetranscrystal.fpsmatch.common.packet.register;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;


public class NetworkPacketRegister {
    private static final Map<NetworkPacketRegister, List<Class<?>>> CACHED = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(0);
    private final SimpleChannel channel;
    private final ResourceLocation name;

    public NetworkPacketRegister(ResourceLocation channel,String version) {
        this(channel,() -> version,version::equals,version::equals);
    }

    public NetworkPacketRegister(ResourceLocation channel, Supplier<String> networkProtocolVersion, Predicate<String> clientAcceptedVersions, Predicate<String> serverAcceptedVersions) {
        this.name = channel;
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

            CACHED.computeIfAbsent(this, k -> new ArrayList<>()).add(packetClass);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Packet class " + packetClass.getName() +
                    " is missing required methods (encode/decode/handle)", e);
        }
    }

    public SimpleChannel getChannel() {
        return channel;
    }

    public ResourceLocation getName() {
        return name;
    }

    public static SimpleChannel getChannelFromCache(Class<?> clazz){
        if (clazz == null) throw new IllegalArgumentException("Packet class cannot be null");

        for (Map.Entry<NetworkPacketRegister, List<Class<?>>> entry : NetworkPacketRegister.CACHED.entrySet()) {
            if (entry.getValue().contains(clazz)) {
                return entry.getKey().getChannel();
            }
        }

        throw new RuntimeException("Failed to find channel for " + clazz.getName());
    }
}
