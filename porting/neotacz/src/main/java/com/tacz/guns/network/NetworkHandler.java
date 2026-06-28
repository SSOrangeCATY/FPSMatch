package com.tacz.guns.network;

import com.tacz.guns.GunMod;
import com.tacz.guns.network.message.ClientMessageCraft;
import com.tacz.guns.network.message.ClientMessageLaserColor;
import com.tacz.guns.network.message.ClientMessagePlayerAim;
import com.tacz.guns.network.message.ClientMessagePlayerBoltGun;
import com.tacz.guns.network.message.ClientMessagePlayerCancelReload;
import com.tacz.guns.network.message.ClientMessagePlayerCrawl;
import com.tacz.guns.network.message.ClientMessagePlayerDrawGun;
import com.tacz.guns.network.message.ClientMessagePlayerFireSelect;
import com.tacz.guns.network.message.ClientMessagePlayerMelee;
import com.tacz.guns.network.message.ClientMessagePlayerReloadGun;
import com.tacz.guns.network.message.ClientMessagePlayerShoot;
import com.tacz.guns.network.message.ClientMessagePlayerZoom;
import com.tacz.guns.network.message.ClientMessageRefitGun;
import com.tacz.guns.network.message.ClientMessageSyncBaseTimestamp;
import com.tacz.guns.network.message.ClientMessageUnloadAttachment;
import com.tacz.guns.network.message.ServerMessageCraft;
import com.tacz.guns.network.message.ServerMessageLevelUp;
import com.tacz.guns.network.message.ServerMessageRefreshRefitScreen;
import com.tacz.guns.network.message.ServerMessageSound;
import com.tacz.guns.network.message.ServerMessageSwapItem;
import com.tacz.guns.network.message.ServerMessageSyncBaseTimestamp;
import com.tacz.guns.network.message.ServerMessageSyncGunPack;
import com.tacz.guns.network.message.ServerMessageUpdateEntityData;
import com.tacz.guns.network.message.event.ServerMessageGunDraw;
import com.tacz.guns.network.message.event.ServerMessageGunFire;
import com.tacz.guns.network.message.event.ServerMessageGunFireSelect;
import com.tacz.guns.network.message.event.ServerMessageGunHurt;
import com.tacz.guns.network.message.event.ServerMessageGunKill;
import com.tacz.guns.network.message.event.ServerMessageGunMelee;
import com.tacz.guns.network.message.event.ServerMessageGunReload;
import com.tacz.guns.network.message.event.ServerMessageGunShoot;
import com.tacz.guns.network.message.handshake.Acknowledge;
import com.tacz.guns.network.message.handshake.ServerMessageSyncedEntityDataMapping;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class NetworkHandler {
    private static final String VERSION = "2.0.0";
    private static final int MAX_PENDING_SYNCED_DATA = 256;

    private static final Map<Integer, MessageRegistration<?>> TO_SERVER_BY_ID = new HashMap<>();
    private static final Map<Integer, MessageRegistration<?>> TO_CLIENT_BY_ID = new HashMap<>();
    private static final Map<Class<?>, MessageRegistration<?>> BY_CLASS = new HashMap<>();
    private static final Map<UUID, Queue<ServerMessageUpdateEntityData>> PENDING_SYNCED_DATA = new ConcurrentHashMap<>();
    private static final java.util.Set<UUID> SYNCED_MAPPING_AWAITING_ACK = ConcurrentHashMap.newKeySet();
    private static final java.util.Set<UUID> SYNCED_MAPPING_READY = ConcurrentHashMap.newKeySet();
    private static boolean commonMessagesRegistered = false;
    private static boolean clientboundReceiversRegistered = false;
    private static Consumer<CustomPacketPayload> clientSender;
    private static Consumer<Object> clientboundDispatcher;

    public static final Channel CHANNEL = new Channel();

    public static void init() {
        registerMessages();
    }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        registerMessages();
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(ServerboundTaczPayload.TYPE, ServerboundTaczPayload.STREAM_CODEC, NetworkHandler::handleServerPayload);
        registrar.playToClient(ClientboundTaczPayload.TYPE, ClientboundTaczPayload.STREAM_CODEC);
    }

    public static void registerClientboundMessageHandlers() {
        registerClientboundReceivers();
    }

    public static void registerClientSender(Consumer<CustomPacketPayload> sender) {
        clientSender = sender;
    }

    public static void registerClientboundDispatcher(Consumer<Object> dispatcher) {
        clientboundDispatcher = dispatcher;
    }

    public static void handleClientPayload(ClientboundTaczPayload payload, IPayloadContext context) {
        handlePayload(payload.messageId(), payload.message(), PayloadFlow.TO_CLIENT, context);
    }

    public static void handleClientboundMessage(Object message, Supplier<NetworkContext> contextSupplier) {
        NetworkContext context = contextSupplier.get();
        enqueueClientboundMessage(context, message);
        context.setPacketHandled(true);
    }

    public static void enqueueClientboundMessage(NetworkContext context, Object message) {
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> dispatchClientboundMessage(message));
        }
    }

    public static void dispatchClientboundMessage(Object message) {
        Consumer<Object> dispatcher = clientboundDispatcher;
        if (dispatcher == null) {
            throw new IllegalStateException("TacZ clientbound network dispatcher is not registered");
        }
        dispatcher.accept(message);
    }

    private static void handleServerPayload(ServerboundTaczPayload payload, IPayloadContext context) {
        handlePayload(payload.messageId(), payload.message(), PayloadFlow.TO_SERVER, context);
    }

    private static void handlePayload(int messageId, Object message, PayloadFlow expectedFlow, IPayloadContext context) {
        NetworkContext networkContext = new NetworkContext(context);
        if (!expectedFlow.matches(context.flow())) {
            networkContext.disconnect(Component.literal("Connection closed - [TacZ] Wrong network direction for message id " + messageId));
            return;
        }
        MessageRegistration<?> registration = expectedFlow.receiverMap().get(messageId);
        if (registration == null) {
            networkContext.disconnect(Component.literal("Connection closed - [TacZ] Unknown network message id " + messageId));
            return;
        }
        if (!registration.type().isInstance(message)) {
            networkContext.disconnect(Component.literal("Connection closed - [TacZ] Network payload id " + messageId + " decoded unexpected message " + message.getClass().getName()));
            return;
        }
        registration.handle(message, networkContext);
    }

    private static synchronized void registerMessages() {
        if (commonMessagesRegistered) {
            return;
        }
        registerToServer(1, ClientMessagePlayerShoot.class, ClientMessagePlayerShoot::encode, ClientMessagePlayerShoot::decode, ClientMessagePlayerShoot::handle);
        registerToServer(2, ClientMessagePlayerReloadGun.class, ClientMessagePlayerReloadGun::encode, ClientMessagePlayerReloadGun::decode, ClientMessagePlayerReloadGun::handle);
        registerToServer(3, ClientMessagePlayerCancelReload.class, ClientMessagePlayerCancelReload::encode, ClientMessagePlayerCancelReload::decode, ClientMessagePlayerCancelReload::handle);
        registerToServer(4, ClientMessagePlayerFireSelect.class, ClientMessagePlayerFireSelect::encode, ClientMessagePlayerFireSelect::decode, ClientMessagePlayerFireSelect::handle);
        registerToServer(5, ClientMessagePlayerAim.class, ClientMessagePlayerAim::encode, ClientMessagePlayerAim::decode, ClientMessagePlayerAim::handle);
        registerToServer(6, ClientMessagePlayerCrawl.class, ClientMessagePlayerCrawl::encode, ClientMessagePlayerCrawl::decode, ClientMessagePlayerCrawl::handle);
        registerToServer(7, ClientMessagePlayerDrawGun.class, ClientMessagePlayerDrawGun::encode, ClientMessagePlayerDrawGun::decode, ClientMessagePlayerDrawGun::handle);
        registerToClientSender(8, ServerMessageSound.class, ServerMessageSound::encode);
        registerToServer(9, ClientMessageCraft.class, ClientMessageCraft::encode, ClientMessageCraft::decode, ClientMessageCraft::handle);
        registerToClientSender(10, ServerMessageCraft.class, ServerMessageCraft::encode);
        registerToServer(11, ClientMessagePlayerZoom.class, ClientMessagePlayerZoom::encode, ClientMessagePlayerZoom::decode, ClientMessagePlayerZoom::handle);
        registerToServer(12, ClientMessageRefitGun.class, ClientMessageRefitGun::encode, ClientMessageRefitGun::decode, ClientMessageRefitGun::handle);
        registerToClientSender(13, ServerMessageRefreshRefitScreen.class, ServerMessageRefreshRefitScreen::encode);
        registerToServer(14, ClientMessageUnloadAttachment.class, ClientMessageUnloadAttachment::encode, ClientMessageUnloadAttachment::decode, ClientMessageUnloadAttachment::handle);
        registerToClientSender(15, ServerMessageSwapItem.class, ServerMessageSwapItem::encode);
        registerToServer(16, ClientMessagePlayerBoltGun.class, ClientMessagePlayerBoltGun::encode, ClientMessagePlayerBoltGun::decode, ClientMessagePlayerBoltGun::handle);
        registerToClientSender(17, ServerMessageLevelUp.class, ServerMessageLevelUp::encode);
        registerToClientSender(18, ServerMessageGunHurt.class, ServerMessageGunHurt::encode);
        registerToClientSender(19, ServerMessageGunKill.class, ServerMessageGunKill::encode);
        registerToClientSender(20, ServerMessageUpdateEntityData.class, ServerMessageUpdateEntityData::encode);
        registerToClientSender(21, ServerMessageSyncGunPack.class, ServerMessageSyncGunPack::encode);
        registerToServer(22, ClientMessagePlayerMelee.class, ClientMessagePlayerMelee::encode, ClientMessagePlayerMelee::decode, ClientMessagePlayerMelee::handle);
        registerToClientSender(23, ServerMessageGunDraw.class, ServerMessageGunDraw::encode);
        registerToClientSender(24, ServerMessageGunFire.class, ServerMessageGunFire::encode);
        registerToClientSender(25, ServerMessageGunFireSelect.class, ServerMessageGunFireSelect::encode);
        registerToClientSender(26, ServerMessageGunMelee.class, ServerMessageGunMelee::encode);
        registerToClientSender(27, ServerMessageGunReload.class, ServerMessageGunReload::encode);
        registerToClientSender(28, ServerMessageGunShoot.class, ServerMessageGunShoot::encode);
        registerToClientSender(29, ServerMessageSyncBaseTimestamp.class, ServerMessageSyncBaseTimestamp::encode);
        registerToServer(30, ClientMessageSyncBaseTimestamp.class, ClientMessageSyncBaseTimestamp::encode, ClientMessageSyncBaseTimestamp::decode, ClientMessageSyncBaseTimestamp::handle);
        registerToServer(31, ClientMessageLaserColor.class, ClientMessageLaserColor::encode, ClientMessageLaserColor::decode, ClientMessageLaserColor::handle);

        Acknowledge acknowledge = new Acknowledge();
        registerToServer(1001, Acknowledge.class, acknowledge::encode, acknowledge::decode, acknowledge::handle);
        ServerMessageSyncedEntityDataMapping mapping = new ServerMessageSyncedEntityDataMapping();
        registerToClientSender(1002, ServerMessageSyncedEntityDataMapping.class, mapping::encode);
        commonMessagesRegistered = true;
    }

    private static synchronized void registerClientboundReceivers() {
        if (clientboundReceiversRegistered) {
            return;
        }
        registerMessages();
        registerToClientReceiver(8, ServerMessageSound.class, ServerMessageSound::encode, ServerMessageSound::decode, ServerMessageSound::handle);
        registerToClientReceiver(10, ServerMessageCraft.class, ServerMessageCraft::encode, ServerMessageCraft::decode, ServerMessageCraft::handle);
        registerToClientReceiver(13, ServerMessageRefreshRefitScreen.class, ServerMessageRefreshRefitScreen::encode, ServerMessageRefreshRefitScreen::decode, ServerMessageRefreshRefitScreen::handle);
        registerToClientReceiver(15, ServerMessageSwapItem.class, ServerMessageSwapItem::encode, ServerMessageSwapItem::decode, ServerMessageSwapItem::handle);
        registerToClientReceiver(17, ServerMessageLevelUp.class, ServerMessageLevelUp::encode, ServerMessageLevelUp::decode, ServerMessageLevelUp::handle);
        registerToClientReceiver(18, ServerMessageGunHurt.class, ServerMessageGunHurt::encode, ServerMessageGunHurt::decode, ServerMessageGunHurt::handle);
        registerToClientReceiver(19, ServerMessageGunKill.class, ServerMessageGunKill::encode, ServerMessageGunKill::decode, ServerMessageGunKill::handle);
        registerToClientReceiver(20, ServerMessageUpdateEntityData.class, ServerMessageUpdateEntityData::encode, ServerMessageUpdateEntityData::decode, ServerMessageUpdateEntityData::handle);
        registerToClientReceiver(21, ServerMessageSyncGunPack.class, ServerMessageSyncGunPack::encode, ServerMessageSyncGunPack::decode, ServerMessageSyncGunPack::handle);
        registerToClientReceiver(23, ServerMessageGunDraw.class, ServerMessageGunDraw::encode, ServerMessageGunDraw::decode, ServerMessageGunDraw::handle);
        registerToClientReceiver(24, ServerMessageGunFire.class, ServerMessageGunFire::encode, ServerMessageGunFire::decode, ServerMessageGunFire::handle);
        registerToClientReceiver(25, ServerMessageGunFireSelect.class, ServerMessageGunFireSelect::encode, ServerMessageGunFireSelect::decode, ServerMessageGunFireSelect::handle);
        registerToClientReceiver(26, ServerMessageGunMelee.class, ServerMessageGunMelee::encode, ServerMessageGunMelee::decode, ServerMessageGunMelee::handle);
        registerToClientReceiver(27, ServerMessageGunReload.class, ServerMessageGunReload::encode, ServerMessageGunReload::decode, ServerMessageGunReload::handle);
        registerToClientReceiver(28, ServerMessageGunShoot.class, ServerMessageGunShoot::encode, ServerMessageGunShoot::decode, ServerMessageGunShoot::handle);
        registerToClientReceiver(29, ServerMessageSyncBaseTimestamp.class, ServerMessageSyncBaseTimestamp::encode, ServerMessageSyncBaseTimestamp::decode, ServerMessageSyncBaseTimestamp::handle);

        ServerMessageSyncedEntityDataMapping mapping = new ServerMessageSyncedEntityDataMapping();
        registerToClientReceiver(1002, ServerMessageSyncedEntityDataMapping.class, mapping::encode, mapping::decode, mapping::handle);
        clientboundReceiversRegistered = true;
    }

    private static <T> void registerToServer(int id, Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder,
                                             Function<FriendlyByteBuf, T> decoder,
                                             BiConsumer<T, Supplier<NetworkContext>> handler) {
        MessageRegistration<T> registration = new MessageRegistration<>(id, type, PayloadFlow.TO_SERVER, encoder, decoder, handler);
        registerSender(registration);
        registerReceiver(TO_SERVER_BY_ID, registration);
    }

    private static <T> void registerToClientSender(int id, Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder) {
        registerSender(new MessageRegistration<>(id, type, PayloadFlow.TO_CLIENT, encoder, null, null));
    }

    private static <T> void registerToClientReceiver(int id, Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder,
                                                     Function<FriendlyByteBuf, T> decoder,
                                                     BiConsumer<T, Supplier<NetworkContext>> handler) {
        MessageRegistration<?> sender = BY_CLASS.get(type);
        if (sender == null || sender.id() != id || sender.flow() != PayloadFlow.TO_CLIENT) {
            throw new IllegalStateException("TACZ clientbound receiver has no matching sender registration for " + type.getName());
        }
        registerReceiver(TO_CLIENT_BY_ID, new MessageRegistration<>(id, type, PayloadFlow.TO_CLIENT, encoder, decoder, handler));
    }

    private static void registerSender(MessageRegistration<?> registration) {
        if (BY_CLASS.put(registration.type(), registration) != null) {
            throw new IllegalStateException("Duplicate TACZ network message class " + registration.type().getName());
        }
    }

    private static void registerReceiver(Map<Integer, MessageRegistration<?>> receivers, MessageRegistration<?> registration) {
        if (receivers.put(registration.id(), registration) != null) {
            throw new IllegalStateException("Duplicate TACZ " + registration.flow() + " network id " + registration.id());
        }
    }

    static CustomPacketPayload wrapReply(Object message, PacketFlow receivedFlow) {
        return wrapForFlow(message, receivedFlow.getOpposite());
    }

    private static CustomPacketPayload wrapForFlow(Object message, PacketFlow expectedFlow) {
        registerMessages();
        PayloadFlow payloadFlow = PayloadFlow.fromPacketFlow(expectedFlow);
        MessageRegistration<?> registration = BY_CLASS.get(message.getClass());
        if (registration == null) {
            throw new IllegalArgumentException("Unregistered TACZ network message " + message.getClass().getName());
        }
        if (registration.flow() != payloadFlow) {
            throw new IllegalArgumentException("Cannot send TACZ network message " + message.getClass().getName() + " as " + expectedFlow);
        }
        return payloadFlow.createPayload(registration.id(), message);
    }

    public static void sendSyncedEntityDataMapping(ServerPlayer player) {
        UUID id = player.getUUID();
        SYNCED_MAPPING_READY.remove(id);
        SYNCED_MAPPING_AWAITING_ACK.add(id);
        PENDING_SYNCED_DATA.computeIfAbsent(id, ignored -> new ConcurrentLinkedQueue<>()).clear();
        sendClientPayloadNow(new ServerMessageSyncedEntityDataMapping(), player);
    }

    public static void acknowledgeSyncedEntityDataMapping(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!SYNCED_MAPPING_AWAITING_ACK.remove(id)) {
            if (!SYNCED_MAPPING_READY.contains(id)) {
                GunMod.LOGGER.warn("Ignoring unexpected synced entity data acknowledgement from {}", player.getGameProfile().name());
            }
            return;
        }
        SYNCED_MAPPING_READY.add(id);
        Queue<ServerMessageUpdateEntityData> queue = PENDING_SYNCED_DATA.get(id);
        if (queue == null) {
            return;
        }
        ServerMessageUpdateEntityData pending;
        while ((pending = queue.poll()) != null) {
            sendClientPayloadNow(pending, player);
        }
    }

    public static void clearSyncedEntityDataMapping(ServerPlayer player) {
        UUID id = player.getUUID();
        SYNCED_MAPPING_AWAITING_ACK.remove(id);
        SYNCED_MAPPING_READY.remove(id);
        PENDING_SYNCED_DATA.remove(id);
    }

    public static void sendToClientPlayer(Object message, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (message instanceof ServerMessageUpdateEntityData updateEntityData) {
            sendSyncedEntityDataToPlayer(updateEntityData, serverPlayer);
            return;
        }
        sendClientPayloadNow(message, serverPlayer);
    }

    /**
     * 发送给所有监听此实体的玩家
     */
    public static void sendToTrackingEntityAndSelf(Entity centerEntity, Object message) {
        if (message instanceof ServerMessageUpdateEntityData updateEntityData) {
            sendSyncedEntityDataToTracking(updateEntityData, centerEntity);
            if (centerEntity instanceof ServerPlayer player) {
                sendSyncedEntityDataToPlayer(updateEntityData, player);
            }
            return;
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(centerEntity, wrapForFlow(message, PacketFlow.CLIENTBOUND));
    }

    public static void sendToAllPlayers(Object message) {
        if (message instanceof ServerMessageUpdateEntityData updateEntityData) {
            ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers().forEach(player -> sendSyncedEntityDataToPlayer(updateEntityData, player));
            return;
        }
        PacketDistributor.sendToAllPlayers(wrapForFlow(message, PacketFlow.CLIENTBOUND));
    }

    public static void sendToTrackingEntity(Object message, final Entity centerEntity) {
        if (message instanceof ServerMessageUpdateEntityData updateEntityData) {
            sendSyncedEntityDataToTracking(updateEntityData, centerEntity);
            return;
        }
        PacketDistributor.sendToPlayersTrackingEntity(centerEntity, wrapForFlow(message, PacketFlow.CLIENTBOUND));
    }

    public static void sendToDimension(Object message, final Entity centerEntity) {
        if (centerEntity.level() instanceof ServerLevel level) {
            PacketDistributor.sendToPlayersInDimension(level, wrapForFlow(message, PacketFlow.CLIENTBOUND));
        }
    }

    private static void sendSyncedEntityDataToTracking(ServerMessageUpdateEntityData message, Entity centerEntity) {
        if (centerEntity.level() instanceof ServerLevel level) {
            level.getChunkSource().chunkMap.getPlayersWatching(centerEntity).forEach(player -> sendSyncedEntityDataToPlayer(message, player));
        }
    }

    private static void sendSyncedEntityDataToPlayer(ServerMessageUpdateEntityData message, ServerPlayer player) {
        UUID id = player.getUUID();
        if (SYNCED_MAPPING_READY.contains(id)) {
            sendClientPayloadNow(message, player);
            return;
        }
        ensureSyncedEntityDataMappingSent(player);
        queueSyncedEntityData(player, message);
    }

    private static void ensureSyncedEntityDataMappingSent(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!SYNCED_MAPPING_READY.contains(id) && !SYNCED_MAPPING_AWAITING_ACK.contains(id)) {
            sendSyncedEntityDataMapping(player);
        }
    }

    private static void queueSyncedEntityData(ServerPlayer player, ServerMessageUpdateEntityData message) {
        UUID id = player.getUUID();
        Queue<ServerMessageUpdateEntityData> queue = PENDING_SYNCED_DATA.computeIfAbsent(id, ignored -> new ConcurrentLinkedQueue<>());
        if (queue.size() >= MAX_PENDING_SYNCED_DATA) {
            queue.clear();
            SYNCED_MAPPING_AWAITING_ACK.remove(id);
            player.connection.disconnect(Component.literal("Connection closed - [TacZ] Synced data mapping was not acknowledged."));
            return;
        }
        queue.add(message);
    }

    private static void sendClientPayloadNow(Object message, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, wrapForFlow(message, PacketFlow.CLIENTBOUND));
    }

    private static void encodePayload(PayloadFlow flow, int messageId, Object message, FriendlyByteBuf buffer) {
        registerMessages();
        MessageRegistration<?> registration = BY_CLASS.get(message.getClass());
        if (registration == null) {
            throw new IllegalArgumentException("Unregistered TACZ network message " + message.getClass().getName());
        }
        if (registration.id() != messageId || registration.flow() != flow) {
            throw new IllegalArgumentException("TACZ network payload id " + messageId + " cannot encode " + message.getClass().getName() + " as " + flow);
        }
        buffer.writeVarInt(messageId);
        registration.encode(message, buffer);
    }

    private static DecodedPayload decodePayload(PayloadFlow flow, FriendlyByteBuf buffer) {
        if (flow == PayloadFlow.TO_CLIENT) {
            registerClientboundReceivers();
        } else {
            registerMessages();
        }
        int id = buffer.readVarInt();
        MessageRegistration<?> registration = flow.receiverMap().get(id);
        if (registration == null) {
            throw new IllegalArgumentException("Unknown TACZ " + flow + " network message id " + id);
        }
        return new DecodedPayload(id, registration.decode(buffer));
    }

    public static final class Channel {
        public void sendToServer(Object message) {
            Consumer<CustomPacketPayload> sender = clientSender;
            if (sender == null) {
                throw new IllegalStateException("TacZ client network sender is not registered");
            }
            sender.accept(wrapForFlow(message, PacketFlow.SERVERBOUND));
        }

        public void reply(Object message, NetworkContext context) {
            context.reply(message);
        }
    }

    public record ServerboundTaczPayload(int messageId, Object message) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ServerboundTaczPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "network_c2s"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundTaczPayload> STREAM_CODEC = StreamCodec.ofMember(ServerboundTaczPayload::write, ServerboundTaczPayload::read);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            encodePayload(PayloadFlow.TO_SERVER, messageId, message, buffer);
        }

        private static ServerboundTaczPayload read(RegistryFriendlyByteBuf buffer) {
            DecodedPayload payload = decodePayload(PayloadFlow.TO_SERVER, buffer);
            return new ServerboundTaczPayload(payload.messageId(), payload.message());
        }
    }

    public record ClientboundTaczPayload(int messageId, Object message) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ClientboundTaczPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "network_s2c"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundTaczPayload> STREAM_CODEC = StreamCodec.ofMember(ClientboundTaczPayload::write, ClientboundTaczPayload::read);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            encodePayload(PayloadFlow.TO_CLIENT, messageId, message, buffer);
        }

        private static ClientboundTaczPayload read(RegistryFriendlyByteBuf buffer) {
            DecodedPayload payload = decodePayload(PayloadFlow.TO_CLIENT, buffer);
            return new ClientboundTaczPayload(payload.messageId(), payload.message());
        }
    }

    private record DecodedPayload(int messageId, Object message) {
    }

    private record MessageRegistration<T>(int id, Class<T> type, PayloadFlow flow,
                                          BiConsumer<T, FriendlyByteBuf> encoder,
                                          Function<FriendlyByteBuf, T> decoder,
                                          BiConsumer<T, Supplier<NetworkContext>> handler) {
        private void encode(Object message, FriendlyByteBuf buffer) {
            encoder.accept(type.cast(message), buffer);
        }

        private T decode(FriendlyByteBuf buffer) {
            if (decoder == null) {
                throw new IllegalStateException("TACZ " + flow + " message " + type.getName() + " is not registered for decoding");
            }
            return decoder.apply(buffer);
        }

        private void handle(Object message, NetworkContext context) {
            if (handler == null) {
                context.disconnect(Component.literal("Connection closed - [TacZ] Missing handler for network message " + type.getName()));
                return;
            }
            handler.accept(type.cast(message), () -> context);
        }
    }

    private enum PayloadFlow {
        TO_SERVER(PacketFlow.SERVERBOUND),
        TO_CLIENT(PacketFlow.CLIENTBOUND);

        private final PacketFlow packetFlow;

        PayloadFlow(PacketFlow packetFlow) {
            this.packetFlow = packetFlow;
        }

        private boolean matches(PacketFlow flow) {
            return packetFlow == flow;
        }

        private Map<Integer, MessageRegistration<?>> receiverMap() {
            return this == TO_SERVER ? TO_SERVER_BY_ID : TO_CLIENT_BY_ID;
        }

        private CustomPacketPayload createPayload(int messageId, Object message) {
            return this == TO_SERVER ? new ServerboundTaczPayload(messageId, message) : new ClientboundTaczPayload(messageId, message);
        }

        private static PayloadFlow fromPacketFlow(PacketFlow flow) {
            if (flow == PacketFlow.SERVERBOUND) {
                return TO_SERVER;
            }
            if (flow == PacketFlow.CLIENTBOUND) {
                return TO_CLIENT;
            }
            throw new IllegalArgumentException("Unsupported TACZ network flow " + flow);
        }
    }
}
