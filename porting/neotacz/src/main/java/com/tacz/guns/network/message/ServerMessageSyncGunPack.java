package com.tacz.guns.network.message;

import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.resource.network.DataType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import com.tacz.guns.network.NetworkContext;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;


public class ServerMessageSyncGunPack {
    private final Map<DataType, Map<Identifier, String>> cache;
    private boolean remoteConnection;

    public ServerMessageSyncGunPack(Map<DataType, Map<Identifier, String>> cache) {
        this.cache = cache;
    }

    public static void encode(ServerMessageSyncGunPack message, FriendlyByteBuf buf) {
        Map<DataType, Map<Identifier, String>> cache = message.getCache();
        buf.writeVarInt(cache.size());
        cache.forEach((dataType, entries) -> {
            buf.writeEnum(dataType);
            buf.writeVarInt(entries.size());
            entries.forEach((id, value) -> {
                buf.writeIdentifier(id);
                buf.writeUtf(value);
            });
        });
    }

    public static ServerMessageSyncGunPack decode(FriendlyByteBuf buf) {
        Map<DataType, Map<Identifier, String>> map = new EnumMap<>(DataType.class);
        int typeCount = buf.readVarInt();
        for (int i = 0; i < typeCount; i++) {
            DataType dataType = buf.readEnum(DataType.class);
            int entryCount = buf.readVarInt();
            Map<Identifier, String> entries = new HashMap<>();
            for (int j = 0; j < entryCount; j++) {
                entries.put(buf.readIdentifier(), buf.readUtf());
            }
            map.put(dataType, entries);
        }
        return new ServerMessageSyncGunPack(map);
    }

    public static void handle(ServerMessageSyncGunPack message, Supplier<NetworkContext> contextSupplier) {
        NetworkContext context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            message.remoteConnection = context.getNetworkManager() != null && !context.getNetworkManager().isMemoryConnection();
            NetworkHandler.enqueueClientboundMessage(context, message);
        }
        context.setPacketHandled(true);
    }


    public Map<DataType, Map<Identifier, String>> getCache() {
        return cache;
    }

    public boolean isRemoteConnection() {
        return remoteConnection;
    }
}
