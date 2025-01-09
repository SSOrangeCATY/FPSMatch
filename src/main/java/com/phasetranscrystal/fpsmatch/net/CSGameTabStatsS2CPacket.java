package com.phasetranscrystal.fpsmatch.net;

import com.mojang.datafixers.util.Pair;
import com.phasetranscrystal.fpsmatch.client.data.ClientData;
import com.phasetranscrystal.fpsmatch.client.screen.CSGameShopScreen;
import com.phasetranscrystal.fpsmatch.core.data.TabData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.UUID;
import java.util.function.Supplier;

public class CSGameTabStatsS2CPacket {
    private final UUID uuid;
    private final TabData tabData;
    private final String team;

    public CSGameTabStatsS2CPacket(UUID uuid, TabData tabData,String team) {
        this.uuid = uuid;
        this.tabData = tabData;
        this.team = team;
    }

    public CSGameTabStatsS2CPacket(FriendlyByteBuf buf) {
        this.uuid = buf.readUUID();
        this.tabData = new TabData(uuid);
        this.tabData.setKills(buf.readInt());
        this.tabData.setDeaths(buf.readInt());
        this.tabData.setAssists(buf.readInt());
        this.tabData.setDamage(buf.readFloat());
        this.tabData.setLiving(buf.readBoolean());
        this.team = buf.readUtf();
    }

    public static void encode(CSGameTabStatsS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.uuid);
        buf.writeInt(packet.tabData.getKills());
        buf.writeInt(packet.tabData.getDeaths());
        buf.writeInt(packet.tabData.getAssists());
        buf.writeFloat(packet.tabData.getDamage());
        buf.writeBoolean(packet.tabData.isLiving());
        buf.writeUtf(packet.team);
    }

    public static CSGameTabStatsS2CPacket decode(FriendlyByteBuf buf) {
        return new CSGameTabStatsS2CPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if(uuid.equals(Minecraft.getInstance().player.getUUID())){
                if(!ClientData.currentTeam.equals(team)){
                    ClientData.currentTeam = team;
                    CSGameShopScreen.refreshFlag = true;
                };
            }
            ClientData.tabData.put(uuid,new Pair<>(team,tabData));
        });
        ctx.get().setPacketHandled(true);
    }
}