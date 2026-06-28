package com.tacz.guns.network.message;

import com.tacz.guns.entity.sync.core.DataEntry;
import com.tacz.guns.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import com.tacz.guns.network.NetworkContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ServerMessageUpdateEntityData {
    private final int entityId;
    private final List<DataEntry<?, ?>> entries;

    public ServerMessageUpdateEntityData(int entityId, List<DataEntry<?, ?>> entries) {
        this.entityId = entityId;
        this.entries = entries;
    }

    public static void encode(ServerMessageUpdateEntityData message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.entityId);
        buffer.writeVarInt(message.entries.size());
        message.entries.forEach(entry -> entry.write(buffer));
    }

    public static ServerMessageUpdateEntityData decode(FriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
        int size = buffer.readVarInt();
        List<DataEntry<?, ?>> entries = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            entries.add(DataEntry.read(buffer));
        }
        return new ServerMessageUpdateEntityData(entityId, entries);
    }

    public static void handle(ServerMessageUpdateEntityData message, Supplier<NetworkContext> contextSupplier) {
        NetworkHandler.handleClientboundMessage(message, contextSupplier);
    }

    public int getEntityId() {
        return entityId;
    }

    public List<DataEntry<?, ?>> getEntries() {
        return entries;
    }
}
