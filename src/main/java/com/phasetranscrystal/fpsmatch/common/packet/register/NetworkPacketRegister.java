package com.phasetranscrystal.fpsmatch.common.packet.register;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class NetworkPacketRegister {
    private static final Map<NetworkPacketRegister, List<Class<?>>> CACHED = new ConcurrentHashMap<>();
    private static final Map<Class<?>, MessageRegistration<?>> BY_CLASS = new HashMap<>();
    private static final Map<Integer, MessageRegistration<?>> BY_ID = new HashMap<>();
    private static final AtomicInteger GLOBAL_ID_COUNTER = new AtomicInteger(0);

    private final Identifier name;
    private final String version;

    public NetworkPacketRegister(Identifier channel, String version) {
        this.name = channel;
        this.version = version;
    }

    public void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(version);
        registrar.playToServer(ServerboundFpsMatchPayload.TYPE, ServerboundFpsMatchPayload.STREAM_CODEC, NetworkPacketRegister::handleServerPayload);
        registrar.playToClient(ClientboundFpsMatchPayload.TYPE, ClientboundFpsMatchPayload.STREAM_CODEC, NetworkPacketRegister::handleClientPayload);
    }

    public <T> void registerPacket(Class<T> packetClass) {
        try {
            Method encode = findPacketCodecMethod(packetClass, "encode", packetClass);
            if (!Modifier.isStatic(encode.getModifiers())) {
                throw new IllegalArgumentException("encode() must be static in " + packetClass.getName());
            }

            Method decode = findPacketCodecMethod(packetClass, "decode");
            if (!Modifier.isStatic(decode.getModifiers())) {
                throw new IllegalArgumentException("decode() must be static in " + packetClass.getName());
            }
            if (!packetClass.isAssignableFrom(decode.getReturnType())) {
                throw new IllegalArgumentException("decode() must return " + packetClass.getName());
            }

            Method handle = packetClass.getMethod("handle", Supplier.class);
            int id = GLOBAL_ID_COUNTER.getAndIncrement();
            MessageRegistration<T> registration = new MessageRegistration<>(id, packetClass, encode, decode, handle);

            if (BY_CLASS.put(packetClass, registration) != null) {
                throw new IllegalStateException("Duplicate FPSMatch network packet class " + packetClass.getName());
            }
            if (BY_ID.put(id, registration) != null) {
                throw new IllegalStateException("Duplicate FPSMatch network packet id " + id);
            }

            CACHED.computeIfAbsent(this, k -> new ArrayList<>()).add(packetClass);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Packet class " + packetClass.getName() +
                    " is missing required methods (encode/decode/handle)", e);
        }
    }

    private static Method findPacketCodecMethod(Class<?> packetClass, String name, Class<?>... leadingTypes) throws NoSuchMethodException {
        Class<?>[] registryTypes = appendBufferType(leadingTypes, RegistryFriendlyByteBuf.class);
        try {
            return packetClass.getMethod(name, registryTypes);
        } catch (NoSuchMethodException ignored) {
            return packetClass.getMethod(name, appendBufferType(leadingTypes, FriendlyByteBuf.class));
        }
    }

    private static Class<?>[] appendBufferType(Class<?>[] leadingTypes, Class<?> bufferType) {
        Class<?>[] result = new Class<?>[leadingTypes.length + 1];
        System.arraycopy(leadingTypes, 0, result, 0, leadingTypes.length);
        result[leadingTypes.length] = bufferType;
        return result;
    }

    public Identifier getName() {
        return name;
    }

    public <M> void sendToPlayer(ServerPlayer player, M message) {
        PacketDistributor.sendToPlayer(player, wrapForFlow(message, PacketFlow.CLIENTBOUND));
    }

    public <M> void sendToAllPlayers(M message) {
        PacketDistributor.sendToAllPlayers(wrapForFlow(message, PacketFlow.CLIENTBOUND));
    }

    public <M> void sendToServer(M message) {
        ClientPacketDistributor.sendToServer(wrapForFlow(message, PacketFlow.SERVERBOUND));
    }

    public static NetworkPacketRegister getRegisterFromCache(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("Packet class cannot be null");
        }

        for (Map.Entry<NetworkPacketRegister, List<Class<?>>> entry : CACHED.entrySet()) {
            if (entry.getValue().contains(clazz)) {
                return entry.getKey();
            }
        }

        throw new RuntimeException("Failed to find network register for " + clazz.getName());
    }

    private static CustomPacketPayload wrapForFlow(Object message, PacketFlow flow) {
        MessageRegistration<?> registration = BY_CLASS.get(message.getClass());
        if (registration == null) {
            throw new IllegalArgumentException("Unregistered FPSMatch network packet " + message.getClass().getName());
        }
        return flow == PacketFlow.SERVERBOUND
                ? new ServerboundFpsMatchPayload(registration.id(), message)
                : new ClientboundFpsMatchPayload(registration.id(), message);
    }

    private static void handleServerPayload(ServerboundFpsMatchPayload payload, IPayloadContext context) {
        handlePayload(payload.messageId(), payload.message(), context);
    }

    private static void handleClientPayload(ClientboundFpsMatchPayload payload, IPayloadContext context) {
        handlePayload(payload.messageId(), payload.message(), context);
    }

    private static void handlePayload(int messageId, Object message, IPayloadContext context) {
        MessageRegistration<?> registration = BY_ID.get(messageId);
        if (registration == null) {
            context.disconnect(Component.literal("Connection closed - [FPSMatch] Unknown network packet id " + messageId));
            return;
        }
        if (!registration.type().isInstance(message)) {
            context.disconnect(Component.literal("Connection closed - [FPSMatch] Network payload id " + messageId + " decoded unexpected packet " + message.getClass().getName()));
            return;
        }
        registration.handle(message, new Context(context));
    }

    private static void encodePayload(int messageId, Object message, FriendlyByteBuf buffer) {
        MessageRegistration<?> registration = BY_CLASS.get(message.getClass());
        if (registration == null) {
            throw new IllegalArgumentException("Unregistered FPSMatch network packet " + message.getClass().getName());
        }
        if (registration.id() != messageId) {
            throw new IllegalArgumentException("FPSMatch network payload id " + messageId + " cannot encode " + message.getClass().getName());
        }
        buffer.writeVarInt(messageId);
        registration.encode(message, buffer);
    }

    private static DecodedPayload decodePayload(FriendlyByteBuf buffer) {
        int id = buffer.readVarInt();
        MessageRegistration<?> registration = BY_ID.get(id);
        if (registration == null) {
            throw new IllegalArgumentException("Unknown FPSMatch network packet id " + id);
        }
        return new DecodedPayload(id, registration.decode(buffer));
    }

    public record ServerboundFpsMatchPayload(int messageId, Object message) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ServerboundFpsMatchPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("fpsmatch", "network_c2s"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundFpsMatchPayload> STREAM_CODEC = StreamCodec.ofMember(ServerboundFpsMatchPayload::write, ServerboundFpsMatchPayload::read);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            encodePayload(messageId, message, buffer);
        }

        private static ServerboundFpsMatchPayload read(RegistryFriendlyByteBuf buffer) {
            DecodedPayload payload = decodePayload(buffer);
            return new ServerboundFpsMatchPayload(payload.messageId(), payload.message());
        }
    }

    public record ClientboundFpsMatchPayload(int messageId, Object message) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ClientboundFpsMatchPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("fpsmatch", "network_s2c"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundFpsMatchPayload> STREAM_CODEC = StreamCodec.ofMember(ClientboundFpsMatchPayload::write, ClientboundFpsMatchPayload::read);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            encodePayload(messageId, message, buffer);
        }

        private static ClientboundFpsMatchPayload read(RegistryFriendlyByteBuf buffer) {
            DecodedPayload payload = decodePayload(buffer);
            return new ClientboundFpsMatchPayload(payload.messageId(), payload.message());
        }
    }

    private record DecodedPayload(int messageId, Object message) {
    }

    private record MessageRegistration<T>(int id, Class<T> type, Method encoder, Method decoder, Method handler) {
        private void encode(Object message, FriendlyByteBuf buffer) {
            try {
                encoder.invoke(null, type.cast(message), buffer);
            } catch (Exception e) {
                throw new RuntimeException("Failed to encode packet " + type.getName(), e);
            }
        }

        private T decode(FriendlyByteBuf buffer) {
            try {
                return type.cast(decoder.invoke(null, buffer));
            } catch (Exception e) {
                throw new RuntimeException("Failed to decode packet " + type.getName(), e);
            }
        }

        private void handle(Object message, Context context) {
            try {
                handler.invoke(type.cast(message), (Supplier<Context>) () -> context);
            } catch (Exception e) {
                throw new RuntimeException("Failed to handle packet " + type.getName(), e);
            }
        }
    }

    public static class Context {
        private final IPayloadContext context;

        private Context(IPayloadContext context) {
            this.context = context;
        }

        public CompletableFuture<Void> enqueueWork(Runnable task) {
            return context.enqueueWork(task);
        }

        public ServerPlayer getSender() {
            if (context.flow() != PacketFlow.SERVERBOUND || !(context.player() instanceof ServerPlayer serverPlayer)) {
                throw new IllegalStateException("FPSMatch serverbound packet has no server player sender");
            }
            return serverPlayer;
        }

        public PacketFlow getDirection() {
            return context.flow();
        }

        public void setPacketHandled(boolean handled) {
        }

        public void reply(Object message) {
            context.reply(wrapForFlow(message, context.flow().getOpposite()));
        }
    }
}
