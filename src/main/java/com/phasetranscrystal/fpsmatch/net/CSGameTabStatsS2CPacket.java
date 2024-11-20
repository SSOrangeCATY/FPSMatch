package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.client.data.ClientData;
import com.phasetranscrystal.fpsmatch.core.data.TabData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public class CSGameTabStatsS2CPacket {
    private final UUID uuid;
    private final TabData tabData;

    public CSGameTabStatsS2CPacket(UUID uuid, TabData tabData) {
        this.uuid = uuid;
        this.tabData = tabData;
    }

    public CSGameTabStatsS2CPacket(FriendlyByteBuf buf) {
        this.uuid = buf.readUUID();
        this.tabData = new TabData(uuid);
        this.tabData.setKills(buf.readInt());
        this.tabData.setDeaths(buf.readInt());
        this.tabData.setAssists(buf.readInt());
        this.tabData.setDamage(buf.readFloat());
    }

    public static void encode(CSGameTabStatsS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.uuid);
        buf.writeInt(packet.tabData.getKills());
        buf.writeInt(packet.tabData.getDeaths());
        buf.writeInt(packet.tabData.getAssists());
        buf.writeFloat(packet.tabData.getDamage());
    }

    public static CSGameTabStatsS2CPacket decode(FriendlyByteBuf buf) {
        return new CSGameTabStatsS2CPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientData.tabData.put(this.uuid,this.tabData);
        });
        ctx.get().setPacketHandled(true);
    }
}